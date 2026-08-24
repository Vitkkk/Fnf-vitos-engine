package com.vitkkk.fnfmobilestudio.ui

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.AppConfig
import com.vitkkk.fnfmobilestudio.export.ProjectExportService
import com.vitkkk.fnfmobilestudio.export.PsychV1Exporter
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface StudioRouteV2 {
    data object Home : StudioRouteV2
    data object Dashboard : StudioRouteV2
    data object Weeks : StudioRouteV2
    data class WeekEditor(val id: String) : StudioRouteV2
    data object Freeplay : StudioRouteV2
    data class SongEditor(val id: String) : StudioRouteV2
    data object Characters : StudioRouteV2
    data class CharacterEditor(val id: String) : StudioRouteV2
    data object Stages : StudioRouteV2
    data class StageEditor(val id: String) : StudioRouteV2
    data object Assets : StudioRouteV2
    data object Settings : StudioRouteV2
    data object Export : StudioRouteV2
}

private data class CharacterChoice(val id: String, val name: String, val imageAssetId: String?)
private data class StageChoice(val id: String, val name: String, val imageAssetId: String?)

@Composable
fun StudioAppV2(projectStore: ProjectStore) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var project by remember { mutableStateOf<Project?>(null) }
    var route by remember { mutableStateOf<StudioRouteV2>(StudioRouteV2.Home) }
    var showCreateProject by remember { mutableStateOf(false) }
    var deleteProject by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() { projects = projectStore.listProjects() }
    fun save(updated: Project) {
        project = updated
        projects = projects.map { if (it.id == updated.id) updated else it }
        scope.launch { projectStore.save(updated); reload() }
    }

    LaunchedEffect(Unit) { reload() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val active = project
            when {
                route == StudioRouteV2.Home || active == null -> HomeV2(
                    projects = projects,
                    onCreate = { showCreateProject = true },
                    onOpen = { project = it; route = StudioRouteV2.Dashboard },
                    onDelete = { deleteProject = it }
                )
                route == StudioRouteV2.Dashboard -> DashboardV2(active, { project = null; route = StudioRouteV2.Home }) { route = it }
                route == StudioRouteV2.Weeks -> WeeksV2(active, { route = StudioRouteV2.Dashboard }, { route = StudioRouteV2.WeekEditor(it) }, ::save)
                route is StudioRouteV2.WeekEditor -> WeekEditorV2(active, (route as StudioRouteV2.WeekEditor).id, { route = StudioRouteV2.Weeks }, { route = StudioRouteV2.SongEditor(it) }, ::save)
                route == StudioRouteV2.Freeplay -> FreeplayV2(active, { route = StudioRouteV2.Dashboard }, { route = StudioRouteV2.SongEditor(it) }, ::save)
                route is StudioRouteV2.SongEditor -> SongEditorV2(active, (route as StudioRouteV2.SongEditor).id, projectStore, { route = StudioRouteV2.Dashboard }, ::save)
                route == StudioRouteV2.Characters -> CharactersV2(active, { route = StudioRouteV2.Dashboard }, { route = StudioRouteV2.CharacterEditor(it) }, ::save)
                route is StudioRouteV2.CharacterEditor -> CharacterEditorV2(active, (route as StudioRouteV2.CharacterEditor).id, projectStore, { route = StudioRouteV2.Characters }, ::save)
                route == StudioRouteV2.Stages -> StagesV2(active, { route = StudioRouteV2.Dashboard }, { route = StudioRouteV2.StageEditor(it) }, ::save)
                route is StudioRouteV2.StageEditor -> StageEditorV2(active, (route as StudioRouteV2.StageEditor).id, projectStore, { route = StudioRouteV2.Stages }, ::save)
                route == StudioRouteV2.Assets -> AssetsV2(active) { route = StudioRouteV2.Dashboard }
                route == StudioRouteV2.Settings -> SettingsV2(active, { route = StudioRouteV2.Dashboard }, ::save)
                route == StudioRouteV2.Export -> ExportV2(active, projectStore) { route = StudioRouteV2.Dashboard }
            }
        }
    }

    if (showCreateProject) CreateProjectDialogV2(
        onDismiss = { showCreateProject = false },
        onCreate = { name, author -> scope.launch {
            val made = projectStore.createProject(name, author)
            reload(); project = made; route = StudioRouteV2.Dashboard; showCreateProject = false
        } }
    )

    deleteProject?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteProject = null },
            title = { Text("Excluir mod?") },
            text = { Text("${target.name} será removido do armazenamento interno do app.") },
            confirmButton = { TextButton(onClick = { scope.launch { projectStore.delete(target.id); deleteProject = null; reload() } }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleteProject = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeV2(projects: List<Project>, onCreate: () -> Unit, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Editor visual de mods FNF para Android")
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ Novo Mod") }
                }
            }
        }
        item { Text("Projetos recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (projects.isEmpty()) item { EmptyV2("Nenhum projeto", "Crie seu primeiro mod.") }
        items(projects, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(p) }, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${p.weeks.size} Weeks • ${p.songs.size} músicas • ${p.characters.size} personagens")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(p) }) { Text("Abrir") }
                        OutlinedButton(onClick = { onDelete(p) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardV2(project: Project, onBack: () -> Unit, onNavigate: (StudioRouteV2) -> Unit) {
    val freeplay = project.songs.count { song -> project.weeks.none { song.id in it.songIds } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderV2(project.name, "${project.weeks.size} Weeks • ${project.songs.size} músicas", onBack) }
        item { DashboardPairV2("Weeks", project.weeks.size, StudioRouteV2.Weeks, "Freeplay", freeplay, StudioRouteV2.Freeplay, onNavigate) }
        item { DashboardPairV2("Personagens", project.characters.size, StudioRouteV2.Characters, "Stages", project.stages.size, StudioRouteV2.Stages, onNavigate) }
        item { DashboardPairV2("Assets", project.assets.size, StudioRouteV2.Assets, "Configurações", 0, StudioRouteV2.Settings, onNavigate) }
        item { Button(onClick = { onNavigate(StudioRouteV2.Export) }, modifier = Modifier.fillMaxWidth()) { Text("Exportar para Psych Engine") } }
    }
}

@Composable
private fun DashboardPairV2(a: String, ac: Int, ar: StudioRouteV2, b: String, bc: Int, br: StudioRouteV2, onNavigate: (StudioRouteV2) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardItemV2(a, ac, Modifier.weight(1f)) { onNavigate(ar) }
        DashboardItemV2(b, bc, Modifier.weight(1f)) { onNavigate(br) }
    }
}

@Composable
private fun DashboardItemV2(label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(if (count > 0) count.toString() else "Abrir →", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WeeksV2(project: Project, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Weeks", "Story Mode", onBack) }
        item { Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Nova Week") } }
        if (project.weeks.isEmpty()) item { EmptyV2("Nenhuma Week", "Crie uma Week para começar.") }
        items(project.weeks, key = { it.id }) { week ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(week.id) }) {
                Column(Modifier.padding(15.dp)) {
                    Text(week.displayName, fontWeight = FontWeight.Bold)
                    Text("ID ${week.id} • ${week.songIds.size} músicas")
                }
            }
        }
    }
    if (create) SimpleCreateDialogV2("Nova Week", "Nome") { create = false } onCreate@{ name, id ->
        onChange(project.copy(weeks = project.weeks + Week(id, id, name))); create = false
    }
}

@Composable
private fun WeekEditorV2(project: Project, weekId: String, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    val week = project.weeks.firstOrNull { it.id == weekId } ?: return
    var newSong by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val songs = project.songs.associateBy { it.id }
    fun replace(updated: Week) = onChange(project.copy(weeks = project.weeks.map { if (it.id == week.id) updated else it }))

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2(week.displayName, "${week.songIds.size} músicas", onBack) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { newSong = true }, modifier = Modifier.weight(1f)) { Text("＋ Música") }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) { Text("Existente") }
        } }
        items(week.songIds) { id -> songs[id]?.let { song ->
            Card(Modifier.fillMaxWidth().clickable { onSong(song.id) }) { Column(Modifier.padding(14.dp)) {
                Text(song.displayName, fontWeight = FontWeight.Bold); Text("${song.bpm} BPM")
                TextButton(onClick = { replace(week.copy(songIds = week.songIds - song.id)) }) { Text("Remover da Week") }
            } }
        } }
    }
    if (newSong) NewSongDialogV2({ newSong = false }) { song ->
        onChange(project.copy(songs = project.songs + song, weeks = project.weeks.map { if (it.id == week.id) it.copy(songIds = it.songIds + song.id) else it })); newSong = false; onSong(song.id)
    }
    if (picker) SongPickerV2(project.songs.filterNot { it.id in week.songIds }, { picker = false }) { song -> replace(week.copy(songIds = week.songIds + song.id)); picker = false }
}

@Composable
private fun FreeplayV2(project: Project, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    val assigned = project.weeks.flatMap { it.songIds }.toSet()
    val songs = project.songs.filterNot { it.id in assigned }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Freeplay", "Músicas fora de Weeks", onBack) }
        item { Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Música Freeplay") } }
        if (songs.isEmpty()) item { EmptyV2("Nenhuma música Freeplay", "Crie uma música exclusiva.") }
        items(songs, key = { it.id }) { song -> Card(Modifier.fillMaxWidth().clickable { onSong(song.id) }) { Column(Modifier.padding(15.dp)) { Text(song.displayName, fontWeight = FontWeight.Bold); Text("${song.bpm} BPM • ID ${song.id}") } } }
    }
    if (create) NewSongDialogV2({ create = false }) { song -> onChange(project.copy(songs = project.songs + song)); create = false; onSong(song.id) }
}

@Composable
private fun SongEditorV2(project: Project, id: String, store: ProjectStore, onBack: () -> Unit, onChange: (Project) -> Unit) {
    val song = project.songs.firstOrNull { it.id == id } ?: return
    var name by remember(song.id, song.displayName) { mutableStateOf(song.displayName) }
    var bpm by remember(song.id, song.bpm) { mutableStateOf(song.bpm.toString()) }
    var stageId by remember(song.id, song.stageId) { mutableStateOf(song.stageId ?: "stage") }
    var playerId by remember(song.id, song.playerId) { mutableStateOf(song.playerId ?: "bf") }
    var opponentId by remember(song.id, song.opponentId) { mutableStateOf(song.opponentId ?: "dad") }
    var gfId by remember(song.id, song.girlfriendId) { mutableStateOf(song.girlfriendId ?: "gf") }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val assets = project.assets.associateBy { it.id }

    fun replace(updated: Song, base: Project = project) = onChange(base.copy(songs = base.songs.map { if (it.id == song.id) updated else it }))

    val inst = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) scope.launch {
        val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
        replace(song.copy(instrumentalAssetId = asset.id), project.copy(assets = project.assets + asset))
    } }
    val vocals = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) scope.launch {
        val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
        replace(song.copy(vocalsAssetId = asset.id), project.copy(assets = project.assets + asset))
    } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderV2(song.displayName, "Editor da música", onBack) }
        item { EditorCardV2("Dados") {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            SelectorFieldV2("Stage", stageId, stageName(project, stageId), { picker = "stage" }, true) { stageId = "stage" }
            SelectorFieldV2("Boyfriend / Player", playerId, characterName(project, playerId), { picker = "player" })
            SelectorFieldV2("Opponent", opponentId, characterName(project, opponentId), { picker = "opponent" })
            SelectorFieldV2("Girlfriend", gfId, characterName(project, gfId), { picker = "gf" })
            Button(onClick = {
                val parsed = bpm.toDoubleOrNull()?.takeIf { it > 0 } ?: song.bpm
                replace(song.copy(displayName = name.trim().ifBlank { song.displayName }, bpm = parsed, tempoMap = if (song.tempoMap.size == 1) listOf(TempoPoint(0.0, parsed)) else song.tempoMap, stageId = stageId, playerId = playerId, opponentId = opponentId, girlfriendId = gfId))
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar dados") }
        } }
        item { EditorCardV2("Áudio") {
            Text("Instrumental: ${song.instrumentalAssetId?.let { assets[it]?.relativePath } ?: "nenhum"}")
            Button(onClick = { inst.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar / trocar Instrumental") }
            Text("Vocals: ${song.vocalsAssetId?.let { assets[it]?.relativePath } ?: "nenhum"}")
            OutlinedButton(onClick = { vocals.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar / trocar Vocals") }
        } }
        item { EditorCardV2("Chart") {
            Text("${song.difficulties.sumOf { it.chart.notes.size }} notas")
            Text("O próximo passo grande é abrir o Chart Editor touch aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }

    when (picker) {
        "stage" -> StagePickerDialogV2(project, store, stageId, { picker = null }) { stageId = it; picker = null }
        "player", "opponent", "gf" -> CharacterPickerDialogV2(project, store, when (picker) { "player" -> playerId; "opponent" -> opponentId; else -> gfId }, { picker = null }) { value ->
            when (picker) { "player" -> playerId = value; "opponent" -> opponentId = value; "gf" -> gfId = value }
            picker = null
        }
    }
}

@Composable
private fun CharactersV2(project: Project, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Personagens", "IDs disponíveis para as músicas e Stages", onBack) }
        item { Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar personagem") } }
        item { Text("Padrões Psych: bf • dad • gf", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (project.characters.isEmpty()) item { EmptyV2("Nenhum personagem customizado", "Os IDs padrão continuam disponíveis nos seletores.") }
        items(project.characters, key = { it.id }) { c -> Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssetThumbV2(project, c.iconAssetId ?: c.imageAssetId, projectStore = null, modifier = Modifier.size(58.dp))
                Column { Text(c.displayName, fontWeight = FontWeight.Bold); Text("ID ${c.id}"); Text("Editar preview/ícone →", color = MaterialTheme.colorScheme.primary) }
            }
        } }
    }
    if (create) SimpleCreateDialogV2("Novo personagem", "Nome", { create = false }) { name, id -> onChange(project.copy(characters = project.characters + CharacterDefinition(id = id, displayName = name))); create = false; onOpen(id) }
}

@Composable
private fun CharacterEditorV2(project: Project, id: String, store: ProjectStore, onBack: () -> Unit, onChange: (Project) -> Unit) {
    val character = project.characters.firstOrNull { it.id == id } ?: return
    var scale by remember(character.id, character.scale) { mutableStateOf(character.scale.toString()) }
    var flip by remember(character.id, character.flipX) { mutableStateOf(character.flipX) }
    val scope = rememberCoroutineScope()

    fun replace(c: CharacterDefinition, base: Project = project) = onChange(base.copy(characters = base.characters.map { if (it.id == c.id) c else it }))

    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) scope.launch {
        val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/characters")
        replace(character.copy(imageAssetId = asset.id), project.copy(assets = project.assets + asset))
    } }
    val iconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) scope.launch {
        val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/icons")
        replace(character.copy(iconAssetId = asset.id), project.copy(assets = project.assets + asset))
    } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderV2(character.displayName, "Character Editor • ID ${character.id}", onBack) }
        item { EditorCardV2("Preview") {
            AssetThumbV2(project, character.imageAssetId, store, Modifier.fillMaxWidth().height(220.dp))
            Button(onClick = { previewLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar imagem de preview") }
            Text("Essa imagem já aparece no Stage Editor. O atlas/animações será ligado depois.", style = MaterialTheme.typography.bodySmall)
        } }
        item { EditorCardV2("Ícone do seletor") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssetThumbV2(project, character.iconAssetId, store, Modifier.size(72.dp))
                Column(Modifier.weight(1f)) { Text("Ícone usado nas listas"); OutlinedButton(onClick = { iconLauncher.launch(arrayOf("image/*")) }) { Text("Importar ícone") } }
            }
        } }
        item { EditorCardV2("Transformação") {
            OutlinedTextField(scale, { scale = it }, label = { Text("Scale") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Flip X"); Switch(flip, { flip = it }) }
            Button(onClick = { replace(character.copy(scale = scale.toDoubleOrNull()?.takeIf { it > 0 } ?: character.scale, flipX = flip)) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        } }
    }
}

@Composable
private fun StagesV2(project: Project, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Stages", "Cenários e posicionamento touch", onBack) }
        item { Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar Stage") } }
        item { Text("O ID padrão 'stage' continua disponível no seletor de música.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (project.stages.isEmpty()) item { EmptyV2("Nenhum Stage customizado", "Crie um para abrir o editor visual.") }
        items(project.stages, key = { it.id }) { stage -> Card(Modifier.fillMaxWidth().clickable { onOpen(stage.id) }) { Column(Modifier.padding(15.dp)) { Text(stage.displayName, fontWeight = FontWeight.Bold); Text("ID ${stage.id} • ${stage.objects.size} objetos"); Text("Abrir Stage Editor →", color = MaterialTheme.colorScheme.primary) } } }
    }
    if (create) SimpleCreateDialogV2("Novo Stage", "Nome", { create = false }) { name, id -> onChange(project.copy(stages = project.stages + StageDefinition(id = id, displayName = name))); create = false; onOpen(id) }
}

@Composable
private fun StageEditorV2(project: Project, id: String, store: ProjectStore, onBack: () -> Unit, onChange: (Project) -> Unit) {
    val original = project.stages.firstOrNull { it.id == id } ?: return
    var stage by remember(original) { mutableStateOf(original) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) scope.launch {
        val asset = store.importAsset(project.id, uri, AssetKind.IMAGE, "images/stages")
        stage = stage.copy(previewAssetId = asset.id)
        onChange(project.copy(assets = project.assets + asset, stages = project.stages.map { if (it.id == stage.id) stage else it }))
    } }

    fun save() = onChange(project.copy(stages = project.stages.map { if (it.id == stage.id) stage else it }))

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2(stage.displayName, "Stage Editor • arraste os personagens", onBack) }
        item {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { canvasSize = it }
            ) {
                StageBackgroundV2(project, stage.previewAssetId, store)
                CharacterMarkerV2(project, store, stage.previewOpponentId ?: "dad", stage.opponent, canvasSize, "Opponent") { dx, dy -> stage = stage.copy(opponent = moved(stage.opponent, dx, dy, canvasSize)) }
                CharacterMarkerV2(project, store, stage.previewGirlfriendId ?: "gf", stage.girlfriend, canvasSize, "GF") { dx, dy -> stage = stage.copy(girlfriend = moved(stage.girlfriend, dx, dy, canvasSize)) }
                CharacterMarkerV2(project, store, stage.previewBoyfriendId ?: "bf", stage.boyfriend, canvasSize, "BF") { dx, dy -> stage = stage.copy(boyfriend = moved(stage.boyfriend, dx, dy, canvasSize)) }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bgLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("Imagem do Stage") }
            Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("Salvar posições") }
        } }
        item { EditorCardV2("Personagens de preview") {
            SelectorFieldV2("Boyfriend", stage.previewBoyfriendId ?: "bf", characterName(project, stage.previewBoyfriendId ?: "bf"), { picker = "bf" })
            SelectorFieldV2("Opponent", stage.previewOpponentId ?: "dad", characterName(project, stage.previewOpponentId ?: "dad"), { picker = "dad" })
            SelectorFieldV2("Girlfriend", stage.previewGirlfriendId ?: "gf", characterName(project, stage.previewGirlfriendId ?: "gf"), { picker = "gf" })
            Text("Esses IDs são só para preview do editor. A música continua escolhendo os personagens exportados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { EditorCardV2("Coordenadas Psych") {
            Text("Opponent: X ${stage.opponent.x.roundToInt()} • Y ${stage.opponent.y.roundToInt()}")
            Text("GF: X ${stage.girlfriend.x.roundToInt()} • Y ${stage.girlfriend.y.roundToInt()}")
            Text("BF: X ${stage.boyfriend.x.roundToInt()} • Y ${stage.boyfriend.y.roundToInt()}")
        } }
    }

    if (picker != null) CharacterPickerDialogV2(project, store, when (picker) { "bf" -> stage.previewBoyfriendId ?: "bf"; "dad" -> stage.previewOpponentId ?: "dad"; else -> stage.previewGirlfriendId ?: "gf" }, { picker = null }) { selected ->
        stage = when (picker) { "bf" -> stage.copy(previewBoyfriendId = selected); "dad" -> stage.copy(previewOpponentId = selected); else -> stage.copy(previewGirlfriendId = selected) }
        picker = null
    }
}

private fun moved(slot: StageCharacterSlot, dxPx: Float, dyPx: Float, size: IntSize): StageCharacterSlot {
    if (size.width <= 0 || size.height <= 0) return slot
    val dx = dxPx / size.width.toDouble() * 1280.0
    val dy = dyPx / size.height.toDouble() * 720.0
    return slot.copy(x = (slot.x + dx).coerceIn(0.0, 1280.0), y = (slot.y + dy).coerceIn(0.0, 720.0))
}

@Composable
private fun CharacterMarkerV2(project: Project, store: ProjectStore, id: String, slot: StageCharacterSlot, size: IntSize, label: String, onDrag: (Float, Float) -> Unit) {
    if (size.width <= 0 || size.height <= 0) return
    val x = (slot.x / 1280.0 * size.width).roundToInt()
    val y = (slot.y / 720.0 * size.height).roundToInt()
    Column(
        Modifier.offset { IntOffset(x - 30, y - 30) }.width(84.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(id, size) { detectDragGestures { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AssetThumbV2(project, characterAssetId(project, id), store, Modifier.size(50.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(id, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StageBackgroundV2(project: Project, assetId: String?, store: ProjectStore) {
    val asset = project.assets.firstOrNull { it.id == assetId }
    val bitmap = remember(assetId, asset?.relativePath) {
        asset?.let { runCatching { BitmapFactory.decodeFile(store.assetFile(project.id, it).absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

@Composable
private fun AssetsV2(project: Project, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HeaderV2("Assets", "Arquivos internos", onBack) }
        if (project.assets.isEmpty()) item { EmptyV2("Nenhum asset", "Importe áudio ou imagens pelos editores.") }
        items(project.assets, key = { it.id }) { a -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(a.kind.name, fontWeight = FontWeight.Bold); Text(a.relativePath) } } }
    }
}

@Composable
private fun SettingsV2(project: Project, onBack: () -> Unit, onChange: (Project) -> Unit) {
    var name by remember(project.name) { mutableStateOf(project.name) }
    var author by remember(project.author) { mutableStateOf(project.author) }
    var description by remember(project.description) { mutableStateOf(project.description) }
    var version by remember(project.version) { mutableStateOf(project.version) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Configurações", "Metadata do mod", onBack) }
        item { EditorCardV2("Projeto") {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(version, { version = it }, label = { Text("Versão") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onChange(project.copy(name = name.trim().ifBlank { project.name }, author = author.trim(), description = description.trim(), version = version.trim().ifBlank { project.version })) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        } }
    }
}

@Composable
private fun ExportV2(project: Project, store: ProjectStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { PsychV1Exporter() }
    val service = remember(store) { ProjectExportService(context, store, exporter) }
    val bundle = remember(project) { exporter.export(project) }
    var status by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderV2("Exportar", "Psych Engine • psych_v1", onBack) }
        item { EditorCardV2("Pré-validação") { Text("${bundle.artifacts.size} arquivos"); Text("${bundle.warnings.size} aviso(s)") } }
        items(bundle.warnings) { warning -> Card(Modifier.fillMaxWidth()) { Text(warning, Modifier.padding(12.dp)) } }
        item { Button(onClick = { scope.launch { runCatching { service.buildZip(project) }.onSuccess { result ->
            status = "ZIP criado: ${result.artifactCount} arquivos"
            val share = Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, service.shareUri(result.file)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.startActivity(Intent.createChooser(share, "Compartilhar mod"))
        }.onFailure { status = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text("Gerar ZIP e compartilhar") } }
        status?.let { item { Text(it) } }
    }
}

@Composable
private fun SelectorFieldV2(label: String, id: String, display: String, onOpen: () -> Unit, optional: Boolean = false, onClear: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelLarge); Text(display, fontWeight = FontWeight.Bold); Text("ID: $id", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Selecionar ›", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (optional && onClear != null && id != "stage") TextButton(onClick = onClear) { Text("Usar padrão") }
}

@Composable
private fun CharacterPickerDialogV2(project: Project, store: ProjectStore, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val choices = characterChoices(project)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar personagem") },
        text = { LazyColumn(Modifier.heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(choices, key = { it.id }) { choice ->
                Card(Modifier.fillMaxWidth().clickable { onPick(choice.id) }, colors = CardDefaults.cardColors(containerColor = if (choice.id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AssetThumbV2(project, choice.imageAssetId, store, Modifier.size(56.dp), fallback = choice.id.take(2).uppercase())
                        Column { Text(choice.name, fontWeight = FontWeight.Bold); Text("ID ${choice.id}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun StagePickerDialogV2(project: Project, store: ProjectStore, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val choices = stageChoices(project)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar Stage") },
        text = { LazyColumn(Modifier.heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(choices, key = { it.id }) { choice -> Card(Modifier.fillMaxWidth().clickable { onPick(choice.id) }, colors = CardDefaults.cardColors(containerColor = if (choice.id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssetThumbV2(project, choice.imageAssetId, store, Modifier.size(70.dp), fallback = "ST")
                    Column { Text(choice.name, fontWeight = FontWeight.Bold); Text("ID ${choice.id}") }
                }
            } }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

private fun characterChoices(project: Project): List<CharacterChoice> {
    val customs = project.characters.associateBy { it.id }
    val defaults = listOf("bf" to "Boyfriend", "dad" to "Dad / Opponent", "gf" to "Girlfriend")
    val result = defaults.map { (id, label) -> customs[id]?.let { CharacterChoice(it.id, it.displayName, it.iconAssetId ?: it.imageAssetId) } ?: CharacterChoice(id, label, null) }.toMutableList()
    project.characters.filterNot { it.id in defaults.map { d -> d.first } }.forEach { result += CharacterChoice(it.id, it.displayName, it.iconAssetId ?: it.imageAssetId) }
    return result
}

private fun stageChoices(project: Project): List<StageChoice> = buildList {
    add(StageChoice("stage", "Stage padrão da Psych", null))
    project.stages.filterNot { it.id == "stage" }.forEach { add(StageChoice(it.id, it.displayName, it.previewAssetId)) }
    project.stages.firstOrNull { it.id == "stage" }?.let { this[0] = StageChoice("stage", it.displayName, it.previewAssetId) }
}

private fun characterName(project: Project, id: String): String = project.characters.firstOrNull { it.id == id }?.displayName ?: when (id) { "bf" -> "Boyfriend"; "dad" -> "Dad / Opponent"; "gf" -> "Girlfriend"; else -> id }
private fun stageName(project: Project, id: String): String = project.stages.firstOrNull { it.id == id }?.displayName ?: if (id == "stage") "Stage padrão da Psych" else id
private fun characterAssetId(project: Project, id: String): String? = project.characters.firstOrNull { it.id == id }?.let { it.imageAssetId ?: it.iconAssetId }

@Composable
private fun AssetThumbV2(project: Project, assetId: String?, projectStore: ProjectStore?, modifier: Modifier, fallback: String = "IMG") {
    val asset = project.assets.firstOrNull { it.id == assetId }
    val bitmap = remember(project.id, assetId, asset?.relativePath, projectStore) {
        if (asset == null || projectStore == null) null else runCatching { BitmapFactory.decodeFile(projectStore.assetFile(project.id, asset).absolutePath)?.asImageBitmap() }.getOrNull()
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(fallback, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeaderV2(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) { TextButton(onClick = onBack) { Text("← Voltar") }; Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun EditorCardV2(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); HorizontalDivider(); content() } }
}

@Composable
private fun EmptyV2(title: String, body: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) } } }

@Composable
private fun CreateProjectDialogV2(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Novo Mod") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Nome") }); OutlinedTextField(author, { author = it }, label = { Text("Autor") }) } }, confirmButton = { Button(onClick = { onCreate(name, author) }, enabled = name.isNotBlank()) { Text("Criar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SimpleCreateDialogV2(title: String, label: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var id by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it; if (id.isBlank()) id = slugV2(it) }, label = { Text(label) }); OutlinedTextField(id, { id = slugV2(it) }, label = { Text("ID interno") }) } }, confirmButton = { Button(onClick = { onCreate(name.trim(), slugV2(id)) }, enabled = name.isNotBlank() && id.isNotBlank()) { Text("Criar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun NewSongDialogV2(onDismiss: () -> Unit, onCreate: (Song) -> Unit) {
    var name by remember { mutableStateOf("") }; var id by remember { mutableStateOf("") }; var bpm by remember { mutableStateOf("120") }
    val parsed = bpm.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nova música") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it; if (id.isBlank()) id = slugV2(it) }, label = { Text("Nome") }); OutlinedTextField(id, { id = slugV2(it) }, label = { Text("ID interno") }); OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } }, confirmButton = { Button(onClick = { onCreate(Song(slugV2(id), name.trim(), parsed ?: 120.0)) }, enabled = name.isNotBlank() && id.isNotBlank() && parsed != null && parsed > 0) { Text("Criar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SongPickerV2(songs: List<Song>, onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adicionar música") }, text = { if (songs.isEmpty()) Text("Nenhuma música disponível") else LazyColumn { items(songs) { s -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onPick(s) }) { Column(Modifier.padding(10.dp)) { Text(s.displayName, fontWeight = FontWeight.Bold); Text("${s.bpm} BPM") } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

private fun slugV2(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").replace(Regex("-+"), "-").trim('-').ifEmpty { "untitled" }
