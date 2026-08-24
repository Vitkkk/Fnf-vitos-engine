package com.vitkkk.fnfmobilestudio.export

import com.vitkkk.fnfmobilestudio.model.Chart
import com.vitkkk.fnfmobilestudio.model.Note
import com.vitkkk.fnfmobilestudio.model.NoteOwner
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.model.Song
import com.vitkkk.fnfmobilestudio.model.StageDefinition
import com.vitkkk.fnfmobilestudio.model.TempoPoint
import com.vitkkk.fnfmobilestudio.model.Week
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

/**
 * Export profile based on the archived ShadowMario/FNF-PsychEngine main
 * chart format (`format = psych_v1`). Editor state never depends on these DTOs.
 */
class PsychV1Exporter : ModExporter {
    override val profileId: String = "psych-v1"

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "\t"
    }

    override fun export(project: Project): ExportBundle {
        val artifacts = mutableListOf<ExportArtifact>()
        val warnings = mutableListOf<String>()
        val songsById = project.songs.associateBy { it.id }
        val assetsById = project.assets.associateBy { it.id }

        project.songs.forEach { song ->
            song.difficulties.forEach { difficulty ->
                val songFolder = psychPath(song.displayName)
                val chartName = if (difficulty.id.equals("normal", ignoreCase = true)) {
                    songFolder
                } else {
                    "$songFolder-${psychPath(difficulty.id)}"
                }
                artifacts += ExportArtifact(
                    path = "data/$songFolder/$chartName.json",
                    textContent = json.encodeToString(JsonObject.serializer(), buildSong(song, difficulty.chart))
                )
            }

            queueAudioArtifact(
                song = song,
                assetId = song.instrumentalAssetId,
                targetName = "Inst.ogg",
                assetsById = assetsById,
                artifacts = artifacts,
                warnings = warnings
            )
            queueAudioArtifact(
                song = song,
                assetId = song.vocalsAssetId,
                targetName = "Voices.ogg",
                assetsById = assetsById,
                artifacts = artifacts,
                warnings = warnings
            )
        }

        project.weeks.forEach { week ->
            val missingSongs = week.songIds.filterNot(songsById::containsKey)
            if (missingSongs.isNotEmpty()) {
                warnings += "Week ${week.id}: missing song references ${missingSongs.joinToString()}"
            }
            artifacts += ExportArtifact(
                path = "weeks/${psychPath(week.id)}.json",
                textContent = json.encodeToString(
                    JsonObject.serializer(),
                    buildWeek(week, week.songIds.mapNotNull(songsById::get))
                )
            )
        }

        val assignedSongIds = project.weeks.flatMap { it.songIds }.toSet()
        val freeplayOnlySongs = project.songs.filterNot { it.id in assignedSongIds }
        val freeplayTechnicalWeekId = if (freeplayOnlySongs.isNotEmpty()) uniqueFreeplayWeekId(project) else null
        if (freeplayTechnicalWeekId != null) {
            val technicalWeek = Week(
                id = freeplayTechnicalWeekId,
                internalName = freeplayTechnicalWeekId,
                displayName = "Freeplay",
                songIds = freeplayOnlySongs.map { it.id }
            )
            artifacts += ExportArtifact(
                path = "weeks/${psychPath(freeplayTechnicalWeekId)}.json",
                textContent = json.encodeToString(
                    JsonObject.serializer(),
                    buildWeek(technicalWeek, freeplayOnlySongs, hideStoryMode = true)
                )
            )
        }

        project.stages.forEach { stage ->
            artifacts += ExportArtifact(
                path = "stages/${psychPath(stage.id)}.json",
                textContent = json.encodeToString(
                    JsonObject.serializer(),
                    buildStage(stage, assetsById.mapValues { it.value.relativePath })
                )
            )
        }

        val weekIds = buildList {
            addAll(project.weeks.map { psychPath(it.id) })
            if (freeplayTechnicalWeekId != null) add(psychPath(freeplayTechnicalWeekId))
        }
        if (weekIds.isNotEmpty()) {
            artifacts += ExportArtifact(
                path = "weeks/weekList.txt",
                textContent = weekIds.joinToString("\n") + "\n"
            )
        }

        return ExportBundle(profileId, artifacts, warnings.distinct())
    }

    private fun queueAudioArtifact(
        song: Song,
        assetId: String?,
        targetName: String,
        assetsById: Map<String, com.vitkkk.fnfmobilestudio.model.AssetRef>,
        artifacts: MutableList<ExportArtifact>,
        warnings: MutableList<String>
    ) {
        if (assetId == null) return
        val asset = assetsById[assetId]
        if (asset == null) {
            warnings += "Song ${song.displayName}: audio asset '$assetId' is missing"
            return
        }
        val extension = asset.relativePath.substringAfterLast('.', "").lowercase()
        if (extension != "ogg") {
            warnings += "Song ${song.displayName}: ${asset.relativePath} must be transcoded to OGG before Psych export; this build will not disguise .$extension bytes as .ogg"
            return
        }
        artifacts += ExportArtifact(
            path = "songs/${psychPath(song.displayName)}/$targetName",
            sourceAssetId = assetId
        )
    }

    private fun uniqueFreeplayWeekId(project: Project): String {
        val used = project.weeks.map { psychPath(it.id) }.toSet()
        var id = "fnfms-freeplay-only"
        var suffix = 2
        while (psychPath(id) in used) {
            id = "fnfms-freeplay-only-$suffix"
            suffix++
        }
        return id
    }

    private fun buildSong(song: Song, chart: Chart): JsonObject {
        chart.notes.forEach { note -> require(note.lane in 0..3) { "Lane must be 0..3, got ${note.lane}" } }
        val sections = buildSections(song, chart.notes)
        val events = buildJsonArray {
            chart.events.sortedBy { it.timeMs }.forEach { event ->
                add(buildJsonArray {
                    add(JsonPrimitive(event.timeMs))
                    add(buildJsonArray {
                        add(buildJsonArray {
                            add(JsonPrimitive(event.type))
                            add(JsonPrimitive(event.value1))
                            add(JsonPrimitive(event.value2))
                        })
                    })
                })
            }
        }

        return buildJsonObject {
            put("song", song.displayName)
            put("notes", JsonArray(sections))
            put("events", events)
            put("bpm", song.bpm)
            put("needsVoices", song.vocalsAssetId != null)
            put("speed", 1.0)
            put("offset", 0.0)
            put("player1", song.playerId ?: "bf")
            put("player2", song.opponentId ?: "dad")
            put("gfVersion", song.girlfriendId ?: "gf")
            put("stage", song.stageId ?: "stage")
            put("format", "psych_v1")
        }
    }

    private fun buildSections(song: Song, notes: List<Note>): List<JsonObject> {
        val tempos = normalizedTempoMap(song)
        val lastNoteTime = notes.maxOfOrNull { it.timeMs + max(0.0, it.sustainMs) } ?: 0.0
        val lastTempoTime = tempos.maxOfOrNull { it.timeMs } ?: 0.0
        val maxTime = max(lastNoteTime, lastTempoTime)

        val result = mutableListOf<JsonObject>()
        var sectionStart = 0.0
        var previousBpm: Double? = null
        var previousMustHit = false
        var safety = 0

        do {
            val tempo = activeTempo(tempos, sectionStart)
            val beatMs = 60_000.0 / tempo.bpm
            val normalEnd = sectionStart + beatMs * 4.0
            val nextTempo = tempos.firstOrNull { it.timeMs > sectionStart + EPSILON }
            val sectionEnd = if (nextTempo != null && nextTempo.timeMs < normalEnd - EPSILON) nextTempo.timeMs else normalEnd
            val sectionBeats = (sectionEnd - sectionStart) / beatMs
            val sectionNotes = notes.filter { it.timeMs >= sectionStart - EPSILON && it.timeMs < sectionEnd - EPSILON }
            val playerCount = sectionNotes.count { it.owner == NoteOwner.PLAYER }
            val opponentCount = sectionNotes.count { it.owner == NoteOwner.OPPONENT || it.owner == NoteOwner.OTHER }
            val gfCount = sectionNotes.count { it.owner == NoteOwner.GIRLFRIEND }
            val mustHit = when {
                playerCount > opponentCount + gfCount -> true
                opponentCount + gfCount > playerCount -> false
                else -> previousMustHit
            }
            previousMustHit = mustHit
            val changeBpm = previousBpm != null && kotlin.math.abs(previousBpm!! - tempo.bpm) > EPSILON

            result += buildJsonObject {
                put("sectionNotes", buildJsonArray {
                    sectionNotes.sortedBy { it.timeMs }.forEach { add(buildPsychNote(it)) }
                })
                put("sectionBeats", sectionBeats)
                put("mustHitSection", mustHit)
                put("gfSection", gfCount > 0 && gfCount >= opponentCount)
                put("altAnim", false)
                put("bpm", tempo.bpm)
                put("changeBPM", changeBpm)
            }

            previousBpm = tempo.bpm
            sectionStart = sectionEnd
            safety++
        } while ((sectionStart <= maxTime + EPSILON || result.isEmpty()) && safety < 100_000)

        check(safety < 100_000) { "Section builder exceeded safety limit" }
        return result
    }

    private fun buildPsychNote(note: Note): JsonArray = buildJsonArray {
        add(JsonPrimitive(note.timeMs))
        val sideOffset = if (note.owner == NoteOwner.PLAYER) 0 else 4
        add(JsonPrimitive(note.lane + sideOffset))
        add(JsonPrimitive(max(0.0, note.sustainMs)))
        add(JsonPrimitive(note.type))
    }

    private fun normalizedTempoMap(song: Song): List<TempoPoint> {
        val sorted = song.tempoMap
            .filter { it.bpm > 0.0 && it.timeMs >= 0.0 }
            .sortedBy { it.timeMs }
            .toMutableList()
        if (sorted.isEmpty() || sorted.first().timeMs > EPSILON) {
            sorted.add(0, TempoPoint(0.0, song.bpm))
        }
        return sorted.distinctBy { it.timeMs }
    }

    private fun activeTempo(tempos: List<TempoPoint>, timeMs: Double): TempoPoint =
        tempos.lastOrNull { it.timeMs <= timeMs + EPSILON } ?: tempos.first()

    private fun buildWeek(week: Week, songs: List<Song>, hideStoryMode: Boolean = false): JsonObject = buildJsonObject {
        put("songs", buildJsonArray {
            songs.forEach { song ->
                add(buildJsonArray {
                    add(JsonPrimitive(song.displayName))
                    add(JsonPrimitive("face"))
                    add(buildJsonArray {
                        add(JsonPrimitive(146))
                        add(JsonPrimitive(113))
                        add(JsonPrimitive(253))
                    })
                })
            }
        })
        put("weekCharacters", buildJsonArray {
            val ids = week.characterIds.ifEmpty { listOf("dad", "bf", "gf") }
            repeat(3) { index -> add(JsonPrimitive(ids.getOrElse(index) { listOf("dad", "bf", "gf")[index] })) }
        })
        put("weekBackground", "stage")
        put("weekBefore", "")
        put("storyName", week.displayName)
        put("weekName", week.displayName)
        put("startUnlocked", true)
        put("hiddenUntilUnlocked", false)
        put("hideStoryMode", hideStoryMode)
        put("hideFreeplay", false)
        put("difficulties", "")
    }

    private fun buildStage(stage: StageDefinition, assetPaths: Map<String, String>): JsonObject = buildJsonObject {
        put("directory", "")
        put("defaultZoom", stage.defaultZoom)
        put("stageUI", "normal")
        put("boyfriend", point(stage.boyfriend.x, stage.boyfriend.y))
        put("girlfriend", point(stage.girlfriend.x, stage.girlfriend.y))
        put("opponent", point(stage.opponent.x, stage.opponent.y))
        put("hide_girlfriend", stage.hideGirlfriend)
        put("camera_boyfriend", point(stage.boyfriend.cameraOffsetX, stage.boyfriend.cameraOffsetY))
        put("camera_opponent", point(stage.opponent.cameraOffsetX, stage.opponent.cameraOffsetY))
        put("camera_girlfriend", point(stage.girlfriend.cameraOffsetX, stage.girlfriend.cameraOffsetY))
        put("camera_speed", stage.cameraSpeed)
        put("objects", buildJsonArray {
            stage.objects.sortedBy { it.layer }.forEach { obj ->
                add(buildJsonObject {
                    put("type", "sprite")
                    put("name", obj.name)
                    put("x", obj.x)
                    put("y", obj.y)
                    put("image", psychImagePath(assetPaths[obj.assetId] ?: obj.assetId))
                    put("scale", point(obj.scaleX, obj.scaleY))
                    put("scroll", point(obj.scrollX, obj.scrollY))
                    put("alpha", obj.alpha)
                    put("angle", obj.angle)
                    put("flipX", obj.flipX)
                    put("flipY", false)
                    put("antialiasing", obj.antialiasing)
                    put("color", "FFFFFF")
                    put("filters", 0)
                })
            }
        })
    }

    private fun point(x: Double, y: Double): JsonArray = buildJsonArray {
        add(JsonPrimitive(x))
        add(JsonPrimitive(y))
    }

    private fun psychImagePath(relativePath: String): String = relativePath
        .replace('\\', '/')
        .removePrefix("images/")
        .substringBeforeLast('.', relativePath.replace('\\', '/').removePrefix("images/"))

    private fun psychPath(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "untitled" }

    private companion object {
        const val EPSILON = 0.0001
    }
}
