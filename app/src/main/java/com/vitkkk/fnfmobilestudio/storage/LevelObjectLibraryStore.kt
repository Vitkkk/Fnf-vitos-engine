package com.vitkkk.fnfmobilestudio.storage

import com.vitkkk.fnfmobilestudio.model.StageObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
enum class LevelObjectKind {
    ICON,
    ITEM,
    SCENERY,
    CHARACTER
}

@Serializable
data class LevelObjectTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: LevelObjectKind,
    val assetId: String? = null,
    val characterId: String? = null,
    val defaultScale: Double = 1.0
)

@Serializable
data class LevelObjectPackage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<LevelObjectTemplate> = emptyList()
)

@Serializable
data class LevelObjectLibrary(
    val packages: List<LevelObjectPackage> = emptyList()
)

class LevelObjectLibraryStore(private val projectStore: ProjectStore) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(projectId: String): LevelObjectLibrary = withContext(Dispatchers.IO) {
        val file = file(projectId)
        if (!file.isFile) return@withContext LevelObjectLibrary()
        runCatching { json.decodeFromString<LevelObjectLibrary>(file.readText()) }
            .getOrDefault(LevelObjectLibrary())
    }

    suspend fun save(projectId: String, library: LevelObjectLibrary) = withContext(Dispatchers.IO) {
        val file = file(projectId)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(library))
        if (file.exists()) file.delete()
        check(temp.renameTo(file)) { "Could not save level object library" }
    }

    fun instantiate(template: LevelObjectTemplate, assetId: String, layer: Int): StageObject = StageObject(
        id = "obj-${UUID.randomUUID().toString().take(8)}",
        name = template.name,
        assetId = assetId,
        x = 640.0,
        y = 360.0,
        scaleX = template.defaultScale,
        scaleY = template.defaultScale,
        layer = layer
    )

    private fun file(projectId: String): File =
        File(projectStore.projectDirectory(projectId), "editor/level-object-library.json")
}
