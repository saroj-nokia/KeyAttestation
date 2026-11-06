package io.github.vvb2060.keyattestation.attestation;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.vvb2060.keyattestation.AppApplication;
import io.github.vvb2060.keyattestation.R;

public record RevocationList(String status, String reason, DataSource source) {
    public enum DataSource {
        NETWORK_INITIAL,
        NETWORK_UPDATE,
        NETWORK_UP_TO_DATE,
        CACHE,
        BUNDLED
    }

    private static final String TAG = "RevocationList";
    private static final String CACHE_FILE = "revocation_cache.json";
    private static final String PREFS_NAME = "revocation_prefs";
    private static final String KEY_PUBLISH_TIME = "last_publish_time";

    private static volatile JSONObject data = null;
    private static volatile Date publishTime = null;
    private static volatile DataSource currentSource = DataSource.BUNDLED;

    // Single thread: syncs run one after another, and the 10s/20s socket timeouts bound each one.
    private static final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnUpdateListener {
        void onUpdateSuccess(DataSource newSource);
    }

    private record StatusResult(JSONObject json, DataSource source) {}
    private record NetworkResult(JSONObject json, JSONObject entries, int responseCode, long lastModified) {}

    private static String toString(InputStream input) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } else {
            var output = new ByteArrayOutputStream(8192);
            var buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) != -1; ) {
                output.write(buffer, 0, length);
            }
            return output.toString();
        }
    }

    private static JSONObject parseStatus(InputStream inputStream) throws IOException {
        try {
            return new JSONObject(toString(inputStream));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static void saveToCache(JSONObject fullJson, long lastModified) {
        try (var output = AppApplication.app.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE)) {
            String pretty;
            try {
                pretty = fullJson.toString(2);
            } catch (JSONException e) {
                pretty = fullJson.toString();
            }
            output.write(pretty.getBytes(StandardCharsets.UTF_8));
            var prefs = AppApplication.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (lastModified != 0) {
                prefs.edit().putLong(KEY_PUBLISH_TIME, lastModified).apply();
            } else {
                // Don't keep a stale If-Modified-Since for data that has no Last-Modified.
                prefs.edit().remove(KEY_PUBLISH_TIME).apply();
            }
            Log.i(TAG, "Local cache file written successfully.");
        } catch (IOException e) {
            Log.w(TAG, "Failed to cache revocation list", e);
        }
    }

    private static NetworkResult fetchFromNetwork(String statusUrl, long cachedTime) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(statusUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("User-Agent", "KeyAttestation");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, no-transform, max-age=0");
            connection.setRequestProperty("Accept", "application/json");

            if (cachedTime != 0) {
                connection.setIfModifiedSince(cachedTime);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return new NetworkResult(null, null, responseCode, 0);
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (var input = connection.getInputStream()) {
                    var json = parseStatus(input);
                    // Validate before anything is cached: a bad body must not replace good data.
                    var entries = json.getJSONObject("entries");
                    return new NetworkResult(json, entries, responseCode, connection.getLastModified());
                }
            }
            Log.w(TAG, "Unexpected response code " + responseCode);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Network fetch failed", e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static StatusResult loadLocalData() {
        var prefs = AppApplication.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long cachedTime = prefs.getLong(KEY_PUBLISH_TIME, 0);

        try (var fis = AppApplication.app.openFileInput(CACHE_FILE)) {
            var cacheJson = parseStatus(fis);
            var entries = cacheJson.getJSONObject("entries");
            publishTime = cachedTime != 0 ? new Date(cachedTime) : null;
            Log.i(TAG, "Successfully matched database schemas inside local CACHE storage.");
            return new StatusResult(entries, DataSource.CACHE);
        } catch (Exception e) {
            Log.i(TAG, "Local file cache missing, loading baseline fallback asset.");
        }

        var res = AppApplication.app.getResources();
        try (var input = res.openRawResource(R.raw.status)) {
            var bundledJson = parseStatus(input);
            publishTime = null;
            return new StatusResult(bundledJson.getJSONObject("entries"), DataSource.BUNDLED);
        } catch (Exception e) {
            throw new RuntimeException("Critical: Baseline resource asset file missing from app payload", e);
        }
    }

    public static void refreshAsync(OnUpdateListener listener) {
        asyncExecutor.execute(() -> {
            var statusUrl = "https://android.googleapis.com/attestation/status";
            var res = AppApplication.app.getResources();
            var resName = "android:string/vendor_required_attestation_revocation_list_url";
            var id = res.getIdentifier(resName, null, null);
            if (id != 0) {
                var url = res.getString(id);
                if (!statusUrl.equals(url) && url.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    statusUrl = url;
                }
            }

            var prefs = AppApplication.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long cachedTime = prefs.getLong(KEY_PUBLISH_TIME, 0);

            Log.i(TAG, "Starting background network sync operation...");
            var network = fetchFromNetwork(statusUrl, cachedTime);
            DataSource newSource = null;
            JSONObject newEntries = null;
            Date newPublishTime = null;

            if (network != null && network.responseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.i(TAG, "Network connection reporting 304 Not Modified. Local data streams up to date.");
                try (var fis = AppApplication.app.openFileInput(CACHE_FILE)) {
                    newEntries = parseStatus(fis).getJSONObject("entries");
                    newPublishTime = new Date(cachedTime);
                    newSource = DataSource.NETWORK_UP_TO_DATE;
                } catch (Exception e) {
                    Log.w(TAG, "Cache unreadable despite 304. Resetting and downloading again.", e);
                    AppApplication.app.deleteFile(CACHE_FILE);
                    prefs.edit().remove(KEY_PUBLISH_TIME).apply();
                    cachedTime = 0;
                    network = fetchFromNetwork(statusUrl, 0);
                }
            }

            if (newSource == null && network != null && network.json() != null) {
                Log.i(TAG, "Network fetch SUCCESS! Downloaded updated production data components.");
                saveToCache(network.json(), network.lastModified());
                newEntries = network.entries();
                newPublishTime = network.lastModified() != 0 ? new Date(network.lastModified()) : null;
                newSource = (cachedTime == 0) ? DataSource.NETWORK_INITIAL : DataSource.NETWORK_UPDATE;
            }

            if (newSource == null) {
                Log.i(TAG, "Sync complete. Network failed; falling back to offline data.");
                try {
                    var local = loadLocalData();
                    newEntries = local.json();
                    newSource = local.source();
                    newPublishTime = publishTime; // set by loadLocalData
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load local data fallback during offline transition", e);
                }
            }

            if (newEntries != null && newSource != null) {
                final DataSource sourceToApply = newSource;
                synchronized (RevocationList.class) {
                    data = newEntries;
                    currentSource = sourceToApply;
                    publishTime = newPublishTime;
                }

                if (listener != null) {
                    mainHandler.post(() -> listener.onUpdateSuccess(sourceToApply));
                }
            } else {
                Log.i(TAG, "Sync complete. Local data streams unchanged.");
            }
        });
    }

    public static void refresh() {
        refreshAsync(null);
    }

    public static Date getPublishTime() {
        return publishTime;
    }

    public static DataSource getCurrentSource() {
        return currentSource;
    }

    public static boolean hasCachedCrl() {
        return AppApplication.app.getFileStreamPath(CACHE_FILE).exists();
    }

    public static String suggestedExportFileName() {
        var fmt = new java.text.SimpleDateFormat("yyyyMMdd-hhmma", Locale.US);
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        if (publishTime != null) {
            return fmt.format(publishTime) + ".json";
        }
        return fmt.format(new Date()) + "-local.json";
    }

    /**
     * Writes the currently cached CRL (as originally downloaded from Google, re-serialized) to the given stream.
     * Callers should check hasCachedCrl() first; a false return here
     * means the write itself failed (e.g. the cache was deleted between the check and this call), not that no cache exists.
     */
    public static boolean exportCachedCrl(java.io.OutputStream out) {
        try (var fis = AppApplication.app.openFileInput(CACHE_FILE)) {
            var buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "exportCachedCrl: cache file missing or unreadable", e);
            return false;
        }
    }

    public static RevocationList get(BigInteger serialNumber) {
        if (data == null) {
            synchronized (RevocationList.class) {
                if (data == null) {
                    StatusResult result = loadLocalData();
                    data = result.json();
                    currentSource = result.source();
                }
            }
        }
        String serial = serialNumber.toString(16).toLowerCase();
        try {
            JSONObject entry = data.getJSONObject(serial);
            return new RevocationList(entry.getString("status"), entry.getString("reason"), currentSource);
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "status: " + status + ", source: " + source;
    }
}
