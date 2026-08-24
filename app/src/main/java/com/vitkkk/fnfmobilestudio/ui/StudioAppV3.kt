package com.vitkkk.fnfmobilestudio.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.AppConfig
import com.vitkkk.fnfmobilestudio.export.ProjectExportService
import com.vitkkk.fnfmobilestudio.export.PsychV1Exporter
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch

private sealed interface StudioRouteV3 {
    data object Home : StudioRouteV3
    data object Dashboard : StudioRouteV3
    data object Weeks : StudioRouteV3
    data class WeekEditor(val id: String) : StudioRouteV3
    data object Freeplay : StudioRouteV3
    data class SongEditor(val id: String) : StudioRouteV3
    data object Characters : StudioRouteV3
    data class CharacterEditor(val id: String) : StudioRouteV3
    data object Stages : StudioRouteV3
    data class StageEditor(val id: String) : StudioRouteV3
    data object Assets : StudioRouteV3
    data object Settings : StudioRouteV3
    data object Export : StudioRouteV3
}

@Composable
fun StudioAppV3(projectStore: ProjectStore) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var activeProject by remember { mutableStateOf<Project?>(null) }
    var route by remember { mutableStateOf<StudioRouteV3>(StudioRouteV3.Home) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reloadProjects() { projects = projectStore.listProjects() }
    fun saveProject(updated: Project) {
        activeProject = updated
        projects = projects.map { if (it.id == updated.id) updated else it }
        scope.launch { projectStore.save(updated); reloadProjects() }
    }

    LaunchedEffect(Unit) { reloadProjects() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val project = activeProject
            when {
                route == StudioRouteV3.Home || project == null -> HomeScreenV3(
                    projects,
                    onCreate = { showCreate = true },
                    onOpen = { activeProject = it; route = StudioRouteV3.Dashboard },
                    onDelete = { pendingDelete = it }
                )
                route == StudioRouteV3.Dashboard -> DashboardScreenV3(project, {
                    activeProject = null
                    route = StudioRouteV3.Home
                }) { route = it }
                route == StudioRouteV3.Weeks -> WeeksScreenV3(project, { route = StudioRouteV3.Dashboard }, { route = StudioRouteV3.WeekEditor(it) }, ::saveProject)
                route is StudioRouteV3.WeekEditor -> WeekEditorScreenV3(project, (route as StudioRouteV3.WeekEditor).id, { route = StudioRouteV3.Weeks }, { route = StudioRouteV3.SongEditor(it) }, ::saveProject)
                route == StudioRouteV3.Freeplay -> FreeplayScreenV3(project, { route = StudioRouteV3.Dashboard }, { route = StudioRouteV3.SongEditor(it) }, ::saveProject)
                route is StudioRouteV3.SongEditor -> SongEditorScreenV3(project, (route as StudioRouteV3.SongEditor).id, projectStore, { route = StudioRouteV3.Dashboard }, ::saveProject)
                route == StudioRouteV3.Characters -> CharacterManagerScreenV3(project, projectStore, { route = StudioRouteV3.Dashboard }, { route = StudioRouteV3.CharacterEditor(it) }, ::saveProject)
                route is StudioRouteV3.CharacterEditor -> CharacterEditorScreenV3(project, (route as StudioRouteV3.CharacterEditor).id, projectStore, { route = StudioRouteV3.Characters }, ::saveProject)
                route == StudioRouteV3.Stages -> StageManagerScreenV3(project, projectStore, { route = StudioRouteV3.Dashboard }, { route = StudioRouteV3.StageEditor(it) }, ::saveProject)
                route is StudioRouteV3.StageEditor -> StageEditorScreenV3(project, (route as StudioRouteV3.StageEditor).id, projectStore, { route = StudioRouteV3.Stages }, ::saveProject)
                route == StudioRouteV3.Assets -> AssetsScreenV3(project) { route = StudioRouteV3.Dashboard }
                route == StudioRouteV3.Settings -> SettingsScreenV3(project, { route = StudioRouteV3.Dashboard }, ::saveProject)
                route == StudioRouteV3.Export -> ExportScreenV3(project, projectStore) { route = StudioRouteV3.Dashboard }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialogV3(
            onDismiss = { showCreate = false },
            onCreate = { name, author ->
                scope.launch {
                    val project = projectStore.createProject(name, author)
                    reloadProjects()
                    activeProject = project
                    route = StudioRouteV3.Dashboard
                    showCreate = false
                }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir mod?") },
            text = { Text("${target.name} e os arquivos internos serão removidos.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { projectStore.delete(target.id); pendingDelete = null; reloadProjects() } }) {
                    Text("Excluir")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeScreenV3(projects: List<Project>, onCreate: () -> Unit, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Mods de FNF no celular, sem editar JSON.")
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ Novo Mod") }
                }
            }
        }
        item { Text("Projetos recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (projects.isEmpty()) item { EmptyCardV3("Nenhum projeto", "Crie seu primeiro mod.") }
        items(projects, key = { it.id }) { project ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(project) }, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (project.author.isNotBlank()) Text("por ${project.author}")
                    Text("${project.weeks.size} Weeks • ${project.songs.size} músicas • ${project.characters.size} personagens")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(project) }) { Text("Abrir") }
                        OutlinedButton(onClick = { onDelete(project) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreenV3(project: Project, onBack: () -> Unit, onNavigate: (StudioRouteV3) -> Unit) {
    val freeplay = project.songs.count { song -> project.weeks.none { song.id in it.songIds } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeaderV3(project.name, "${project.weeks.size} Weeks • ${project.songs.size} músicas", onBack) }
        item { DashboardPairV3("Weeks", project.weeks.size, StudioRouteV3.Weeks, "Freeplay", freeplay, StudioRouteV3.Freeplay, onNavigate) }
        item { DashboardPairV3("Personagens", project.characters.size, StudioRouteV3.Characters, "Stages", project.stages.size, StudioRouteV3.Stages, onNavigate) }
        item { DashboardPairV3("Assets", project.assets.size, StudioRouteV3.Assets, "Configurações", 0, StudioRouteV3.Settings, onNavigate) }
        item { Button(onClick = { onNavigate(StudioRouteV3.Export) }, modifier = Modifier.fillMaxWidth()) { Text("Exportar para Psych Engine") } }
    }
}

@Composable
private fun DashboardPairV3(a: String, ac: Int, ar: StudioRouteV3, b: String, bc: Int, br: StudioRouteV3, onNavigate: (StudioRouteV3) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardCardV3(a, ac, Modifier.weight(1f)) { onNavigate(ar) }
        DashboardCardV3(b, bc, Modifier.weight(1f)) { onNavigate(br) }
    }
}

@Composable
private fun DashboardCardV3(label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(if (count > 0) count.toString() else "Abrir →", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WeeksScreenV3(project: Project, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeaderV3("Weeks", "Organize o Story Mode", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Nova Week") } }
        if (project.weeks.isEmpty()) item { EmptyCardV3("Nenhuma Week", "Crie uma Week e adicione músicas.") }
        items(project.weeks, key = { it.id }) { week ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(week.id) }) {
                Column(Modifier.padding(15.dp)) {
                    Text(week.displayName, fontWeight = FontWeight.Bold)
                    Text("ID ${week.id} • ${week.songIds.size} músicas")
                }
            }
        }
    }
    if (showCreate) {
        SimpleIdDialogV3("Nova Week", "Nome", { showCreate = false }) { name, id ->
            onChange(project.copy(weeks = project.weeks + Week(id = id, internalName = id, displayName = name)))
            showCreate = false
            onOpen(id)
        }
    }
}

@Composable
private fun WeekEditorScreenV3(project: Project, weekId: String, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    val week = project.weeks.firstOrNull { it.id == weekId } ?: return
    var showNewSong by remember { mutableStateOf(false) }
    var showExisting by remember { mutableStateOf(false) }
    val songs = project.songs.associateBy { it.id }
    fun replace(updated: Week) = onChange(project.copy(weeks = project.weeks.map { if (it.id == week.id) updated else it }))

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { StudioHeaderV3(week.displayName, "${week.songIds.size} músicas", onBack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showNewSong = true }, modifier = Modifier.weight(1f)) { Text("＋ Música") }
                OutlinedButton(onClick = { showExisting = true }, modifier = Modifier.weight(1f)) { Text("Existente") }
            }
        }
        items(week.songIds) { id ->
            songs[id]?.let { song ->
                val index = week.songIds.indexOf(id)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Text("${index + 1}. ${song.displayName}", fontWeight = FontWeight.Bold)
                        Text("${song.bpm} BPM")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onSong(song.id) }) { Text("Editar") }
                            TextButton(onClick = {
                                if (index > 0) {
                                    val ids = week.songIds.toMutableList(); val value = ids.removeAt(index); ids.add(index - 1, value); replace(week.copy(songIds = ids))
                                }
                            }) { Text("↑") }
                            TextButton(onClick = {
                                if (index < week.songIds.lastIndex) {
                                    val ids = week.songIds.toMutableList(); val value = ids.removeAt(index); ids.add(index + 1, value); replace(week.copy(songIds = ids))
                                }
                            }) { Text("↓") }
                            TextButton(onClick = { replace(week.copy(songIds = week.songIds - song.id)) }) { Text("Remover") }
                        }
                    }
                }
            }
        }
    }

    if (showNewSong) NewSongDialogV3({ showNewSong = false }) { song ->
        onChange(project.copy(
            songs = project.songs + song,
            weeks = project.weeks.map { if (it.id == week.id) it.copy(songIds = it.songIds + song.id) else it }
        ))
        showNewSong = false
        onSong(song.id)
    }
    if (showExisting) SongPickerDialogV3(project.songs.filterNot { it.id in week.songIds }, { showExisting = false }) { song ->
        replace(week.copy(songIds = week.songIds + song.id))
        showExisting = false
    }
}

@Composable
private fun FreeplayScreenV3(project: Project, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    val used = project.weeks.flatMap { it.songIds }.toSet()
    val songs = project.songs.filterNot { it.id in used }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeaderV3("Freeplay", "Músicas fora das Weeks", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Música Freeplay") } }
        if (songs.isEmpty()) item { EmptyCardV3("Nenhuma música Freeplay", "Crie uma música exclusiva.") }
        items(songs, key = { it.id }) { song ->
            Card(Modifier.fillMaxWidth().clickable { onSong(song.id) }) {
                Column(Modifier.padding(15.dp)) { Text(song.displayName, fontWeight = FontWeight.Bold); Text("${song.bpm} BPM • ID ${song.id}") }
            }
        }
    }
    if (showCreate) NewSongDialogV3({ showCreate = false }) { song ->
        onChange(project.copy(songs = project.songs + song))
        showCreate = false
        onSong(song.id)
    }
}

@Composable
private fun SongEditorScreenV3(project: Project, songId: String, store: ProjectStore, onBack: () -> Unit, onChange: (Project) -> Unit) {
    val song = project.songs.firstOrNull { it.id == songId } ?: return
    var name by remember(song.id, song.displayName) { mutableStateOf(song.displayName) }
    var bpmText by remember(song.id, song.bpm) { mutableStateOf(song.bpm.toString()) }
    var stageId by remember(song.id, song.stageId) { mutableStateOf(song.stageId ?: "stage") }
    var playerId by remember(song.id, song.playerId) { mutableStateOf(song.playerId ?: "bf") }
    var opponentId by remember(song.id, song.opponentId) { mutableStateOf(song.opponentId ?: "dad") }
    var gfId by remember(song.id, song.girlfriendId) { mutableStateOf(song.girlfriendId ?: "gf") }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val assets = project.assets.associateBy { it.id }

    fun replace(updated: Song, base: Project = project) {
        onChange(base.copy(songs = base.songs.map { if (it.id == song.id) updated else it }))
    }

    val instLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            replace(song.copy(instrumentalAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }
    val vocalsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            replace(song.copy(vocalsAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeaderV3(song.displayName, "Editor da música", onBack) }
        item {
            EditorCardV3("Dados") {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bpmText, { bpmText = it }, label = { Text("BPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                SelectorField("Stage", stageId, stageName(project, stageId), { picker = "stage" }, optional = true, onUseDefault = { stageId = "stage" })
                SelectorField("Boyfriend / Player", playerId, characterName(project, playerId), { picker = "player" })
                SelectorField("Opponent", opponentId, characterName(project, opponentId), { picker = "opponent" })
                SelectorField("Girlfriend", gfId, characterName(project, gfId), { picker = "gf" })
                Button(onClick = {
                    val bpm = bpmText.toDoubleOrNull()?.takeIf { it > 0 } ?: song.bpm
                    replace(song.copy(
                        displayName = name.trim().ifBlank { song.displayName },
                        bpm = bpm,
                        tempoMap = if (song.tempoMap.size == 1) listOf(TempoPoint(0.0, bpm)) else song.tempoMap,
                        stageId = stageId,
                        playerId = playerId,
                        opponentId = opponentId,
                        girlfriendId = gfId
                    ))
                }, modifier = Modifier.fillMaxWidth()) { Text("Salvar dados") }
            }
        }
        item {
            EditorCardV3("Áudio") {
                Text("Instrumental: ${song.instrumentalAssetId?.let { assets[it]?.relativePath } ?: "nenhum"}")
                Button(onClick = { instLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar / trocar Instrumental") }
                Text("Vocals: ${song.vocalsAssetId?.let { assets[it]?.relativePath } ?: "nenhum"}")
                OutlinedButton(onClick = { vocalsLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar / trocar Vocals") }
            }
        }
        item {
            EditorCardV3("Chart") {
                Text("${song.difficulties.sumOf { it.chart.notes.size }} notas")
                Text("O Chart Editor touch entra como o próximo editor grande.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    when (picker) {
        "stage" -> StagePickerDialog(project, store, stageId, { picker = null }) { stageId = it; picker = null }
        "player", "opponent", "gf" -> {
            val selected = when (picker) { "player" -> playerId; "opponent" -> opponentId; else -> gfId }
            CharacterPickerDialog(project, store, selected, { picker = null }) { id ->
                when (picker) { "player" -> playerId = id; "opponent" -> opponentId = id; "gf" -> gfId = id }
                picker = null
            }
        }
    }
}

@Composable
private fun AssetsScreenV3(project: Project, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StudioHeaderV3("Assets", "Arquivos importados", onBack) }
        if (project.assets.isEmpty()) item { EmptyCardV3("Nenhum asset", "Importe áudio ou imagens pelos editores.") }
        items(project.assets, key = { it.id }) { asset ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(asset.kind.name, fontWeight = FontWeight.Bold); Text(asset.relativePath) } }
        }
    }
}

@Composable
private fun SettingsScreenV3(project: Project, onBack: () -> Unit, onChange: (Project) -> Unit) {
    var name by remember(project.name) { mutableStateOf(project.name) }
    var author by remember(project.author) { mutableStateOf(project.author) }
    var description by remember(project.description) { mutableStateOf(project.description) }
    var version by remember(project.version) { mutableStateOf(project.version) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeaderV3("Configurações", "Metadata do projeto", onBack) }
        item {
            EditorCardV3("Mod") {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(version, { version = it }, label = { Text("Versão") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onChange(project.copy(name = name.trim().ifBlank { project.name }, author = author.trim(), description = description.trim(), version = version.trim().ifBlank { project.version })) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
            }
        }
    }
}

@Composable
private fun ExportScreenV3(project: Project, store: ProjectStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { PsychV1Exporter() }
    val service = remember(store) { ProjectExportService(context, store, exporter) }
    val bundle = remember(project) { exporter.export(project) }
    var status by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeaderV3("Exportar", "Psych Engine • psych_v1", onBack) }
        item { EditorCardV3("Pré-validação") { Text("${bundle.artifacts.size} arquivos"); Text("${bundle.warnings.size} aviso(s)") } }
        items(bundle.warnings) { warning -> Card(Modifier.fillMaxWidth()) { Text(warning, Modifier.padding(12.dp)) } }
        item {
            Button(onClick = {
                scope.launch {
                    runCatching { service.buildZip(project) }.onSuccess { result ->
                        status = "ZIP criado: ${result.artifactCount} arquivos"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, service.shareUri(result.file))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartilhar mod"))
                    }.onFailure { status = "Falha: ${it.message}" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Gerar ZIP e compartilhar") }
        }
        status?.let { item { Text(it) } }
    }
}

@Composable
internal fun StudioHeaderV3(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        TextButton(onClick = onBack) { Text("← Voltar") }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EditorCardV3(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
internal fun EmptyCardV3(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) } }
}

@Composable
private fun CreateProjectDialogV3(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Mod") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Nome") }); OutlinedTextField(author, { author = it }, label = { Text("Autor") }) } },
        confirmButton = { Button(onClick = { onCreate(name, author) }, enabled = name.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun SimpleIdDialogV3(title: String, label: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it; if (id.isBlank()) id = slugV3(it) }, label = { Text(label) }); OutlinedTextField(id, { id = slugV3(it) }, label = { Text("ID interno") }) } },
        confirmButton = { Button(onClick = { onCreate(name.trim(), slugV3(id)) }, enabled = name.isNotBlank() && id.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NewSongDialogV3(onDismiss: () -> Unit, onCreate: (Song) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var bpm by remember { mutableStateOf("120") }
    val parsed = bpm.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova música") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it; if (id.isBlank()) id = slugV3(it) }, label = { Text("Nome") }); OutlinedTextField(id, { id = slugV3(it) }, label = { Text("ID interno") }); OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } },
        confirmButton = { Button(onClick = { onCreate(Song(id = slugV3(id), displayName = name.trim(), bpm = parsed ?: 120.0)) }, enabled = name.isNotBlank() && id.isNotBlank() && parsed != null && parsed > 0) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SongPickerDialogV3(songs: List<Song>, onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar música") },
        text = {
            if (songs.isEmpty()) Text("Nenhuma música disponível") else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(songs, key = { it.id }) { song ->
                    Card(Modifier.fillMaxWidth().clickable { onPick(song) }) {
                        Column(Modifier.padding(10.dp)) { Text(song.displayName, fontWeight = FontWeight.Bold); Text("${song.bpm} BPM") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

private fun slugV3(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").replace(Regex("-+"), "-").trim('-').ifEmpty { "untitled" }
