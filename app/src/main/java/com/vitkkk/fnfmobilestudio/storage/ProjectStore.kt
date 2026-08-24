package com.vitkkk.fnfmobilestudio.storage

import android.content.Context
import com.vitkkk.fnfmobilestudio.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectStore(context: Context) {
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
            .sortedBy { it.name.lowercase() }
            .toList()
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

    private fun saveBlocking(project: Project) {
        ensureRoot()
        val dir = projectDirectory(project.id)
        check(dir.exists() || dir.mkdirs()) { "Could not create project directory ${dir.absolutePath}" }
        val target = File(dir, PROJECT_FILE)
        val temp = File(dir, "$PROJECT_FILE.tmp")
        temp.writeText(json.encodeToString(project))
        if (target.exists() && !target.delete()) error("Could not replace project file")
        check(temp.renameTo(target)) { "Could not atomically save project" }
    }

    private fun ensureRoot() {
        check(root.exists() || root.mkdirs()) { "Could not create projects directory ${root.absolutePath}" }
    }

    private fun projectDirectory(id: String) = File(root, id)

    private fun slug(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "untitled-mod" }

    private companion object {
        const val PROJECT_FILE = "project.json"
    }
}
