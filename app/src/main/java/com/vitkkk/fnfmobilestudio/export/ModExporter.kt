package com.vitkkk.fnfmobilestudio.export

import com.vitkkk.fnfmobilestudio.model.Project

data class ExportArtifact(
    val path: String,
    val textContent: String? = null,
    val sourceAssetId: String? = null
) {
    init {
        require((textContent == null) xor (sourceAssetId == null)) {
            "Artifact must contain text content or reference exactly one source asset"
        }
    }
}

data class ExportBundle(
    val profileId: String,
    val artifacts: List<ExportArtifact>,
    val warnings: List<String> = emptyList()
)

interface ModExporter {
    val profileId: String
    fun export(project: Project): ExportBundle
}
