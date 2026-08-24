package com.vitkkk.fnfmobilestudio.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val iconAssetId: String? = null,
    val weeks: List<Week> = emptyList(),
    val songs: List<Song> = emptyList(),
    val characters: List<CharacterDefinition> = emptyList(),
    val stages: List<StageDefinition> = emptyList(),
    val spritePackages: List<SpritePackage> = emptyList(),
    val assets: List<AssetRef> = emptyList(),
    val settings: ProjectSettings = ProjectSettings()
)

@Serializable
data class ProjectSettings(
    val defaultExportProfile: String = "psych-v1",
    val autosaveEnabled: Boolean = true
)

@Serializable
data class Week(
    val id: String,
    val internalName: String,
    val displayName: String,
    val imageAssetId: String? = null,
    val songIds: List<String> = emptyList(),
    val defaultDifficulty: String = "normal",
    val characterIds: List<String> = emptyList(),
    val description: String = "",
    val colorArgb: Long? = null
)

@Serializable
data class Song(
    val id: String,
    val displayName: String,
    val bpm: Double,
    val stageId: String? = null,
    val playerId: String? = null,
    val opponentId: String? = null,
    val girlfriendId: String? = null,
    val instrumentalAssetId: String? = null,
    val vocalsAssetId: String? = null,
    val tempoMap: List<TempoPoint> = listOf(TempoPoint(0.0, bpm)),
    val difficulties: List<DifficultyChart> = listOf(DifficultyChart("normal", "Normal"))
)

@Serializable
data class TempoPoint(
    val timeMs: Double,
    val bpm: Double
)

@Serializable
data class DifficultyChart(
    val id: String,
    val displayName: String,
    val chart: Chart = Chart()
)

@Serializable
data class Chart(
    val notes: List<Note> = emptyList(),
    val events: List<TimelineEvent> = emptyList()
)

@Serializable
data class Note(
    val timeMs: Double,
    val lane: Int,
    val owner: NoteOwner,
    val sustainMs: Double = 0.0,
    val type: String = ""
)

@Serializable
enum class NoteOwner {
    PLAYER,
    OPPONENT,
    GIRLFRIEND,
    OTHER
}

@Serializable
data class TimelineEvent(
    val timeMs: Double,
    val type: String,
    val value1: String = "",
    val value2: String = ""
)

@Serializable
data class CharacterDefinition(
    val id: String,
    val displayName: String,
    val imageAssetId: String? = null,
    val iconAssetId: String? = null,
    val scale: Double = 1.0,
    val flipX: Boolean = false,
    val antialiasing: Boolean = true,
    val cameraOffsetX: Double = 0.0,
    val cameraOffsetY: Double = 0.0,
    val animations: List<CharacterAnimation> = emptyList()
)

@Serializable
data class CharacterAnimation(
    val id: String,
    val frameAssetIds: List<String> = emptyList(),
    val fps: Double = 24.0,
    val loop: Boolean = false,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0
)

@Serializable
data class StageDefinition(
    val id: String,
    val displayName: String,
    val defaultZoom: Double = 0.9,
    val cameraSpeed: Double = 1.0,
    val boyfriend: StageCharacterSlot = StageCharacterSlot(770.0, 100.0),
    val opponent: StageCharacterSlot = StageCharacterSlot(100.0, 100.0),
    val girlfriend: StageCharacterSlot = StageCharacterSlot(400.0, 130.0),
    val hideGirlfriend: Boolean = false,
    val objects: List<StageObject> = emptyList(),
    val previewAssetId: String? = null,
    val previewBoyfriendId: String? = "bf",
    val previewOpponentId: String? = "dad",
    val previewGirlfriendId: String? = "gf"
)

@Serializable
data class StageCharacterSlot(
    val x: Double,
    val y: Double,
    val cameraOffsetX: Double = 0.0,
    val cameraOffsetY: Double = 0.0
)

@Serializable
data class StageObject(
    val id: String,
    val name: String,
    val assetId: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val scrollX: Double = 1.0,
    val scrollY: Double = 1.0,
    val alpha: Double = 1.0,
    val angle: Double = 0.0,
    val flipX: Boolean = false,
    val antialiasing: Boolean = true,
    val layer: Int = 0,
    val packageId: String? = null,
    val editorKind: EditorObjectKind = EditorObjectKind.SCENERY
)

@Serializable
data class SpritePackage(
    val id: String,
    val displayName: String,
    val kind: EditorObjectKind = EditorObjectKind.ITEM,
    val imageAssetId: String? = null,
    val iconAssetId: String? = null,
    val characterId: String? = null
)

@Serializable
enum class EditorObjectKind {
    ICON,
    ITEM,
    SCENERY,
    CHARACTER
}

@Serializable
data class AssetRef(
    val id: String,
    val relativePath: String,
    val kind: AssetKind
)

@Serializable
enum class AssetKind {
    IMAGE,
    AUDIO,
    FRAME,
    OTHER
}
