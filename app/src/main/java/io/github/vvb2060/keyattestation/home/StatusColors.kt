package io.github.vvb2060.keyattestation.home

import android.content.Context
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import io.github.vvb2060.keyattestation.databinding.HomeHeaderBinding
import com.google.android.material.R as M

/** Container and on-container colors for a status attr (colorSafe/Warning/Alert/Inactive). */
internal fun statusColors(context: Context, statusAttr: Int): Pair<Int, Int> {
    val (container, on) = when (statusAttr) {
        rikka.material.R.attr.colorSafe ->
            M.attr.colorPrimaryContainer to M.attr.colorOnPrimaryContainer
        rikka.material.R.attr.colorWarning ->
            M.attr.colorTertiaryContainer to M.attr.colorOnTertiaryContainer
        rikka.material.R.attr.colorAlert ->
            M.attr.colorErrorContainer to M.attr.colorOnErrorContainer
        else -> M.attr.colorSurfaceContainerHighest to M.attr.colorOnSurfaceVariant
    }
    return MaterialColors.getColor(context, container, 0) to MaterialColors.getColor(context, on, 0)
}

internal fun HomeHeaderBinding.applyStatus(statusAttr: Int) {
    val (container, on) = statusColors(root.context, statusAttr)
    root.backgroundTintList = ColorStateList.valueOf(container)
    icon.imageTintList = ColorStateList.valueOf(on)
    title.setTextColor(on)
    summary.setTextColor(on)
}
