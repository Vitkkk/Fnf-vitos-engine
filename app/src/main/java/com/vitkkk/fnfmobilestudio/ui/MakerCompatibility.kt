package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Convenience overload so editor screens can use a trailing lambda for tab selection. */
@Composable
internal fun MakerTabRow(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    MakerTabRow(labels, selected, onSelect, modifier)
}
