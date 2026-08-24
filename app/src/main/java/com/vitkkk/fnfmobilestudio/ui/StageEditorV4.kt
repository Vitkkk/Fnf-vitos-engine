package com.vitkkk.fnfmobilestudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

private enum class StageToolV4 { OBJECTS, CHARACTERS, CAMERA }

@Composable
internal fun StageEditorScreenV4(
    project: Project,
    stageId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val persistedStage = project.stages.firstOrNull { it.id == stageId } ?: return
    var draft by remember(stageId) { mutableStateOf(persistedStage) }
    var tool by remember { mutableStateOf(StageToolV4.OBJECTS) }
    var selected by remember { mutableStateOf<String?>(null) }
    var propertyTab by remember { mutableIntStateOf(0) }
    var showObjectManager by remember { mutableStateOf(false) }
    var pickerRole by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist(updated: StageDefinition = draft, updatedProject: Project = project) {
        draft = updated
        onProjectChange(updatedProject.copy(stages = updatedProject.stages.map { if (it.id == stageId) updated else it }))
    }

    val addObjectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "stage-${stageId}")
            val obj = StageObject(
                id = "obj-${UUID.randomUUID().toString().take(8)}",
                name = asset.relativePath.substringAfterLast('/').substringBeforeLast('.'),
                assetId = asset.id,
                x = 520.0,
                y = 260.0,
                layer = (draft.objects.maxOfOrNull { it.layer } ?: -1) + 1
            )
            val updated = draft.copy(objects = draft.objects + obj)
            selected = "obj:${obj.id}"
            persist(updated, project.copy(assets = project.assets + asset))
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "stage-${stageId}")
            val updated = draft.copy(previewAssetId = asset.id)
            persist(updated, project.copy(assets = project.assets + asset))
        }
    }

    val selectedObject = selected?.removePrefix("obj:")?.let { id -> draft.objects.firstOrNull { it.id == id } }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Stage • ${draft.displayName}", onBack = onBack) {
                MakerSquareButton("⌂", onHome, size = 54.dp)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StageViewportV4(
                    project = project,
                    stage = draft,
                    store = store,
                    selected = selected,
                    modifier = Modifier.weight(1f).aspectRatio(1.25f),
                    onSelect = { selected = it },
                    onMoveObject = { id, dx, dy ->
                        draft = draft.copy(objects = draft.objects.map {
                            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
                        })
                    },
                    onMoveCharacter = { role, dx, dy ->
                        draft = when (role) {
                            "bf" -> draft.copy(boyfriend = draft.boyfriend.copy(x = draft.boyfriend.x + dx, y = draft.boyfriend.y + dy))
                            "opp" -> draft.copy(opponent = draft.opponent.copy(x = draft.opponent.x + dx, y = draft.opponent.y + dy))
                            else -> draft.copy(girlfriend = draft.girlfriend.copy(x = draft.girlfriend.x + dx, y = draft.girlfriend.y + dy))
                        }
                    }
                )

                Column(
                    Modifier.width(72.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MakerSquareButton("▧", { tool = StageToolV4.OBJECTS; showObjectManager = true }, Modifier.fillMaxWidth(), size = 62.dp, tone = if (tool == StageToolV4.OBJECTS) MakerButtonTone.ACCENT else MakerButtonTone.DARK)
                    MakerSquareButton("♟", { tool = StageToolV4.CHARACTERS }, Modifier.fillMaxWidth(), size = 62.dp, tone = if (tool == StageToolV4.CHARACTERS) MakerButtonTone.ACCENT else MakerButtonTone.DARK)
                    MakerSquareButton("▣", { backgroundLauncher.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth(), size = 62.dp, tone = MakerButtonTone.DARK)
                    MakerSquareButton("◉", { tool = StageToolV4.CAMERA }, Modifier.fillMaxWidth(), size = 62.dp, tone = if (tool == StageToolV4.CAMERA) MakerButtonTone.ACCENT else MakerButtonTone.DARK)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MakerButton("🗑", { showDeleteConfirm = selectedObject != null }, Modifier.weight(1f), MakerButtonTone.DANGER, enabled = selectedObject != null)
                MakerButton("＋", { addObjectLauncher.launch(arrayOf("image/*")) }, Modifier.weight(1f), MakerButtonTone.ADD)
                MakerButton("▣▣", {
                    selectedObject?.let { obj ->
                        val duplicate = obj.copy(id = "obj-${UUID.randomUUID().toString().take(8)}", name = obj.name + " copy", x = obj.x + 24, y = obj.y + 24, layer = obj.layer + 1)
                        draft = draft.copy(objects = draft.objects + duplicate)
                        selected = "obj:${duplicate.id}"
                    }
                }, Modifier.weight(1f), MakerButtonTone.ACCENT, enabled = selectedObject != null)
                MakerButton("SALVAR", { persist(draft) }, Modifier.weight(1.45f), MakerButtonTone.NORMAL)
            }

            when (tool) {
                StageToolV4.OBJECTS -> StageObjectPropertiesV4(draft, selectedObject, propertyTab, { propertyTab = it }) { updated ->
                    draft = draft.copy(objects = draft.objects.map { if (it.id == updated.id) updated else it })
                }
                StageToolV4.CHARACTERS -> StageCharacterPropertiesV4(project, draft, propertyTab, { propertyTab = it }, { role -> pickerRole = role }) { draft = it }
                StageToolV4.CAMERA -> StageCameraPropertiesV4(draft) { draft = it }
            }

            Spacer(Modifier.weight(1f))
            MakerBottomHome(onHome)
        }

        if (showObjectManager) {
            StageObjectManagerOverlayV4(
                project = project,
                stage = draft,
                store = store,
                onClose = { showObjectManager = false },
                onSelect = { id -> selected = "obj:$id"; tool = StageToolV4.OBJECTS; showObjectManager = false },
                onAdd = { showObjectManager = false; addObjectLauncher.launch(arrayOf("image/*")) }
            )
        }
    }

    pickerRole?.let { role ->
        val current = when (role) {
            "bf" -> draft.previewBoyfriendId ?: "bf"
            "opp" -> draft.previewOpponentId ?: "dad"
            else -> draft.previewGirlfriendId ?: "gf"
        }
        CharacterPickerDialog(project, store, current, { pickerRole = null }) { picked ->
            draft = when (role) {
                "bf" -> draft.copy(previewBoyfriendId = picked)
                "opp" -> draft.copy(previewOpponentId = picked)
                else -> draft.copy(previewGirlfriendId = picked)
            }
            pickerRole = null
        }
    }

    if (showDeleteConfirm && selectedObject != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir objeto?") },
            text = { Text(selectedObject.name) },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(objects = draft.objects - selectedObject)
                    selected = null
                    showDeleteConfirm = false
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StageViewportV4(
    project: Project,
    stage: StageDefinition,
    store: ProjectStore,
    selected: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onMoveObject: (String, Double, Double) -> Unit,
    onMoveCharacter: (String, Double, Double) -> Unit
) {
    BoxWithConstraints(
        modifier
            .background(Color.Black, RoundedCornerShape(4.dp))
            .border(2.dp, MakerPalette.PanelAlt, RoundedCornerShape(4.dp))
    ) {
        val density = LocalDensity.current.density
        val widthDp = maxWidth.value.coerceAtLeast(1f)
        val heightDp = maxHeight.value.coerceAtLeast(1f)

        if (stage.previewAssetId != null) {
            AssetThumbnail(project, stage.previewAssetId, store, Modifier.fillMaxSize(), "BG")
        }

        stage.objects.sortedBy { it.layer }.forEach { obj ->
            val x = maxWidth * (obj.x / 1280.0).toFloat()
            val y = maxHeight * (obj.y / 720.0).toFloat()
            val size = (76.dp * obj.scaleX.toFloat().coerceIn(0.25f, 4f))
            Box(
                Modifier
                    .offset(x = x - size / 2, y = y - size / 2)
                    .size(size)
                    .border(if (selected == "obj:${obj.id}") 3.dp else 0.dp, if (selected == "obj:${obj.id}") Color.White else Color.Transparent, RoundedCornerShape(5.dp))
                    .pointerInput(obj.id, widthDp, heightDp) {
                        detectDragGestures(
                            onDragStart = { onSelect("obj:${obj.id}") }
                        ) { change, drag ->
                            change.consume()
                            val dxDp = drag.x / density
                            val dyDp = drag.y / density
                            onMoveObject(obj.id, dxDp / widthDp * 1280.0, dyDp / heightDp * 720.0)
                        }
                    }
                    .clickable { onSelect("obj:${obj.id}") }
            ) {
                AssetThumbnail(project, obj.assetId, store, Modifier.fillMaxSize(), "OBJ")
            }
        }

        StageCharacterMarkerV4(project, store, stage.previewOpponentId ?: "dad", "opp", stage.opponent, selected, maxWidth, maxHeight, density, onSelect, onMoveCharacter)
        if (!stage.hideGirlfriend) StageCharacterMarkerV4(project, store, stage.previewGirlfriendId ?: "gf", "gf", stage.girlfriend, selected, maxWidth, maxHeight, density, onSelect, onMoveCharacter)
        StageCharacterMarkerV4(project, store, stage.previewBoyfriendId ?: "bf", "bf", stage.boyfriend, selected, maxWidth, maxHeight, density, onSelect, onMoveCharacter)
    }
}

@Composable
private fun BoxScope.StageCharacterMarkerV4(
    project: Project,
    store: ProjectStore,
    charId: String,
    role: String,
    slot: StageCharacterSlot,
    selected: String?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    density: Float,
    onSelect: (String) -> Unit,
    onMove: (String, Double, Double) -> Unit
) {
    val markerSize = 82.dp
    val x = width * (slot.x / 1280.0).toFloat()
    val y = height * (slot.y / 720.0).toFloat()
    val widthDp = width.value.coerceAtLeast(1f)
    val heightDp = height.value.coerceAtLeast(1f)
    Box(
        Modifier
            .offset(x = x - markerSize / 2, y = y - markerSize / 2)
            .size(markerSize)
            .background(MakerPalette.PanelDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .border(if (selected == role) 3.dp else 1.dp, if (selected == role) Color.White else MakerPalette.Header, RoundedCornerShape(8.dp))
            .pointerInput(role, widthDp, heightDp) {
                detectDragGestures(onDragStart = { onSelect(role) }) { change, drag ->
                    change.consume()
                    val dxDp = drag.x / density
                    val dyDp = drag.y / density
                    onMove(role, dxDp / widthDp * 1280.0, dyDp / heightDp * 720.0)
                }
            }
            .clickable { onSelect(role) }
    ) {
        AssetThumbnail(project, characterPreviewAssetId(project, charId), store, Modifier.fillMaxSize(), role.uppercase())
    }
}

@Composable
private fun StageObjectPropertiesV4(
    stage: StageDefinition,
    obj: StageObject?,
    tab: Int,
    onTab: (Int) -> Unit,
    onObject: (StageObject) -> Unit
) {
    MakerPanel("Objeto", Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        MakerTabRow(listOf("Posição", "Aparência", "Lógica"), tab, onTab)
        if (obj == null) {
            Text("Selecione um sprite no cenário ou abra o gerenciador de objetos.", color = MakerPalette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(18.dp))
            return@MakerPanel
        }
        when (tab) {
            0 -> {
                MakerLabelValue("Objeto", "${obj.name} • layer ${obj.layer}")
                MakerAxisRowV4("POSIÇÃO", obj.x, obj.y) { x, y -> onObject(obj.copy(x = x, y = y)) }
                MakerValueStepper("Escala", "${"%.2f".format(obj.scaleX)}x", { onObject(obj.copy(scaleX = max(0.1, obj.scaleX - 0.05), scaleY = max(0.1, obj.scaleY - 0.05))) }, { onObject(obj.copy(scaleX = obj.scaleX + 0.05, scaleY = obj.scaleY + 0.05)) })
            }
            1 -> {
                MakerValueStepper("Alpha", "${"%.2f".format(obj.alpha)}", { onObject(obj.copy(alpha = max(0.0, obj.alpha - 0.1))) }, { onObject(obj.copy(alpha = (obj.alpha + 0.1).coerceAtMost(1.0))) })
                MakerButton(if (obj.flipX) "FLIP X: SIM" else "FLIP X: NÃO", { onObject(obj.copy(flipX = !obj.flipX)) }, Modifier.fillMaxWidth(), MakerButtonTone.DARK)
                MakerValueStepper("Layer", obj.layer.toString(), { onObject(obj.copy(layer = obj.layer - 1)) }, { onObject(obj.copy(layer = obj.layer + 1)) })
            }
            else -> {
                MakerLabelValue("Scroll X", "${"%.2f".format(obj.scrollX)}")
                MakerLabelValue("Scroll Y", "${"%.2f".format(obj.scrollY)}")
                Text("Scroll factor e lógica avançada serão expandidos sem exigir scripts.", color = MakerPalette.Muted)
            }
        }
    }
}

@Composable
private fun StageCharacterPropertiesV4(
    project: Project,
    stage: StageDefinition,
    tab: Int,
    onTab: (Int) -> Unit,
    onPick: (String) -> Unit,
    onStage: (StageDefinition) -> Unit
) {
    MakerPanel("Personagens", Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        MakerTabRow(listOf("Personagens", "Posições", "Visibilidade"), tab, onTab)
        when (tab) {
            0 -> {
                MakerButton("BF • ${characterName(project, stage.previewBoyfriendId ?: "bf")}", { onPick("bf") }, Modifier.fillMaxWidth())
                MakerButton("INIMIGO • ${characterName(project, stage.previewOpponentId ?: "dad")}", { onPick("opp") }, Modifier.fillMaxWidth())
                MakerButton("GF • ${characterName(project, stage.previewGirlfriendId ?: "gf")}", { onPick("gf") }, Modifier.fillMaxWidth())
            }
            1 -> {
                MakerAxisRowV4("BF", stage.boyfriend.x, stage.boyfriend.y) { x, y -> onStage(stage.copy(boyfriend = stage.boyfriend.copy(x = x, y = y))) }
                MakerAxisRowV4("INIMIGO", stage.opponent.x, stage.opponent.y) { x, y -> onStage(stage.copy(opponent = stage.opponent.copy(x = x, y = y))) }
                MakerAxisRowV4("GF", stage.girlfriend.x, stage.girlfriend.y) { x, y -> onStage(stage.copy(girlfriend = stage.girlfriend.copy(x = x, y = y))) }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MOSTRAR GIRLFRIEND", color = MakerPalette.White, fontWeight = FontWeight.Bold)
                    Switch(checked = !stage.hideGirlfriend, onCheckedChange = { onStage(stage.copy(hideGirlfriend = !it)) })
                }
            }
        }
    }
}

@Composable
private fun StageCameraPropertiesV4(stage: StageDefinition, onStage: (StageDefinition) -> Unit) {
    var cameraTarget by remember { mutableIntStateOf(0) }
    val slot = when (cameraTarget) { 0 -> stage.opponent; 1 -> stage.boyfriend; else -> stage.girlfriend }
    MakerPanel("Câmera", Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        MakerTabRow(listOf("Inimigo", "Jogador", "GF"), cameraTarget) { cameraTarget = it }
        MakerAxisRowV4("OFFSET DA CÂMERA", slot.cameraOffsetX, slot.cameraOffsetY) { x, y ->
            onStage(when (cameraTarget) {
                0 -> stage.copy(opponent = stage.opponent.copy(cameraOffsetX = x, cameraOffsetY = y))
                1 -> stage.copy(boyfriend = stage.boyfriend.copy(cameraOffsetX = x, cameraOffsetY = y))
                else -> stage.copy(girlfriend = stage.girlfriend.copy(cameraOffsetX = x, cameraOffsetY = y))
            })
        }
        MakerValueStepper("Zoom padrão", "${"%.2f".format(stage.defaultZoom)}", { onStage(stage.copy(defaultZoom = max(0.1, stage.defaultZoom - 0.05))) }, { onStage(stage.copy(defaultZoom = stage.defaultZoom + 0.05)) })
        Text("Isto ajusta os offsets normais de câmera do Stage. Câmeras temporárias por evento ficam para o editor de eventos futuro.", color = MakerPalette.Muted)
    }
}

@Composable
private fun MakerAxisRowV4(label: String, x: Double, y: Double, onChange: (Double, Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MakerSectionHeader("$label • X ${x.toInt()}  Y ${y.toInt()}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MakerSquareButton("◀", { onChange(x - 10, y) }, Modifier.weight(1f), size = 46.dp)
            MakerSquareButton("▶", { onChange(x + 10, y) }, Modifier.weight(1f), size = 46.dp)
            MakerSquareButton("▲", { onChange(x, y - 10) }, Modifier.weight(1f), size = 46.dp)
            MakerSquareButton("▼", { onChange(x, y + 10) }, Modifier.weight(1f), size = 46.dp)
        }
    }
}

@Composable
private fun StageObjectManagerOverlayV4(
    project: Project,
    stage: StageDefinition,
    store: ProjectStore,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE622284F))
            .padding(16.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MakerPalette.Panel, RoundedCornerShape(10.dp))
                .border(2.dp, MakerPalette.Header, RoundedCornerShape(10.dp))
        ) {
            MakerSectionHeader("Gerenciador de objetos do Stage")
            if (stage.objects.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhum sprite no Stage", color = MakerPalette.Muted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stage.objects, key = { it.id }) { obj ->
                        Column(
                            Modifier
                                .background(MakerPalette.PanelAlt, RoundedCornerShape(8.dp))
                                .clickable { onSelect(obj.id) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AssetThumbnail(project, obj.assetId, store, Modifier.fillMaxWidth().aspectRatio(1f), "OBJ")
                            Text(obj.name, color = MakerPalette.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MakerButton("＋ ADICIONAR", onAdd, Modifier.weight(1f), MakerButtonTone.ADD)
                MakerButton("FECHAR", onClose, Modifier.weight(1f), MakerButtonTone.NORMAL)
            }
        }
    }
}
