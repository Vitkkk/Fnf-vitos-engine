package com.vitkkk.fnfmobilestudio.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vitkkk.fnfmobilestudio.model.AssetKind
import com.vitkkk.fnfmobilestudio.model.AssetRef
import com.vitkkk.fnfmobilestudio.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "projects")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun listProjects(): List<Project> = withContext(Dispatchers.IO) {
        ensureRoot()
        root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .map { File(it, PROJECT_FILE) }
            .filter { it.isFile }
            .mapNotNull { file -> runCatching { json.decodeFromString<Project>(file.readText()) }.getOrNull() }
            .sortedByDescending { projectDirectory(it.id).lastModified() }
            .toList()
    }

    suspend fun load(projectId: String): Project? = withContext(Dispatchers.IO) {
        val file = File(projectDirectory(projectId), PROJECT_FILE)
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString<Project>(file.readText()) }.getOrNull()
    }

    suspend fun createProject(name: String, author: String): Project = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Untitled Mod" }
        val baseId = slug(cleanName)
        var id = baseId
        var suffix = 2
        while (projectDirectory(id).exists()) {
            id = "$baseId-$suffix"
            suffix++
        }
        Project(id = id, name = cleanName, author = author.trim()).also { saveBlocking(it) }
    }

    suspend fun save(project: Project) = withContext(Dispatchers.IO) {
        saveBlocking(project)
    }

    suspend fun delete(projectId: String) = withContext(Dispatchers.IO) {
        projectDirectory(projectId).deleteRecursively()
    }

    suspend fun importAsset(
        projectId: String,
        uri: Uri,
        kind: AssetKind,
        folder: String
    ): AssetRef = withContext(Dispatchers.IO) {
        val projectDir = projectDirectory(projectId)
        check(projectDir.exists()) { "Project '$projectId' does not exist" }
        val sourceName = displayName(uri) ?: "asset"
        val safeName = safeFileName(sourceName)
        val assetDir = File(projectDir, "assets/${safeFileName(folder)}")
        check(assetDir.exists() || assetDir.mkdirs()) { "Could not create asset directory" }

        var target = File(assetDir, safeName)
        var suffix = 2
        val stem = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        while (target.exists()) {
            target = File(assetDir, "$stem-$suffix$extension")
            suffix++
        }

        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Could not open selected file" }
            target.outputStream().use { output -> input.copyTo(output) }
        }

        AssetRef(
            id = UUID.randomUUID().toString(),
            relativePath = target.relativeTo(projectDir).invariantSeparatorsPath,
            kind = kind
        )
    }

    fun projectDirectory(projectId: String): File = File(root, projectId)

    fun assetFile(projectId: String, asset: AssetRef): File =
        File(projectDirectory(projectId), asset.relativePath)

    private fun saveBlocking(project: Project) {
        ensureRoot()
        val dir = projectDirectory(project.id)
        check(dir.exists() || dir.mkdirs()) { "Could not create project directory ${dir.absolutePath}" }
        val target = File(dir, PROJECT_FILE)
        val temp = File(dir, "$PROJECT_FILE.tmp")
        temp.writeText(json.encodeToString(project))
        if (target.exists() && !target.delete()) error("Could not replace project file")
        check(temp.renameTo(target)) { "Could not atomically save project" }
        dir.setLastModified(System.currentTimeMillis())
    }

    private fun displayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun ensureRoot() {
        check(root.exists() || root.mkdirs()) { "Could not create projects directory ${root.absolutePath}" }
    }

    private fun slug(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "untitled-mod" }

    private fun safeFileName(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.')
        .ifEmpty { "asset" }

    private companion object {
        const val PROJECT_FILE = "project.json"
    }
}
