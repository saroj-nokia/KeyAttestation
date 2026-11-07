-allowaccessmodification
-repackageclasses

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}

-assumenosideeffects class java.util.Objects{
    ** requireNonNull(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

-keep class com.google.android.material.theme.MaterialComponentsViewInflater {
    <init>();
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

-keep class rikka.shizuku.** { *; }

# Behaviors are named by string in layouts (layout_behavior), so R8 can't see the reference.
-keep class com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior { <init>(...); }
-keep class * extends androidx.coordinatorlayout.widget.CoordinatorLayout$Behavior { <init>(...); }
