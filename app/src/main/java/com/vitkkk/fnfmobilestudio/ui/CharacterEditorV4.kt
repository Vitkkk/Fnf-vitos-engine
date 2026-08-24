package com.vitkkk.fnfmobilestudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch
import kotlin.math.max

private val recommendedPoses = listOf(
    "idle" to "●",
    "singLEFT" to "←",
    "singDOWN" to "↓",
    "singUP" to "↑",
    "singRIGHT" to "→",
    "singLEFTmiss" to "←!",
    "singDOWNmiss" to "↓!",
    "singUPmiss" to "↑!",
    "singRIGHTmiss" to "→!"
)

@Composable
internal fun CharacterEditorScreenV4(
    project: Project,
    characterId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val persisted = project.characters.firstOrNull { it.id == characterId } ?: return
    var character by remember(characterId) { mutableStateOf(persisted) }
    var selectedPoseId by remember(characterId) { mutableStateOf(character.animations.firstOrNull()?.id ?: "idle") }
    var tab by remember { mutableIntStateOf(0) }
    var showAddPose by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist(updated: CharacterDefinition = character, updatedProject: Project = project) {
        character = updated
        onProjectChange(updatedProject.copy(characters = updatedProject.characters.map { if (it.id == characterId) updated else it }))
    }

    fun ensurePose(id: String): CharacterAnimation {
        val existing = character.animations.firstOrNull { it.id == id }
        if (existing != null) return existing
        val created = CharacterAnimation(id = id)
        character = character.copy(animations = character.animations + created)
        return created
    }

    val selectedPose = character.animations.firstOrNull { it.id == selectedPoseId }
    val selectedPreviewAsset = selectedPose?.frameAssetIds?.firstOrNull() ?: character.imageAssetId

    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "character-${character.id}")
            persist(character.copy(imageAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }
    val iconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "character-${character.id}")
            persist(character.copy(iconAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }
    val framesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch {
            var updatedProject = project
            val importedIds = mutableListOf<String>()
            uris.forEach { uri ->
                val asset = store.importAsset(project.id, uri, AssetKind.FRAME, "character-${character.id}/frames")
                importedIds += asset.id
                updatedProject = updatedProject.copy(assets = updatedProject.assets + asset)
            }
            val pose = ensurePose(selectedPoseId)
            val updatedPose = pose.copy(frameAssetIds = pose.frameAssetIds + importedIds)
            val updatedCharacter = character.copy(animations = character.animations.map { if (it.id == pose.id) updatedPose else it })
            persist(updatedCharacter, updatedProject)
        }
    }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Character • ${character.displayName}", onBack = onBack) {
                MakerSquareButton("⌂", onHome, size = 54.dp)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
                    .padding(horizontal = 14.dp)
                    .background(MakerPalette.BackgroundDeep, RoundedCornerShape(7.dp))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.minDimension / 9f
                    var x = step
                    while (x < size.width) {
                        drawLine(MakerPalette.Grid.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.5f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(MakerPalette.Grid.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 1.5f)
                        y += step
                    }
                    drawLine(MakerPalette.GridStrong, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3f)
                    drawLine(MakerPalette.GridStrong, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f)
                }
                AssetThumbnail(
                    project,
                    selectedPreviewAsset,
                    store,
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(0.78f)
                        .aspectRatio(0.75f),
                    "CHAR"
                )
                Column(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("${selectedPoseId}", color = MakerPalette.White, fontWeight = FontWeight.Black)
                    Text("scale ${"%.2f".format(character.scale)}", color = MakerPalette.Muted)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MakerPanel("Poses / animações", Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val known = linkedMapOf<String, String>()
                        recommendedPoses.forEach { known[it.first] = it.second }
                        character.animations.forEach { if (it.id !in known) known[it.id] = "★" }
                        known.forEach { (id, glyph) ->
                            MakerButton(
                                "$glyph\n$id",
                                {
                                    ensurePose(id)
                                    selectedPoseId = id
                                },
                                Modifier.width(92.dp).height(64.dp),
                                if (selectedPoseId == id) MakerButtonTone.ACCENT else MakerButtonTone.NORMAL
                            )
                        }
                        MakerButton("＋\nNOVA", { showAddPose = true }, Modifier.width(88.dp).height(64.dp), MakerButtonTone.ADD)
                    }
                }

                MakerTabRow(listOf("Pose", "Frames", "Personagem"), tab) { tab = it }

                when (tab) {
                    0 -> MakerPanel("Propriedades da pose", Modifier.fillMaxWidth().weight(1f)) {
                        val pose = character.animations.firstOrNull { it.id == selectedPoseId }
                        if (pose == null) {
                            Text("Selecione ou crie uma pose.", color = MakerPalette.Muted)
                        } else {
                            MakerLabelValue("Pose", pose.id)
                            MakerValueStepper("FPS", "${"%.0f".format(pose.fps)}", {
                                val p = pose.copy(fps = max(1.0, pose.fps - 1))
                                character = character.copy(animations = character.animations.map { if (it.id == pose.id) p else it })
                            }, {
                                val p = pose.copy(fps = pose.fps + 1)
                                character = character.copy(animations = character.animations.map { if (it.id == pose.id) p else it })
                            })
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("LOOP", color = MakerPalette.White, fontWeight = FontWeight.Bold)
                                Switch(checked = pose.loop, onCheckedChange = { loop ->
                                    val p = pose.copy(loop = loop)
                                    character = character.copy(animations = character.animations.map { if (it.id == pose.id) p else it })
                                })
                            }
                            MakerAxisPoseV4(pose.offsetX, pose.offsetY) { x, y ->
                                val p = pose.copy(offsetX = x, offsetY = y)
                                character = character.copy(animations = character.animations.map { if (it.id == pose.id) p else it })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                MakerButton("SALVAR", { persist(character) }, Modifier.weight(1f))
                                MakerButton("EXCLUIR POSE", {
                                    character = character.copy(animations = character.animations.filterNot { it.id == pose.id })
                                    selectedPoseId = character.animations.firstOrNull()?.id ?: "idle"
                                    persist(character)
                                }, Modifier.weight(1f), MakerButtonTone.DANGER)
                            }
                        }
                    }
                    1 -> MakerPanel("Frames", Modifier.fillMaxWidth().weight(1f)) {
                        val pose = character.animations.firstOrNull { it.id == selectedPoseId }
                        MakerButton("IMPORTAR PNGs / IMAGENS", { framesLauncher.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth(), MakerButtonTone.ADD)
                        Text("${pose?.frameAssetIds?.size ?: 0} frames nesta pose", color = MakerPalette.Muted)
                        if (pose != null && pose.frameAssetIds.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pose.frameAssetIds.forEachIndexed { index, assetId ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AssetThumbnail(project, assetId, store, Modifier.size(70.dp), (index + 1).toString())
                                        Text((index + 1).toString(), color = MakerPalette.Muted)
                                    }
                                }
                            }
                            MakerButton("LIMPAR FRAMES", {
                                val p = pose.copy(frameAssetIds = emptyList())
                                character = character.copy(animations = character.animations.map { if (it.id == pose.id) p else it })
                                persist(character)
                            }, Modifier.fillMaxWidth(), MakerButtonTone.DANGER)
                        }
                        Text("Importação direta de GIF com extração automática de frames entra na próxima etapa do pipeline de animação.", color = MakerPalette.Muted)
                    }
                    else -> MakerPanel("Personagem", Modifier.fillMaxWidth().weight(1f)) {
                        MakerButton("IMPORTAR PREVIEW", { previewLauncher.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                        MakerButton("IMPORTAR ÍCONE", { iconLauncher.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                        MakerValueStepper("Escala", "${"%.2f".format(character.scale)}", { character = character.copy(scale = max(0.1, character.scale - 0.05)) }, { character = character.copy(scale = character.scale + 0.05) })
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("FLIP X", color = MakerPalette.White, fontWeight = FontWeight.Bold)
                            Switch(checked = character.flipX, onCheckedChange = { character = character.copy(flipX = it) })
                        }
                        MakerButton("SALVAR PERSONAGEM", { persist(character) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showAddPose) {
        var poseName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPose = false },
            title = { Text("Nova pose") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use um ID descritivo. Ex.: hey, laugh, dodge, transform.")
                    OutlinedTextField(poseName, { poseName = it }, label = { Text("ID da pose") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = poseName.trim().replace(Regex("\\s+"), "_")
                    if (id.isNotBlank()) {
                        ensurePose(id)
                        selectedPoseId = id
                        showAddPose = false
                    }
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { showAddPose = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MakerAxisPoseV4(x: Double, y: Double, onChange: (Double, Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MakerSectionHeader("Offset • X ${x.toInt()}  Y ${y.toInt()}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MakerSquareButton("◀", { onChange(x - 1, y) }, Modifier.weight(1f), size = 44.dp)
            MakerSquareButton("▶", { onChange(x + 1, y) }, Modifier.weight(1f), size = 44.dp)
            MakerSquareButton("▲", { onChange(x, y - 1) }, Modifier.weight(1f), size = 44.dp)
            MakerSquareButton("▼", { onChange(x, y + 1) }, Modifier.weight(1f), size = 44.dp)
        }
    }
}
