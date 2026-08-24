package com.vitkkk.fnfmobilestudio.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun StageManagerScreenV3(
    project: Project,
    store: ProjectStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onProjectChange: (Project) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StudioHeaderV3("Stages", "Cenários e posicionamento touch", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar Stage") } }
        item { Text("O ID padrão 'stage' continua disponível nas músicas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (project.stages.isEmpty()) item { EmptyCardV3("Nenhum Stage customizado", "Crie um Stage para abrir o editor visual.") }
        items(project.stages, key = { it.id }) { stage ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(stage.id) }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssetThumbnail(project, stage.previewAssetId, store, Modifier.size(78.dp), "ST")
                    Column(Modifier.weight(1f)) {
                        Text(stage.displayName, fontWeight = FontWeight.Bold)
                        Text("ID ${stage.id} • ${stage.objects.size} objetos")
                        Text("Abrir Stage Editor →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showCreate) {
        SimpleIdDialogV3(
            title = "Novo Stage",
            label = "Nome",
            onDismiss = { showCreate = false },
            onCreate = { name, id ->
                onProjectChange(project.copy(stages = project.stages + StageDefinition(id = id, displayName = name)))
                showCreate = false
                onOpen(id)
            }
        )
    }
}

@Composable
internal fun StageEditorScreenV3(
    project: Project,
    stageId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val original = project.stages.firstOrNull { it.id == stageId } ?: return
    var stage by remember(original) { mutableStateOf(original) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(base: Project = project) {
        onProjectChange(base.copy(stages = base.stages.map { if (it.id == stage.id) stage else it }))
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/stages")
            stage = stage.copy(previewAssetId = asset.id)
            save(project.copy(assets = project.assets + asset))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StudioHeaderV3(stage.displayName, "Stage Editor • arraste BF, Opponent e GF", onBack) }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { canvasSize = it }
            ) {
                StageBackground(project, stage.previewAssetId, store)
                CharacterMarker(project, store, stage.previewOpponentId ?: "dad", stage.opponent, canvasSize, "Opponent") { dx, dy ->
                    stage = stage.copy(opponent = moveSlot(stage.opponent, dx, dy, canvasSize))
                }
                if (!stage.hideGirlfriend) {
                    CharacterMarker(project, store, stage.previewGirlfriendId ?: "gf", stage.girlfriend, canvasSize, "GF") { dx, dy ->
                        stage = stage.copy(girlfriend = moveSlot(stage.girlfriend, dx, dy, canvasSize))
                    }
                }
                CharacterMarker(project, store, stage.previewBoyfriendId ?: "bf", stage.boyfriend, canvasSize, "BF") { dx, dy ->
                    stage = stage.copy(boyfriend = moveSlot(stage.boyfriend, dx, dy, canvasSize))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { backgroundLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Imagem do Stage") }
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("Salvar posições") }
            }
        }
        item {
            EditorCardV3("Personagens de preview") {
                SelectorField("Boyfriend", stage.previewBoyfriendId ?: "bf", characterName(project, stage.previewBoyfriendId ?: "bf"), { picker = "bf" })
                SelectorField("Opponent", stage.previewOpponentId ?: "dad", characterName(project, stage.previewOpponentId ?: "dad"), { picker = "dad" })
                SelectorField("Girlfriend", stage.previewGirlfriendId ?: "gf", characterName(project, stage.previewGirlfriendId ?: "gf"), { picker = "gf" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar Girlfriend")
                    Switch(checked = !stage.hideGirlfriend, onCheckedChange = { stage = stage.copy(hideGirlfriend = !it) })
                }
                Text(
                    "Esses IDs servem só para preview do editor. A música continua escolhendo os personagens exportados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            EditorCardV3("Coordenadas Psych") {
                Text("Opponent: X ${stage.opponent.x.roundToInt()} • Y ${stage.opponent.y.roundToInt()}")
                Text("GF: X ${stage.girlfriend.x.roundToInt()} • Y ${stage.girlfriend.y.roundToInt()}")
                Text("BF: X ${stage.boyfriend.x.roundToInt()} • Y ${stage.boyfriend.y.roundToInt()}")
                Text("Canvas do editor: 1280 × 720")
            }
        }
    }

    if (picker != null) {
        val selected = when (picker) {
            "bf" -> stage.previewBoyfriendId ?: "bf"
            "dad" -> stage.previewOpponentId ?: "dad"
            else -> stage.previewGirlfriendId ?: "gf"
        }
        CharacterPickerDialog(project, store, selected, { picker = null }) { picked ->
            stage = when (picker) {
                "bf" -> stage.copy(previewBoyfriendId = picked)
                "dad" -> stage.copy(previewOpponentId = picked)
                else -> stage.copy(previewGirlfriendId = picked)
            }
            picker = null
        }
    }
}

@Composable
private fun StageBackground(project: Project, assetId: String?, store: ProjectStore) {
    val asset = project.assets.firstOrNull { it.id == assetId }
    val bitmap = remember(project.id, assetId, asset?.relativePath) {
        asset?.let {
            runCatching { BitmapFactory.decodeFile(store.assetFile(project.id, it).absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CharacterMarker(
    project: Project,
    store: ProjectStore,
    characterId: String,
    slot: StageCharacterSlot,
    canvasSize: IntSize,
    label: String,
    onDrag: (Float, Float) -> Unit
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val x = (slot.x / 1280.0 * canvasSize.width).roundToInt()
    val y = (slot.y / 720.0 * canvasSize.height).roundToInt()
    Column(
        Modifier
            .offset { IntOffset(x - 30, y - 30) }
            .width(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(characterId, canvasSize) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AssetThumbnail(project, characterPreviewAssetId(project, characterId), store, Modifier.size(50.dp), characterId.take(2).uppercase())
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(characterId, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun moveSlot(slot: StageCharacterSlot, dxPx: Float, dyPx: Float, size: IntSize): StageCharacterSlot {
    if (size.width <= 0 || size.height <= 0) return slot
    val dx = dxPx / size.width.toDouble() * 1280.0
    val dy = dyPx / size.height.toDouble() * 720.0
    return slot.copy(
        x = (slot.x + dx).coerceIn(0.0, 1280.0),
        y = (slot.y + dy).coerceIn(0.0, 720.0)
    )
}
