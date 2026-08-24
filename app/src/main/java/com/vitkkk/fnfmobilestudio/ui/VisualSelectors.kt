package com.vitkkk.fnfmobilestudio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.storage.ProjectStore

internal data class CharacterChoice(val id: String, val name: String, val imageAssetId: String?)
internal data class StageChoice(val id: String, val name: String, val imageAssetId: String?)

@Composable
internal fun SelectorField(
    label: String,
    id: String,
    displayName: String,
    onOpen: () -> Unit,
    optional: Boolean = false,
    onUseDefault: (() -> Unit)? = null
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(displayName, fontWeight = FontWeight.Bold)
                Text("ID: $id", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Selecionar ›", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (optional && onUseDefault != null && id != "stage") {
        TextButton(onClick = onUseDefault) { Text("Usar padrão") }
    }
}

@Composable
internal fun CharacterPickerDialog(
    project: Project,
    store: ProjectStore,
    selectedId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar personagem") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(characterChoices(project), key = { it.id }) { choice ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onPick(choice.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (choice.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssetThumbnail(project, choice.imageAssetId, store, Modifier.size(58.dp), choice.id.take(2).uppercase())
                            Column {
                                Text(choice.name, fontWeight = FontWeight.Bold)
                                Text("ID ${choice.id}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
internal fun StagePickerDialog(
    project: Project,
    store: ProjectStore,
    selectedId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar Stage") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(stageChoices(project), key = { it.id }) { choice ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onPick(choice.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (choice.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssetThumbnail(project, choice.imageAssetId, store, Modifier.size(72.dp), "ST")
                            Column {
                                Text(choice.name, fontWeight = FontWeight.Bold)
                                Text("ID ${choice.id}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
internal fun AssetThumbnail(
    project: Project,
    assetId: String?,
    store: ProjectStore,
    modifier: Modifier,
    fallback: String = "IMG"
) {
    val asset = project.assets.firstOrNull { it.id == assetId }
    val bitmap = remember(project.id, assetId, asset?.relativePath) {
        asset?.let {
            runCatching {
                BitmapFactory.decodeFile(store.assetFile(project.id, it).absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(fallback, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun characterName(project: Project, id: String): String =
    project.characters.firstOrNull { it.id == id }?.displayName ?: when (id) {
        "bf" -> "Boyfriend"
        "dad" -> "Dad / Opponent"
        "gf" -> "Girlfriend"
        else -> id
    }

internal fun stageName(project: Project, id: String): String =
    project.stages.firstOrNull { it.id == id }?.displayName ?: if (id == "stage") "Stage padrão da Psych" else id

internal fun characterPreviewAssetId(project: Project, id: String): String? =
    project.characters.firstOrNull { it.id == id }?.let { it.imageAssetId ?: it.iconAssetId }

private fun characterChoices(project: Project): List<CharacterChoice> {
    val customById = project.characters.associateBy { it.id }
    val defaults = listOf(
        "bf" to "Boyfriend",
        "dad" to "Dad / Opponent",
        "gf" to "Girlfriend"
    )
    val out = defaults.map { (id, label) ->
        customById[id]?.let { CharacterChoice(it.id, it.displayName, it.iconAssetId ?: it.imageAssetId) }
            ?: CharacterChoice(id, label, null)
    }.toMutableList()
    project.characters.filterNot { it.id in defaults.map { pair -> pair.first } }.forEach {
        out += CharacterChoice(it.id, it.displayName, it.iconAssetId ?: it.imageAssetId)
    }
    return out
}

private fun stageChoices(project: Project): List<StageChoice> {
    val customStage = project.stages.firstOrNull { it.id == "stage" }
    return buildList {
        add(StageChoice("stage", customStage?.displayName ?: "Stage padrão da Psych", customStage?.previewAssetId))
        project.stages.filterNot { it.id == "stage" }.forEach {
            add(StageChoice(it.id, it.displayName, it.previewAssetId))
        }
    }
}
