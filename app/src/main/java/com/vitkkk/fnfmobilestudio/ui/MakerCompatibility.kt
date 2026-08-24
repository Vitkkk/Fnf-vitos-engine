package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.runtime.Composable

/** Convenience overload for editor screens that use a trailing selection lambda. */
@Composable
internal fun MakerTabRow(
    labels: List<String>,
    selected: Int,
    onTabSelected: (Int) -> Unit
) {
    MakerTabRow(labels = labels, selected = selected, onSelect = onTabSelected)
}
