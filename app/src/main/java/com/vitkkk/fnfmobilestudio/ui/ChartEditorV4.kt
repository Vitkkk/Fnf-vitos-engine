package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.runtime.Composable
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.storage.ProjectStore

/**
 * Compatibility entry point kept for the V4 router.
 * The chart is now edited inside the unified Level Editor.
 */
@Composable
internal fun ChartEditorScreenV4(
    project: Project,
    songId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    LevelEditorScreenV5(
        project = project,
        songId = songId,
        store = store,
        onBack = onBack,
        onHome = onHome,
        onProjectChange = onProjectChange
    )
}
