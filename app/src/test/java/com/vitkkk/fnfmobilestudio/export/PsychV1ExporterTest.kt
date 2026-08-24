package com.vitkkk.fnfmobilestudio.export

import com.vitkkk.fnfmobilestudio.model.Chart
import com.vitkkk.fnfmobilestudio.model.DifficultyChart
import com.vitkkk.fnfmobilestudio.model.Note
import com.vitkkk.fnfmobilestudio.model.NoteOwner
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.model.Song
import com.vitkkk.fnfmobilestudio.model.TempoPoint
import com.vitkkk.fnfmobilestudio.model.TimelineEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PsychV1ExporterTest {
    private val exporter = PsychV1Exporter()

    @Test
    fun exportsPsychV1NotesWithoutCouplingOwnerToSectionLane() {
        val song = Song(
            id = "test-song",
            displayName = "Test Song",
            bpm = 120.0,
            playerId = "bf-test",
            opponentId = "dad-test",
            difficulties = listOf(
                DifficultyChart(
                    id = "normal",
                    displayName = "Normal",
                    chart = Chart(
                        notes = listOf(
                            Note(0.0, lane = 2, owner = NoteOwner.PLAYER),
                            Note(500.0, lane = 1, owner = NoteOwner.OPPONENT, sustainMs = 250.0)
                        )
                    )
                )
            )
        )

        val bundle = exporter.export(Project(id = "p", name = "Project", songs = listOf(song)))
        val chart = bundle.artifacts.single { it.path == "data/test-song/test-song.json" }
        val root = Json.parseToJsonElement(requireNotNull(chart.textContent)).jsonObject
        val firstSection = root.getValue("notes").jsonArray.first().jsonObject
        val notes = firstSection.getValue("sectionNotes").jsonArray

        assertEquals("psych_v1", root.getValue("format").jsonPrimitive.content)
        assertEquals(2, notes[0].jsonArray[1].jsonPrimitive.content.toInt())
        assertEquals(5, notes[1].jsonArray[1].jsonPrimitive.content.toInt())
        assertEquals(250.0, notes[1].jsonArray[2].jsonPrimitive.content.toDouble(), 0.0001)
    }

    @Test
    fun createsSectionBoundaryAtTempoChangeAndExportsEvents() {
        val song = Song(
            id = "tempo-test",
            displayName = "Tempo Test",
            bpm = 120.0,
            tempoMap = listOf(
                TempoPoint(0.0, 120.0),
                TempoPoint(1000.0, 180.0)
            ),
            difficulties = listOf(
                DifficultyChart(
                    id = "hard",
                    displayName = "Hard",
                    chart = Chart(
                        notes = listOf(Note(1250.0, 0, NoteOwner.PLAYER)),
                        events = listOf(TimelineEvent(750.0, "Hey!", "bf", "0.5"))
                    )
                )
            )
        )

        val bundle = exporter.export(Project(id = "p", name = "Project", songs = listOf(song)))
        val artifact = bundle.artifacts.single { it.path == "data/tempo-test/tempo-test-hard.json" }
        val root = Json.parseToJsonElement(requireNotNull(artifact.textContent)).jsonObject
        val sections = root.getValue("notes").jsonArray

        assertTrue(sections.size >= 2)
        assertEquals(2.0, sections[0].jsonObject.getValue("sectionBeats").jsonPrimitive.content.toDouble(), 0.0001)
        assertEquals(true, sections[1].jsonObject.getValue("changeBPM").jsonPrimitive.content.toBoolean())
        assertEquals("Hey!", root.getValue("events").jsonArray[0].jsonArray[1].jsonArray[0].jsonArray[0].jsonPrimitive.content)
    }
}
