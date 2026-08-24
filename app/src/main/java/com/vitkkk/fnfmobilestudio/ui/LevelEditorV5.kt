package com.vitkkk.fnfmobilestudio.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private enum class LevelTabV5 { STAGE, CHART, EVENTS }
private data class LevelNoteKeyV5(val timeMs: Double, val lane: Int, val owner: NoteOwner)

@Composable
internal fun LevelEditorScreenV5(
    project: Project,
    songId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val song = project.songs.firstOrNull { it.id == songId } ?: return
    val stageId = song.stageId ?: "stage"
    val initialStage = project.stages.firstOrNull { it.id == stageId }
        ?: StageDefinition(id = stageId, displayName = if (stageId == "stage") "Stage" else stageId)

    var stage by remember(songId, stageId) { mutableStateOf(initialStage) }
    var tab by remember(songId) { mutableStateOf(LevelTabV5.CHART) }
    var selectedStageItem by remember { mutableStateOf<String?>(null) }
    var showPackages by remember { mutableStateOf(false) }
    var showCreatePackage by remember { mutableStateOf(false) }
    var packageKindPending by remember { mutableStateOf<EditorObjectKind?>(null) }
    var packageNamePending by remember { mutableStateOf("") }
    var showEventsNotice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project.stages, stageId) {
        project.stages.firstOrNull { it.id == stageId }?.let { persisted ->
            if (persisted != stage) stage = persisted
        }
    }

    fun persistStage(updated: StageDefinition = stage, updatedProject: Project = project) {
        stage = updated
        val stages = if (updatedProject.stages.any { it.id == updated.id }) {
            updatedProject.stages.map { if (it.id == updated.id) updated else it }
        } else {
            updatedProject.stages + updated
        }
        onProjectChange(updatedProject.copy(stages = stages))
    }

    val addImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "level-${song.id}")
            val obj = StageObject(
                id = "obj-${UUID.randomUUID().toString().take(8)}",
                name = asset.relativePath.substringAfterLast('/').substringBeforeLast('.'),
                assetId = asset.id,
                x = 640.0,
                y = 360.0,
                layer = (stage.objects.maxOfOrNull { it.layer } ?: -1) + 1,
                editorKind = EditorObjectKind.ITEM
            )
            selectedStageItem = "obj:${obj.id}"
            persistStage(stage.copy(objects = stage.objects + obj), project.copy(assets = project.assets + asset))
        }
    }

    val addBackgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "level-${song.id}")
            val obj = StageObject(
                id = "bg-${UUID.randomUUID().toString().take(8)}",
                name = "background-${stage.objects.count { it.editorKind == EditorObjectKind.SCENERY } + 1}",
                assetId = asset.id,
                x = 640.0,
                y = 360.0,
                scaleX = 2.4,
                scaleY = 2.4,
                layer = (stage.objects.minOfOrNull { it.layer } ?: 0) - 1,
                editorKind = EditorObjectKind.SCENERY
            )
            selectedStageItem = "obj:${obj.id}"
            persistStage(stage.copy(objects = stage.objects + obj), project.copy(assets = project.assets + asset))
        }
    }

    val packageImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val kind = packageKindPending
        if (uri != null && kind != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "packages")
            val pack = SpritePackage(
                id = "pack-${UUID.randomUUID().toString().take(8)}",
                displayName = packageNamePending.trim().ifBlank { kind.name.lowercase() },
                kind = kind,
                imageAssetId = asset.id
            )
            onProjectChange(project.copy(assets = project.assets + asset, spritePackages = project.spritePackages + pack))
            packageKindPending = null
            packageNamePending = ""
            showPackages = true
        }
    }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Nível • ${song.displayName}", onBack = onBack) {
                MakerSquareButton("⌂", onHome, size = 54.dp)
            }

            LevelPreviewV5(
                project = project,
                song = song,
                stage = stage,
                store = store,
                selected = selectedStageItem,
                interactive = tab == LevelTabV5.STAGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (tab == LevelTabV5.STAGE) 230.dp else 150.dp)
                    .padding(horizontal = 10.dp),
                onSelect = { selectedStageItem = it },
                onMoveObject = { id, dx, dy ->
                    stage = stage.copy(objects = stage.objects.map {
                        if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
                    })
                },
                onMoveCharacter = { role, dx, dy ->
                    stage = when (role) {
                        "opp" -> stage.copy(opponent = stage.opponent.copy(x = stage.opponent.x + dx, y = stage.opponent.y + dy))
                        "gf" -> stage.copy(girlfriend = stage.girlfriend.copy(x = stage.girlfriend.x + dx, y = stage.girlfriend.y + dy))
                        else -> stage.copy(boyfriend = stage.boyfriend.copy(x = stage.boyfriend.x + dx, y = stage.boyfriend.y + dy))
                    }
                }
            )

            Spacer(Modifier.height(7.dp))
            MakerTabRow(
                labels = listOf("Stage", "Chart", "Eventos"),
                selected = tab.ordinal,
                onSelect = { index: Int ->
                    when (index) {
                        0 -> tab = LevelTabV5.STAGE
                        1 -> tab = LevelTabV5.CHART
                        else -> showEventsNotice = true
                    }
                },
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            when (tab) {
                LevelTabV5.STAGE -> LevelStageToolsV5(
                    stage = stage,
                    selected = selectedStageItem,
                    onSelected = { selectedStageItem = it },
                    onStage = { stage = it },
                    onSave = { persistStage(stage) },
                    onAddImage = { addImageLauncher.launch(arrayOf("image/*")) },
                    onAddBackground = { addBackgroundLauncher.launch(arrayOf("image/*")) },
                    onPackages = { showPackages = true }
                )

                LevelTabV5.CHART -> LevelChartV5(
                    project = project,
                    song = song,
                    store = store,
                    onProjectChange = onProjectChange
                )

                LevelTabV5.EVENTS -> Unit
            }
        }
    }

    if (showPackages) {
        LevelPackageDialogV5(
            project = project,
            store = store,
            onDismiss = { showPackages = false },
            onCreate = { showPackages = false; showCreatePackage = true },
            onUse = { pack ->
                val assetId = pack.imageAssetId ?: return@LevelPackageDialogV5
                val obj = StageObject(
                    id = "obj-${UUID.randomUUID().toString().take(8)}",
                    name = pack.displayName,
                    assetId = assetId,
                    x = 640.0,
                    y = 360.0,
                    layer = (stage.objects.maxOfOrNull { it.layer } ?: -1) + 1,
                    packageId = pack.id,
                    editorKind = pack.kind
                )
                stage = stage.copy(objects = stage.objects + obj)
                selectedStageItem = "obj:${obj.id}"
                showPackages = false
            }
        )
    }

    if (showCreatePackage) {
        CreatePackageDialogV5(
            onDismiss = { showCreatePackage = false },
            onCreate = { name, kind ->
                showCreatePackage = false
                packageNamePending = name
                packageKindPending = kind
                packageImageLauncher.launch(arrayOf("image/*"))
            }
        )
    }

    if (showEventsNotice) {
        AlertDialog(
            onDismissRequest = { showEventsNotice = false },
            title = { Text("Eventos") },
            text = { Text("A aba já faz parte do Level Editor, mas o editor de eventos continua reservado para uma etapa futura.") },
            confirmButton = { TextButton(onClick = { showEventsNotice = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun LevelPreviewV5(
    project: Project,
    song: Song,
    stage: StageDefinition,
    store: ProjectStore,
    selected: String?,
    interactive: Boolean,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onMoveObject: (String, Double, Double) -> Unit,
    onMoveCharacter: (String, Double, Double) -> Unit
) {
    BoxWithConstraints(
        modifier
            .background(Color.Black, RoundedCornerShape(7.dp))
            .border(2.dp, MakerPalette.HeaderDark, RoundedCornerShape(7.dp))
    ) {
        val widthDp = maxWidth.value.coerceAtLeast(1f)
        val heightDp = maxHeight.value.coerceAtLeast(1f)
        val density = LocalDensity.current.density

        stage.objects.sortedBy { it.layer }.forEach { obj ->
            val base = if (obj.editorKind == EditorObjectKind.SCENERY) 126.dp else 76.dp
            val objectWidth = base * obj.scaleX.toFloat().coerceIn(0.15f, 5f)
            val objectHeight = base * obj.scaleY.toFloat().coerceIn(0.15f, 5f)
            val x = maxWidth * (obj.x / 1280.0).toFloat()
            val y = maxHeight * (obj.y / 720.0).toFloat()
            val gesture = if (interactive) {
                Modifier.pointerInput(obj.id, widthDp, heightDp) {
                    detectDragGestures(onDragStart = { onSelect("obj:${obj.id}") }) { _, drag ->
                        val dxDp = drag.x / density
                        val dyDp = drag.y / density
                        onMoveObject(obj.id, dxDp / widthDp * 1280.0, dyDp / heightDp * 720.0)
                    }
                }
            } else Modifier

            Box(
                Modifier
                    .offset(x = x - objectWidth / 2, y = y - objectHeight / 2)
                    .size(objectWidth, objectHeight)
                    .then(gesture)
                    .clickable(enabled = interactive) { onSelect("obj:${obj.id}") }
                    .border(
                        if (selected == "obj:${obj.id}") 3.dp else 0.dp,
                        Color.White,
                        RoundedCornerShape(5.dp)
                    )
            ) {
                AssetThumbnail(project, obj.assetId, store, Modifier.fillMaxSize(), "OBJ")
            }
        }

        val oppId = song.opponentId ?: stage.previewOpponentId ?: "dad"
        val gfId = song.girlfriendId ?: stage.previewGirlfriendId ?: "gf"
        val bfId = song.playerId ?: stage.previewBoyfriendId ?: "bf"

        LevelCharacterV5(project, store, oppId, "opp", stage.opponent, selected, maxWidth, maxHeight, density, interactive, onSelect, onMoveCharacter)
        if (!stage.hideGirlfriend) {
            LevelCharacterV5(project, store, gfId, "gf", stage.girlfriend, selected, maxWidth, maxHeight, density, interactive, onSelect, onMoveCharacter)
        }
        LevelCharacterV5(project, store, bfId, "bf", stage.boyfriend, selected, maxWidth, maxHeight, density, interactive, onSelect, onMoveCharacter)
    }
}

@Composable
private fun BoxScope.LevelCharacterV5(
    project: Project,
    store: ProjectStore,
    charId: String,
    role: String,
    slot: StageCharacterSlot,
    selected: String?,
    width: Dp,
    height: Dp,
    density: Float,
    interactive: Boolean,
    onSelect: (String) -> Unit,
    onMove: (String, Double, Double) -> Unit
) {
    val markerSize = 80.dp
    val x = width * (slot.x / 1280.0).toFloat()
    val y = height * (slot.y / 720.0).toFloat()
    val gesture = if (interactive) {
        Modifier.pointerInput(role, width.value, height.value) {
            detectDragGestures(onDragStart = { onSelect(role) }) { _, drag ->
                val dxDp = drag.x / density
                val dyDp = drag.y / density
                onMove(
                    role,
                    dxDp / width.value.coerceAtLeast(1f) * 1280.0,
                    dyDp / height.value.coerceAtLeast(1f) * 720.0
                )
            }
        }
    } else Modifier

    Box(
        Modifier
            .offset(x = x - markerSize / 2, y = y - markerSize / 2)
            .size(markerSize)
            .background(MakerPalette.PanelDark.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .then(gesture)
            .clickable(enabled = interactive) { onSelect(role) }
            .border(
                if (selected == role) 3.dp else 1.dp,
                if (selected == role) Color.White else MakerPalette.Header,
                RoundedCornerShape(7.dp)
            )
    ) {
        AssetThumbnail(
            project,
            characterPreviewAssetId(project, charId),
            store,
            Modifier.fillMaxSize(),
            charId.take(3).uppercase()
        )
    }
}

@Composable
private fun LevelStageToolsV5(
    stage: StageDefinition,
    selected: String?,
    onSelected: (String?) -> Unit,
    onStage: (StageDefinition) -> Unit,
    onSave: () -> Unit,
    onAddImage: () -> Unit,
    onAddBackground: () -> Unit,
    onPackages: () -> Unit
) {
    val selectedObject = selected
        ?.removePrefix("obj:")
        ?.let { id -> stage.objects.firstOrNull { it.id == id } }

    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MakerButton("＋ OBJETO", onAddImage, Modifier.weight(1f), MakerButtonTone.ADD)
            MakerButton("▣ FUNDO", onAddBackground, Modifier.weight(1f), MakerButtonTone.ACCENT)
            MakerButton("PACOTES", onPackages, Modifier.weight(1f), MakerButtonTone.DARK)
            MakerButton("SALVAR", onSave, Modifier.weight(1f))
        }

        MakerPanel(
            if (selectedObject != null) "Objeto • ${selectedObject.name}" else "Stage",
            Modifier.fillMaxWidth().weight(1f)
        ) {
            if (selectedObject == null) {
                Text(
                    "Toque em um objeto ou personagem na preview. Arraste diretamente para mover.",
                    color = MakerPalette.Muted
                )
                MakerLabelValue("Stage", stage.displayName)
                MakerLabelValue("Objetos", stage.objects.size.toString())
                MakerValueStepper(
                    "Zoom padrão",
                    "${"%.2f".format(stage.defaultZoom)}",
                    { onStage(stage.copy(defaultZoom = max(0.1, stage.defaultZoom - 0.05))) },
                    { onStage(stage.copy(defaultZoom = stage.defaultZoom + 0.05)) }
                )
            } else {
                MakerLabelValue("Tipo", selectedObject.editorKind.name)
                MakerLabelValue("Posição", "X ${selectedObject.x.toInt()}  Y ${selectedObject.y.toInt()}")
                MakerValueStepper(
                    "Escala",
                    "${"%.2f".format(selectedObject.scaleX)}x",
                    {
                        val updated = selectedObject.copy(
                            scaleX = max(0.1, selectedObject.scaleX - 0.1),
                            scaleY = max(0.1, selectedObject.scaleY - 0.1)
                        )
                        onStage(stage.copy(objects = stage.objects.map { if (it.id == updated.id) updated else it }))
                    },
                    {
                        val updated = selectedObject.copy(
                            scaleX = selectedObject.scaleX + 0.1,
                            scaleY = selectedObject.scaleY + 0.1
                        )
                        onStage(stage.copy(objects = stage.objects.map { if (it.id == updated.id) updated else it }))
                    }
                )
                MakerValueStepper(
                    "Layer",
                    selectedObject.layer.toString(),
                    {
                        val updated = selectedObject.copy(layer = selectedObject.layer - 1)
                        onStage(stage.copy(objects = stage.objects.map { if (it.id == updated.id) updated else it }))
                    },
                    {
                        val updated = selectedObject.copy(layer = selectedObject.layer + 1)
                        onStage(stage.copy(objects = stage.objects.map { if (it.id == updated.id) updated else it }))
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MakerButton(
                        if (selectedObject.flipX) "FLIP X: SIM" else "FLIP X: NÃO",
                        {
                            val updated = selectedObject.copy(flipX = !selectedObject.flipX)
                            onStage(stage.copy(objects = stage.objects.map { if (it.id == updated.id) updated else it }))
                        },
                        Modifier.weight(1f),
                        MakerButtonTone.DARK
                    )
                    MakerButton(
                        "🗑",
                        {
                            onStage(stage.copy(objects = stage.objects - selectedObject))
                            onSelected(null)
                        },
                        Modifier.weight(0.45f),
                        MakerButtonTone.DANGER
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelChartV5(
    project: Project,
    song: Song,
    store: ProjectStore,
    onProjectChange: (Project) -> Unit
) {
    val difficulty = song.difficulties.firstOrNull() ?: return
    val sourceChart = difficulty.chart
    val bpm = song.bpm.coerceAtLeast(1.0)
    val beatMs = 60_000.0 / bpm
    val snapValues = listOf(4, 8, 12, 16, 24, 32, 48, 64)

    var notes by remember(song.id, difficulty.id) { mutableStateOf(sourceChart.notes) }
    var snapIndex by remember(song.id) { mutableIntStateOf(3) }
    var zoom by remember(song.id) { mutableDoubleStateOf(1.0) }
    var startMs by remember(song.id) { mutableDoubleStateOf(0.0) }
    var currentMs by remember(song.id) { mutableDoubleStateOf(0.0) }
    var isPlaying by remember(song.id) { mutableStateOf(false) }
    var follow by remember(song.id) { mutableStateOf(true) }
    var sustainKey by remember(song.id) { mutableStateOf<LevelNoteKeyV5?>(null) }

    val snap = snapValues[snapIndex]
    val snapMs = beatMs * 4.0 / snap

    LaunchedEffect(sourceChart.notes, sustainKey) {
        if (sustainKey == null && sourceChart.notes != notes) notes = sourceChart.notes
    }

    fun persistNotes(updatedNotes: List<Note>) {
        notes = updatedNotes.sortedBy { it.timeMs }
        val updatedChart = sourceChart.copy(notes = notes)
        val difficulties = song.difficulties.mapIndexed { index, d ->
            if (index == 0) d.copy(chart = updatedChart) else d
        }
        val updatedSong = song.copy(difficulties = difficulties)
        onProjectChange(project.copy(songs = project.songs.map { if (it.id == song.id) updatedSong else it }))
    }

    fun previewSustain(key: LevelNoteKeyV5, sustainMs: Double) {
        notes = notes.map { note ->
            if (note.timeMs == key.timeMs && note.lane == key.lane && note.owner == key.owner) {
                note.copy(sustainMs = max(0.0, round(sustainMs / snapMs) * snapMs))
            } else note
        }
    }

    val audioAsset = project.assets.firstOrNull { it.id == song.instrumentalAssetId }
    val audioPath = audioAsset?.let {
        store.assetFile(project.id, it).takeIf { file -> file.isFile }?.absolutePath
    }
    val player = remember(audioPath) {
        audioPath?.let { path ->
            runCatching { MediaPlayer().apply { setDataSource(path); prepare() } }.getOrNull()
        }
    }
    DisposableEffect(player) { onDispose { runCatching { player?.release() } } }

    LaunchedEffect(isPlaying, player, follow, zoom) {
        while (isPlaying && player != null) {
            currentMs = player.currentPosition.toDouble()
            if (!player.isPlaying) {
                isPlaying = false
                break
            }
            if (follow) {
                val visibleBeats = 9.0 / zoom
                startMs = max(0.0, currentMs - visibleBeats * beatMs * 0.32)
            }
            delay(24)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MakerSquareButton("◀", { snapIndex = (snapIndex - 1).coerceAtLeast(0) }, size = 42.dp)
            MakerButton("SNAP 1/$snap", {}, Modifier.weight(1f), MakerButtonTone.DARK)
            MakerSquareButton("▶", { snapIndex = (snapIndex + 1).coerceAtMost(snapValues.lastIndex) }, size = 42.dp)
            MakerButton("ZOOM ${"%.1f".format(zoom)}x", {}, Modifier.weight(0.8f), MakerButtonTone.DARK)
        }

        GestureChartGridV5(
            notes = notes,
            bpm = bpm,
            snap = snap,
            zoom = zoom,
            startMs = startMs,
            currentMs = currentMs,
            sustainActive = sustainKey,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onPanMs = { delta ->
                if (sustainKey == null && (!isPlaying || !follow)) startMs = max(0.0, startMs + delta)
            },
            onZoom = { factor ->
                if (sustainKey == null) zoom = (zoom * factor).coerceIn(0.45, 5.0)
            },
            onTapCell = { owner, lane, time ->
                val existing = notes.firstOrNull {
                    it.owner == owner && it.lane == lane && abs(it.timeMs - time) <= snapMs * 0.38
                }
                if (existing != null) {
                    persistNotes(notes - existing)
                } else {
                    persistNotes(notes + Note(timeMs = time, lane = lane, owner = owner))
                }
            },
            onSustainStart = { note ->
                sustainKey = LevelNoteKeyV5(note.timeMs, note.lane, note.owner)
            },
            onSustainPreview = { key, value -> previewSustain(key, value) },
            onSustainEnd = {
                if (sustainKey != null) persistNotes(notes)
                sustainKey = null
            }
        )

        Text(
            "1 dedo: timeline • 2 dedos: zoom • toque: criar/remover • segure + arraste: sustain",
            color = MakerPalette.Muted,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MakerSquareButton(
                "◀◀",
                {
                    val target = max(0, (player?.currentPosition ?: currentMs.toInt()) - 5000)
                    player?.seekTo(target)
                    currentMs = target.toDouble()
                    startMs = max(0.0, target - beatMs * 2)
                },
                modifier = Modifier.weight(0.22f),
                size = 54.dp
            )
            MakerButton(
                if (isPlaying) "Ⅱ" else "▶",
                {
                    if (player != null) {
                        if (isPlaying) player.pause() else player.start()
                        isPlaying = !isPlaying
                    }
                },
                Modifier.weight(0.42f),
                enabled = player != null
            )
            MakerButton(
                if (follow) "SEGUIR" else "LIVRE",
                { follow = !follow },
                Modifier.weight(0.28f),
                MakerButtonTone.DARK
            )
            MakerSquareButton(
                "▶▶",
                {
                    val duration = player?.duration ?: Int.MAX_VALUE
                    val target = min(duration, (player?.currentPosition ?: currentMs.toInt()) + 5000)
                    player?.seekTo(target)
                    currentMs = target.toDouble()
                    startMs = max(0.0, target - beatMs * 2)
                },
                modifier = Modifier.weight(0.22f),
                size = 54.dp
            )
        }
    }
}

@Composable
private fun GestureChartGridV5(
    notes: List<Note>,
    bpm: Double,
    snap: Int,
    zoom: Double,
    startMs: Double,
    currentMs: Double,
    sustainActive: LevelNoteKeyV5?,
    modifier: Modifier,
    onPanMs: (Double) -> Unit,
    onZoom: (Double) -> Unit,
    onTapCell: (NoteOwner, Int, Double) -> Unit,
    onSustainStart: (Note) -> Unit,
    onSustainPreview: (LevelNoteKeyV5, Double) -> Unit,
    onSustainEnd: () -> Unit
) {
    val beatMs = 60_000.0 / bpm.coerceAtLeast(1.0)
    val visibleBeats = 9.0 / zoom
    val haptic = LocalHapticFeedback.current

    val currentNotes by rememberUpdatedState(notes)
    val currentPan by rememberUpdatedState(onPanMs)
    val currentZoom by rememberUpdatedState(onZoom)
    val currentTap by rememberUpdatedState(onTapCell)
    val currentSustainStart by rememberUpdatedState(onSustainStart)
    val currentSustainPreview by rememberUpdatedState(onSustainPreview)
    val currentSustainEnd by rememberUpdatedState(onSustainEnd)

    Box(
        modifier
            .background(Color(0xFF101632), RoundedCornerShape(5.dp))
            .border(2.dp, MakerPalette.GridStrong, RoundedCornerShape(5.dp))
    ) {
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
                        val snapped = max(0.0, round(rawMs / snapMs) * snapMs)
                        currentTap(owner, lane, snapped)
                    }
                }
                .pointerInput(startMs, zoom) {
                    detectTransformGestures(panZoomLock = true) { _, pan, scale, _ ->
                        val pxPerBeat = size.height / visibleBeats.toFloat()
                        val msPerPx = beatMs / pxPerBeat
                        if (abs(scale - 1f) > 0.002f) currentZoom(scale.toDouble())
                        if (abs(pan.y) > 0.1f) currentPan(-pan.y * msPerPx)
                    }
                }
                .pointerInput(startMs, zoom, snap) {
                    var targetKey: LevelNoteKeyV5? = null
                    var baseSustain = 0.0
                    var accumulatedPx = 0f

                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val laneWidth = size.width / 8f
                            val globalLane = floor(pos.x / laneWidth).toInt().coerceIn(0, 7)
                            val owner = if (globalLane < 4) NoteOwner.OPPONENT else NoteOwner.PLAYER
                            val lane = globalLane % 4
                            val pxPerBeat = size.height / visibleBeats.toFloat()
                            val timeMs = startMs + (pos.y / pxPerBeat) * beatMs
                            val hitMs = beatMs * 0.32
                            val target = currentNotes
                                .filter { it.owner == owner && it.lane == lane }
                                .minByOrNull { abs(it.timeMs - timeMs) }
                                ?.takeIf { abs(it.timeMs - timeMs) <= hitMs }

                            if (target != null) {
                                targetKey = LevelNoteKeyV5(target.timeMs, target.lane, target.owner)
                                baseSustain = target.sustainMs
                                accumulatedPx = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentSustainStart(target)
                            }
                        },
                        onDragEnd = {
                            if (targetKey != null) currentSustainEnd()
                            targetKey = null
                        },
                        onDragCancel = {
                            if (targetKey != null) currentSustainEnd()
                            targetKey = null
                        }
                    ) { _, drag ->
                        val key = targetKey ?: return@detectDragGesturesAfterLongPress
                        accumulatedPx += drag.y
                        val pxPerBeat = size.height / visibleBeats.toFloat()
                        val msPerPx = beatMs / pxPerBeat
                        currentSustainPreview(key, baseSustain + accumulatedPx * msPerPx)
                    }
                }
        ) {
            val laneWidth = size.width / 8f
            val pxPerBeat = size.height / visibleBeats.toFloat()
            val endMs = startMs + visibleBeats * beatMs
            val snapMs = beatMs * 4.0 / snap

            for (lane in 0..8) {
                val x = lane * laneWidth
                drawLine(
                    color = if (lane == 4) MakerPalette.GridStrong else MakerPalette.Grid,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (lane == 4) 4f else 1.5f
                )
            }

            val firstStep = floor(startMs / snapMs).toInt()
            val lastStep = floor(endMs / snapMs).toInt() + 1
            for (step in firstStep..lastStep) {
                val t = step * snapMs
                val y = ((t - startMs) / beatMs * pxPerBeat).toFloat()
                val beatBoundary = abs(t / beatMs - round(t / beatMs)) < 0.001
                drawLine(
                    color = if (beatBoundary) MakerPalette.GridStrong else MakerPalette.Grid.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (beatBoundary) 2.5f else 1f
                )
            }

            val playheadY = ((currentMs - startMs) / beatMs * pxPerBeat).toFloat()
            if (playheadY in 0f..size.height) {
                drawLine(Color.White, Offset(0f, playheadY), Offset(size.width, playheadY), strokeWidth = 2f)
            }

            notes
                .filter { it.timeMs + it.sustainMs >= startMs && it.timeMs <= endMs }
                .forEach { note ->
                    val globalLane = (if (note.owner == NoteOwner.OPPONENT) 0 else 4) + note.lane.coerceIn(0, 3)
                    val x = globalLane * laneWidth
                    val y = ((note.timeMs - startMs) / beatMs * pxPerBeat).toFloat()
                    val color = levelLaneColorV5(note.lane)

                    if (note.sustainMs > 0) {
                        val sustainHeight = (note.sustainMs / beatMs * pxPerBeat).toFloat()
                        drawRoundRect(
                            color = color.copy(alpha = 0.78f),
                            topLeft = Offset(x + laneWidth * 0.42f, y + laneWidth * 0.28f),
                            size = Size(laneWidth * 0.16f, max(4f, sustainHeight)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                        )
                    }

                    val headSize = min(laneWidth * 0.62f, 48f)
                    val left = x + (laneWidth - headSize) / 2f
                    val active = sustainActive?.let {
                        it.timeMs == note.timeMs && it.lane == note.lane && it.owner == note.owner
                    } == true

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, y - headSize / 2f),
                        size = Size(headSize, headSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    if (active) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left - 4f, y - headSize / 2f - 4f),
                            size = Size(headSize + 8f, headSize + 8f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                            style = Stroke(width = 4f)
                        )
                    }
                }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 3.dp)
        ) {
            listOf("←", "↓", "↑", "→", "←", "↓", "↑", "→").forEachIndexed { index, arrow ->
                Text(
                    arrow,
                    color = levelLaneColorV5(index % 4),
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(vertical = 4.dp)
        ) {
            Text("INIMIGO", color = MakerPalette.Muted, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("JOGADOR", color = MakerPalette.Muted, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LevelPackageDialogV5(
    project: Project,
    store: ProjectStore,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onUse: (SpritePackage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pacotes de sprites") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ícone • Item • Cenário • Personagem")
                if (project.spritePackages.isEmpty()) {
                    Text("Nenhum pacote criado ainda.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(project.spritePackages, key = { it.id }) { pack ->
                            Column(
                                Modifier
                                    .background(MakerPalette.PanelAlt, RoundedCornerShape(7.dp))
                                    .clickable { onUse(pack) }
                                    .padding(7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AssetThumbnail(
                                    project,
                                    pack.imageAssetId ?: pack.iconAssetId,
                                    store,
                                    Modifier.size(72.dp),
                                    pack.kind.name.take(2)
                                )
                                Text(pack.displayName, color = MakerPalette.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text(pack.kind.name, color = MakerPalette.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("＋ Criar pacote") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun CreatePackageDialogV5(
    onDismiss: () -> Unit,
    onCreate: (String, EditorObjectKind) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(EditorObjectKind.ITEM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo pacote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                Text("Tipo")
                MakerTabRow(
                    labels = listOf("Ícone", "Item", "Cenário", "Personagem"),
                    selected = kind.ordinal,
                    onSelect = { index: Int -> kind = EditorObjectKind.entries[index] }
                )
                Text("O Character Editor continua sendo o lugar de editar animações. Aqui o tipo serve para organizar e reutilizar o sprite no nível.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, kind) }, enabled = name.isNotBlank()) {
                Text("Escolher imagem")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun levelLaneColorV5(lane: Int): Color = when (lane) {
    0 -> Color(0xFFD64DCE)
    1 -> Color(0xFF4FD8E6)
    2 -> Color(0xFF62E369)
    else -> Color(0xFFE95757)
}
