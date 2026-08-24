package com.vitkkk.fnfmobilestudio.export

import android.content.Context
import androidx.core.content.FileProvider
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectExportService(
    private val context: Context,
    private val projectStore: ProjectStore,
    private val exporter: ModExporter = PsychV1Exporter()
) {
    suspend fun buildZip(project: Project): ExportResult = withContext(Dispatchers.IO) {
        val bundle = exporter.export(project)
        val exportDir = File(context.cacheDir, "exports")
        check(exportDir.exists() || exportDir.mkdirs()) { "Could not create export directory" }
        val zipFile = File(exportDir, "${safeName(project.id)}-${bundle.profileId}.zip")
        val assetsById = project.assets.associateBy { it.id }
        val warnings = bundle.warnings.toMutableList()

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            bundle.artifacts.forEach { artifact ->
                val entryPath = artifact.path.replace('\\', '/').trimStart('/')
                zip.putNextEntry(ZipEntry(entryPath))
                when {
                    artifact.textContent != null -> zip.write(artifact.textContent.toByteArray(Charsets.UTF_8))
                    artifact.sourceAssetId != null -> {
                        val asset = assetsById[artifact.sourceAssetId]
                        val source = asset?.let { projectStore.assetFile(project.id, it) }
                        if (source != null && source.isFile) {
                            source.inputStream().use { it.copyTo(zip) }
                        } else {
                            warnings += "Missing source asset for ${artifact.path}"
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        ExportResult(
            file = zipFile,
            warnings = warnings.distinct(),
            artifactCount = bundle.artifacts.size
        )
    }

    fun shareUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        file
    )

    private fun safeName(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .trim('-')
        .ifEmpty { "mod" }
}

data class ExportResult(
    val file: File,
    val warnings: List<String>,
    val artifactCount: Int
)
