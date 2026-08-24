package com.vitkkk.fnfmobilestudio.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

private data class NoteKey(val timeMs: Double, val lane: Int, val owner: NoteOwner)

@Composable
internal fun ChartEditorScreenV4(
    project: Project,
    songId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val song = project.songs.firstOrNull { it.id == songId } ?: return
    val difficulty = song.difficulties.firstOrNull() ?: return
    val chart = difficulty.chart
    val bpm = song.bpm.coerceAtLeast(1.0)
    val beatMs = 60_000.0 / bpm
    val snapValues = listOf(4, 8, 12, 16, 24, 32)
    var snapIndex by remember(songId) { mutableStateOf(3) }
    val snap = snapValues[snapIndex]
    val snapMs = beatMs * 4.0 / snap
    var zoom by remember(songId) { mutableStateOf(1.0) }
    var startMs by remember(songId) { mutableStateOf(0.0) }
    var selectedKey by remember(songId) { mutableStateOf<NoteKey?>(null) }
    var showEventsNotice by remember { mutableStateOf(false) }
    var currentMs by remember(songId) { mutableStateOf(0.0) }
    var isPlaying by remember(songId) { mutableStateOf(false) }
    var followPlayhead by remember(songId) { mutableStateOf(true) }

    val audioAsset = project.assets.firstOrNull { it.id == song.instrumentalAssetId }
    val audioPath = audioAsset?.let { store.assetFile(project.id, it).takeIf { file -> file.isFile }?.absolutePath }
    val player = remember(audioPath) {
        audioPath?.let { path ->
            runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            }.getOrNull()
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player?.release() } }
    }

    LaunchedEffect(isPlaying, player, followPlayhead, zoom) {
        while (isPlaying && player != null) {
            currentMs = player.currentPosition.toDouble()
            if (!player.isPlaying) {
                isPlaying = false
                break
            }
            if (followPlayhead) {
                val visibleBeats = 8.0 / zoom
                startMs = max(0.0, currentMs - beatMs * visibleBeats * 0.30)
            }
            delay(24)
        }
    }

    fun replaceChart(newChart: Chart) {
        val newDifficulties = song.difficulties.mapIndexed { index, d ->
            if (index == 0) d.copy(chart = newChart) else d
        }
        val updatedSong = song.copy(difficulties = newDifficulties)
        onProjectChange(project.copy(songs = project.songs.map { if (it.id == song.id) updatedSong else it }))
    }

    val selected = selectedKey?.let { key ->
        chart.notes.firstOrNull {
            it.timeMs == key.timeMs && it.lane == key.lane && it.owner == key.owner
        }
    }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Chart • ${song.displayName}", onBack = onBack) {
                MakerSquareButton("⌂", onHome, size = 54.dp)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MakerButton("NOTAS", {}, Modifier.weight(1f), MakerButtonTone.ACCENT)
                MakerButton("EVENTOS", { showEventsNotice = true }, Modifier.weight(1f), MakerButtonTone.DARK)
            }

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Column(Modifier.weight(0.62f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MakerSquareButton("−", { zoom = (zoom / 1.25).coerceAtLeast(0.5) }, size = 44.dp)
                        MakerButton("ZOOM x${"%.2f".format(zoom)}", {}, Modifier.weight(1f), MakerButtonTone.DARK)
                        MakerSquareButton("+", { zoom = (zoom * 1.25).coerceAtMost(4.0) }, size = 44.dp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MakerSquareButton("◀", {
                            snapIndex = (snapIndex - 1).coerceAtLeast(0)
                        }, size = 40.dp)
                        MakerButton("SNAP 1/$snap", {}, Modifier.weight(1f), MakerButtonTone.DARK)
                        MakerSquareButton("▶", {
                            snapIndex = (snapIndex + 1).coerceAtMost(snapValues.lastIndex)
                        }, size = 40.dp)
                    }
                    Spacer(Modifier.height(6.dp))

                    ChartGridV4(
                        notes = chart.notes,
                        bpm = bpm,
                        snap = snap,
                        zoom = zoom,
                        startMs = startMs,
                        currentMs = currentMs,
                        selected = selectedKey,
                        onScrollMs = { delta -> startMs = max(0.0, startMs + delta) },
                        onTapCell = { owner, lane, timeMs ->
                            val threshold = snapMs * 0.42
                            val existing = chart.notes.minByOrNull { note ->
                                if (note.owner == owner && note.lane == lane) kotlin.math.abs(note.timeMs - timeMs) else Double.MAX_VALUE
                            }?.takeIf { kotlin.math.abs(it.timeMs - timeMs) <= threshold }
                            if (existing != null) {
                                selectedKey = NoteKey(existing.timeMs, existing.lane, existing.owner)
                            } else {
                                val note = Note(timeMs = timeMs, lane = lane, owner = owner)
                                replaceChart(chart.copy(notes = (chart.notes + note).sortedBy { it.timeMs }))
                                selectedKey = NoteKey(note.timeMs, note.lane, note.owner)
                            }
                        }
                    )
                }

                Column(Modifier.weight(0.38f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MakerPanel("Propriedades da nota", Modifier.weight(1f)) {
                        if (selected == null) {
                            Text(
                                "Toque em uma célula para criar uma nota. Toque em uma nota existente para selecioná-la.",
                                color = MakerPalette.Muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                            )
                        } else {
                            MakerLabelValue("Lado", if (selected.owner == NoteOwner.OPPONENT) "INIMIGO" else "JOGADOR")
                            MakerLabelValue("Lane", (selected.lane + 1).toString())
                            MakerLabelValue("Tempo", "${selected.timeMs.toInt()} ms")
                            MakerValueStepper(
                                label = "Duração",
                                value = "${selected.sustainMs.toInt()} ms",
                                onMinus = {
                                    val newValue = max(0.0, selected.sustainMs - snapMs)
                                    val newNote = selected.copy(sustainMs = newValue)
                                    replaceChart(chart.copy(notes = chart.notes.map { if (it == selected) newNote else it }))
                                    selectedKey = NoteKey(newNote.timeMs, newNote.lane, newNote.owner)
                                },
                                onPlus = {
                                    val newNote = selected.copy(sustainMs = selected.sustainMs + snapMs)
                                    replaceChart(chart.copy(notes = chart.notes.map { if (it == selected) newNote else it }))
                                    selectedKey = NoteKey(newNote.timeMs, newNote.lane, newNote.owner)
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            MakerButton(
                                "🗑 EXCLUIR NOTA",
                                {
                                    replaceChart(chart.copy(notes = chart.notes - selected))
                                    selectedKey = null
                                },
                                Modifier.fillMaxWidth(),
                                MakerButtonTone.DANGER
                            )
                        }
                    }
                    MakerButton(
                        if (followPlayhead) "PLAYHEAD: SEGUIR" else "PLAYHEAD: LIVRE",
                        { followPlayhead = !followPlayhead },
                        Modifier.fillMaxWidth(),
                        MakerButtonTone.DARK
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(6.dp))
                            .padding(7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(formatTime(currentMs), color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MakerSquareButton("◀◀", {
                    val target = max(0, (player?.currentPosition ?: currentMs.toInt()) - 5000)
                    player?.seekTo(target)
                    currentMs = target.toDouble()
                    startMs = max(0.0, target - beatMs * 2)
                }, modifier = Modifier.weight(0.22f), size = 58.dp)
                MakerButton(
                    if (isPlaying) "Ⅱ" else "▶",
                    {
                        if (player != null) {
                            if (isPlaying) player.pause() else player.start()
                            isPlaying = !isPlaying
                        }
                    },
                    Modifier.weight(0.56f),
                    MakerButtonTone.NORMAL,
                    enabled = player != null
                )
                MakerSquareButton("▶▶", {
                    val duration = player?.duration ?: Int.MAX_VALUE
                    val target = ((player?.currentPosition ?: currentMs.toInt()) + 5000).coerceAtMost(duration)
                    player?.seekTo(target)
                    currentMs = target.toDouble()
                    startMs = max(0.0, target - beatMs * 2)
                }, modifier = Modifier.weight(0.22f), size = 58.dp)
            }
        }

        if (showEventsNotice) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showEventsNotice = false },
                title = { Text("Eventos") },
                text = { Text("A trilha de eventos já existe no modelo interno, mas o editor visual de eventos fica para uma etapa futura.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showEventsNotice = false }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
private fun ChartGridV4(
    notes: List<Note>,
    bpm: Double,
    snap: Int,
    zoom: Double,
    startMs: Double,
    currentMs: Double,
    selected: NoteKey?,
    onScrollMs: (Double) -> Unit,
    onTapCell: (NoteOwner, Int, Double) -> Unit
) {
    val beatMs = 60_000.0 / bpm.coerceAtLeast(1.0)
    val visibleBeats = 8.0 / zoom
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101632), RoundedCornerShape(5.dp))
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(startMs, zoom, snap) {
                    detectTapGestures { pos ->
                        val laneWidth = size.width / 8f
                        val globalLane = floor(pos.x / laneWidth).toInt().coerceIn(0, 7)
                        val owner = if (globalLane < 4) NoteOwner.OPPONENT else NoteOwner.PLAYER
                        val lane = globalLane % 4
                        val pxPerBeat = size.height / visibleBeats.toFloat()
                        val rawMs = startMs + (pos.y / pxPerBeat) * beatMs
                        val snapMs = beatMs * 4.0 / snap
                        val snapped = round(rawMs / snapMs) * snapMs
                        onTapCell(owner, lane, max(0.0, snapped))
                    }
                }
                .pointerInput(startMs, zoom) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val pxPerBeat = size.height / visibleBeats.toFloat()
                        val msPerPx = beatMs / pxPerBeat
                        onScrollMs(-dragAmount.y * msPerPx)
                    }
                }
        ) {
            val laneWidth = size.width / 8f
            val pxPerBeat = size.height / visibleBeats.toFloat()
            val endMs = startMs + visibleBeats * beatMs

            for (lane in 0..8) {
                val x = lane * laneWidth
                drawLine(
                    color = if (lane == 4) MakerPalette.GridStrong else MakerPalette.Grid,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (lane == 4) 4f else 2f
                )
            }

            val snapMs = beatMs * 4.0 / snap
            val firstStep = floor(startMs / snapMs).toInt()
            val lastStep = floor(endMs / snapMs).toInt() + 1
            for (step in firstStep..lastStep) {
                val t = step * snapMs
                val y = ((t - startMs) / beatMs * pxPerBeat).toFloat()
                val beatBoundary = kotlin.math.abs((t / beatMs) - round(t / beatMs)) < 0.001
                drawLine(
                    color = if (beatBoundary) MakerPalette.GridStrong else MakerPalette.Grid.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (beatBoundary) 3f else 1f
                )
            }

            val playheadY = ((currentMs - startMs) / beatMs * pxPerBeat).toFloat()
            if (playheadY in 0f..size.height) {
                drawLine(Color.White, Offset(0f, playheadY), Offset(size.width, playheadY), strokeWidth = 2f)
            }

            notes.filter { it.timeMs + it.sustainMs >= startMs && it.timeMs <= endMs }.forEach { note ->
                val globalLane = (if (note.owner == NoteOwner.OPPONENT) 0 else 4) + note.lane.coerceIn(0, 3)
                val x = globalLane * laneWidth
                val y = ((note.timeMs - startMs) / beatMs * pxPerBeat).toFloat()
                val noteColor = laneColor(note.lane)
                if (note.sustainMs > 0) {
                    val sustainHeight = (note.sustainMs / beatMs * pxPerBeat).toFloat()
                    drawRoundRect(
                        color = noteColor.copy(alpha = 0.75f),
                        topLeft = Offset(x + laneWidth * 0.38f, y + laneWidth * 0.30f),
                        size = Size(laneWidth * 0.24f, sustainHeight.coerceAtLeast(3f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
                drawRoundRect(
                    color = noteColor,
                    topLeft = Offset(x + laneWidth * 0.12f, y - laneWidth * 0.34f),
                    size = Size(laneWidth * 0.76f, laneWidth * 0.68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                )
                if (selected == NoteKey(note.timeMs, note.lane, note.owner)) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(x + laneWidth * 0.08f, y - laneWidth * 0.38f),
                        size = Size(laneWidth * 0.84f, laneWidth * 0.76f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(8) { i ->
                Text(
                    when (i % 4) { 0 -> "←"; 1 -> "↓"; 2 -> "↑"; else -> "→" },
                    color = laneColor(i % 4),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            "INIMIGO",
            color = MakerPalette.Muted,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            fontWeight = FontWeight.Bold
        )
        Text(
            "JOGADOR",
            color = MakerPalette.Muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

private fun laneColor(lane: Int): Color = when (lane) {
    0 -> Color(0xFFB657C6)
    1 -> Color(0xFF38D8E8)
    2 -> Color(0xFF6DE35D)
    else -> Color(0xFFE64D4D)
}

private fun formatTime(ms: Double): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
