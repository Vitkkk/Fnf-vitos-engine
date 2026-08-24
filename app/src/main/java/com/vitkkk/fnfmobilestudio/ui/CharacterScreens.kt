package com.vitkkk.fnfmobilestudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.model.AssetKind
import com.vitkkk.fnfmobilestudio.model.CharacterDefinition
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch

@Composable
internal fun CharacterManagerScreenV3(
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
        item { StudioHeaderV3("Personagens", "IDs disponíveis nos seletores", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar personagem") } }
        item { Text("Padrões sempre disponíveis: bf • dad • gf", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (project.characters.isEmpty()) {
            item { EmptyCardV3("Nenhum personagem customizado", "Os personagens padrão continuam disponíveis.") }
        }
        items(project.characters, key = { it.id }) { character ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(character.id) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AssetThumbnail(
                        project,
                        character.iconAssetId ?: character.imageAssetId,
                        store,
                        Modifier.size(60.dp),
                        character.id.take(2).uppercase()
                    )
                    Column(Modifier.weight(1f)) {
                        Text(character.displayName, fontWeight = FontWeight.Bold)
                        Text("ID ${character.id}")
                        Text("Editar preview/ícone →", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showCreate) {
        SimpleIdDialogV3(
            title = "Novo personagem",
            label = "Nome",
            onDismiss = { showCreate = false },
            onCreate = { name, id ->
                onProjectChange(project.copy(characters = project.characters + CharacterDefinition(id = id, displayName = name)))
                showCreate = false
                onOpen(id)
            }
        )
    }
}

@Composable
internal fun CharacterEditorScreenV3(
    project: Project,
    characterId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val character = project.characters.firstOrNull { it.id == characterId } ?: return
    var scale by remember(character.id, character.scale) { mutableStateOf(character.scale.toString()) }
    var flipX by remember(character.id, character.flipX) { mutableStateOf(character.flipX) }
    val scope = rememberCoroutineScope()

    fun replace(updated: CharacterDefinition, base: Project = project) {
        onProjectChange(base.copy(characters = base.characters.map { if (it.id == updated.id) updated else it }))
    }

    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/characters")
            replace(character.copy(imageAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }
    val iconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/icons")
            replace(character.copy(iconAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StudioHeaderV3(character.displayName, "Character Editor • ID ${character.id}", onBack) }
        item {
            EditorCardV3("Preview do personagem") {
                AssetThumbnail(project, character.imageAssetId, store, Modifier.fillMaxWidth().height(230.dp), "PREVIEW")
                Button(onClick = { previewLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar imagem de preview")
                }
                Text(
                    "Esse preview já aparece no Stage Editor. Depois ele será substituído/ligado ao atlas animado real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            EditorCardV3("Ícone do seletor") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssetThumbnail(project, character.iconAssetId, store, Modifier.size(78.dp), character.id.take(2).uppercase())
                    Column(Modifier.weight(1f)) {
                        Text("Imagem pequena usada nas listas")
                        OutlinedButton(onClick = { iconLauncher.launch(arrayOf("image/*")) }) { Text("Importar ícone") }
                    }
                }
            }
        }
        item {
            EditorCardV3("Transformação") {
                OutlinedTextField(
                    value = scale,
                    onValueChange = { scale = it },
                    label = { Text("Scale") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flip X")
                    Switch(checked = flipX, onCheckedChange = { flipX = it })
                }
                Button(
                    onClick = {
                        replace(character.copy(
                            scale = scale.toDoubleOrNull()?.takeIf { it > 0 } ?: character.scale,
                            flipX = flipX
                        ))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar") }
            }
        }
    }
}
