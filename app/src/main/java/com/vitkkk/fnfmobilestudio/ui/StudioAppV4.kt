package com.vitkkk.fnfmobilestudio.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.AppConfig
import com.vitkkk.fnfmobilestudio.export.ProjectExportService
import com.vitkkk.fnfmobilestudio.export.PsychV1Exporter
import com.vitkkk.fnfmobilestudio.model.*
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch
import kotlin.math.max

private sealed interface StudioRouteV4 {
    data object Home : StudioRouteV4
    data object Dashboard : StudioRouteV4
    data object Weeks : StudioRouteV4
    data class WeekEditor(val id: String) : StudioRouteV4
    data object Freeplay : StudioRouteV4
    data class SongEditor(val id: String) : StudioRouteV4
    data class ChartEditor(val id: String) : StudioRouteV4
    data object Characters : StudioRouteV4
    data class CharacterEditor(val id: String) : StudioRouteV4
    data object Stages : StudioRouteV4
    data class StageEditor(val id: String) : StudioRouteV4
    data object Assets : StudioRouteV4
    data object Settings : StudioRouteV4
    data object Export : StudioRouteV4
}

@Composable
fun StudioAppV4(projectStore: ProjectStore) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var project by remember { mutableStateOf<Project?>(null) }
    var route by remember { mutableStateOf<StudioRouteV4>(StudioRouteV4.Home) }
    var createProject by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() { projects = projectStore.listProjects() }
    fun save(updated: Project) {
        project = updated
        projects = projects.map { if (it.id == updated.id) updated else it }
        scope.launch { projectStore.save(updated); reload() }
    }
    fun goHome() {
        project = null
        route = StudioRouteV4.Home
    }

    LaunchedEffect(Unit) { reload() }

    StudioTheme {
        Surface(Modifier.fillMaxSize()) {
            val active = project
            when {
                route == StudioRouteV4.Home || active == null -> HomeScreenV4(
                    projects = projects,
                    store = projectStore,
                    onNew = { createProject = true },
                    onOpen = { project = it; route = StudioRouteV4.Dashboard },
                    onDelete = { pendingDelete = it }
                )
                route == StudioRouteV4.Dashboard -> DashboardV4(active, ::goHome) { route = it }
                route == StudioRouteV4.Weeks -> WeeksV4(active, { route = StudioRouteV4.Dashboard }, { route = StudioRouteV4.WeekEditor(it) }, ::save)
                route is StudioRouteV4.WeekEditor -> WeekEditorV4(active, (route as StudioRouteV4.WeekEditor).id, { route = StudioRouteV4.Weeks }, { route = StudioRouteV4.SongEditor(it) }, ::save)
                route == StudioRouteV4.Freeplay -> FreeplayV4(active, { route = StudioRouteV4.Dashboard }, { route = StudioRouteV4.SongEditor(it) }, ::save)
                route is StudioRouteV4.SongEditor -> SongEditorV4(
                    active,
                    (route as StudioRouteV4.SongEditor).id,
                    projectStore,
                    onBack = { route = StudioRouteV4.Dashboard },
                    onChart = { route = StudioRouteV4.ChartEditor(it) },
                    onStage = { id -> route = if (active.stages.any { it.id == id }) StudioRouteV4.StageEditor(id) else StudioRouteV4.Stages },
                    onExport = { route = StudioRouteV4.Export },
                    onChange = ::save
                )
                route is StudioRouteV4.ChartEditor -> ChartEditorScreenV4(active, (route as StudioRouteV4.ChartEditor).id, projectStore, { route = StudioRouteV4.SongEditor((route as StudioRouteV4.ChartEditor).id) }, ::goHome, ::save)
                route == StudioRouteV4.Characters -> CharacterManagerV4(active, projectStore, { route = StudioRouteV4.Dashboard }, { route = StudioRouteV4.CharacterEditor(it) }, ::save)
                route is StudioRouteV4.CharacterEditor -> CharacterEditorScreenV4(active, (route as StudioRouteV4.CharacterEditor).id, projectStore, { route = StudioRouteV4.Characters }, ::goHome, ::save)
                route == StudioRouteV4.Stages -> StageManagerV4(active, projectStore, { route = StudioRouteV4.Dashboard }, { route = StudioRouteV4.StageEditor(it) }, ::save)
                route is StudioRouteV4.StageEditor -> StageEditorScreenV4(active, (route as StudioRouteV4.StageEditor).id, projectStore, { route = StudioRouteV4.Stages }, ::goHome, ::save)
                route == StudioRouteV4.Assets -> AssetsV4(active, projectStore) { route = StudioRouteV4.Dashboard }
                route == StudioRouteV4.Settings -> SettingsV4(active, { route = StudioRouteV4.Dashboard }, ::save)
                route == StudioRouteV4.Export -> ExportV4(active, projectStore) { route = StudioRouteV4.Dashboard }
            }
        }
    }

    if (createProject) {
        CreateProjectDialogV4({ createProject = false }) { name, author ->
            scope.launch {
                val created = projectStore.createProject(name, author)
                reload()
                project = created
                route = StudioRouteV4.Dashboard
                createProject = false
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir projeto?") },
            text = { Text("${target.name} e os arquivos internos serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { projectStore.delete(target.id); pendingDelete = null; reload() }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeScreenV4(
    projects: List<Project>,
    store: ProjectStore,
    onNew: () -> Unit,
    onOpen: (Project) -> Unit,
    onDelete: (Project) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember(projects) { mutableStateOf(projects.firstOrNull()?.id) }
    val visible = projects.filter { it.name.contains(query, true) || it.id.contains(query, true) }
    val selected = projects.firstOrNull { it.id == selectedId }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar(AppConfig.APP_NAME) {
                MakerSquareButton("＋", onNew, size = 54.dp, tone = MakerButtonTone.ADD)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filtro de projetos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MakerPalette.Button,
                    unfocusedContainerColor = MakerPalette.Button,
                    focusedTextColor = MakerPalette.ButtonText,
                    unfocusedTextColor = MakerPalette.ButtonText,
                    focusedLabelColor = MakerPalette.ButtonText,
                    unfocusedLabelColor = MakerPalette.ButtonText
                )
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        MakerPanel("Projetos", Modifier.fillMaxWidth()) {
                            Text("Nenhum projeto encontrado.", color = MakerPalette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
                        }
                    }
                }
                items(visible, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (item.id == selectedId) MakerPalette.Warning else MakerPalette.PanelAlt, RoundedCornerShape(8.dp))
                            .clickable { selectedId = item.id }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AssetThumbnail(item, item.iconAssetId, store, Modifier.size(58.dp), "MOD")
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = MakerPalette.White, fontWeight = FontWeight.Black)
                            Text("${item.weeks.size} weeks • ${item.songs.size} músicas", color = MakerPalette.Muted)
                            Text("ID ${item.id}", color = MakerPalette.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MakerButton("🗑", { selected?.let(onDelete) }, Modifier.weight(0.8f), MakerButtonTone.DANGER, enabled = selected != null)
                MakerButton("＋ NOVO PROJETO", onNew, Modifier.weight(1.5f), MakerButtonTone.ADD)
                MakerButton("✓ ABRIR", { selected?.let(onOpen) }, Modifier.weight(1.2f), MakerButtonTone.NORMAL, enabled = selected != null)
            }
        }
    }
}

@Composable
private fun DashboardV4(project: Project, onHome: () -> Unit, onNavigate: (StudioRouteV4) -> Unit) {
    val freeplay = project.songs.count { song -> project.weeks.none { song.id in it.songIds } }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar(project.name, onBack = onHome)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.Center
            ) {
                MakerPanel("Projeto • ${project.id}", Modifier.fillMaxWidth()) {
                    MakerMenuButtonV4("WEEKS", "${project.weeks.size}") { onNavigate(StudioRouteV4.Weeks) }
                    MakerMenuButtonV4("FREEPLAY", "$freeplay") { onNavigate(StudioRouteV4.Freeplay) }
                    MakerMenuButtonV4("PERSONAGENS", "${project.characters.size}") { onNavigate(StudioRouteV4.Characters) }
                    MakerMenuButtonV4("STAGES", "${project.stages.size}") { onNavigate(StudioRouteV4.Stages) }
                    MakerMenuButtonV4("ASSETS", "${project.assets.size}") { onNavigate(StudioRouteV4.Assets) }
                    MakerMenuButtonV4("CONFIGURAÇÕES", "⚙") { onNavigate(StudioRouteV4.Settings) }
                    MakerButton("EXPORTAR PARA PSYCH ENGINE", { onNavigate(StudioRouteV4.Export) }, Modifier.fillMaxWidth(), MakerButtonTone.ADD)
                }
            }
            MakerBottomHome(onHome)
        }
    }
}

@Composable
private fun MakerMenuButtonV4(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MakerPalette.PanelAlt, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MakerPalette.White, fontWeight = FontWeight.Black)
        Text(value, color = MakerPalette.Header, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WeeksV4(project: Project, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Weeks", onBack)
            MakerButton("＋ NOVA WEEK", { create = true }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), MakerButtonTone.ADD)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(project.weeks, key = { it.id }) { week ->
                    MakerMenuButtonV4(week.displayName, "${week.songIds.size} músicas") { onOpen(week.id) }
                }
            }
        }
    }
    if (create) SimpleIdDialogV4("Nova Week", "Nome da Week", { create = false }) { name, id ->
        onChange(project.copy(weeks = project.weeks + Week(id, id, name)))
        create = false
        onOpen(id)
    }
}

@Composable
private fun WeekEditorV4(project: Project, weekId: String, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    val week = project.weeks.firstOrNull { it.id == weekId } ?: return
    var addNew by remember { mutableStateOf(false) }
    var addExisting by remember { mutableStateOf(false) }
    val songs = project.songs.associateBy { it.id }
    fun replace(updated: Week) = onChange(project.copy(weeks = project.weeks.map { if (it.id == week.id) updated else it }))

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar(week.displayName, onBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MakerButton("＋ NOVA MÚSICA", { addNew = true }, Modifier.weight(1f), MakerButtonTone.ADD)
                MakerButton("EXISTENTE", { addExisting = true }, Modifier.weight(1f))
            }
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(week.songIds) { id ->
                    songs[id]?.let { song ->
                        val index = week.songIds.indexOf(id)
                        MakerPanel(null, Modifier.fillMaxWidth()) {
                            MakerMenuButtonV4("${index + 1}. ${song.displayName}", "${song.bpm.toInt()} BPM") { onSong(song.id) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                MakerSquareButton("▲", {
                                    if (index > 0) {
                                        val list = week.songIds.toMutableList(); val item = list.removeAt(index); list.add(index - 1, item); replace(week.copy(songIds = list))
                                    }
                                }, Modifier.weight(1f), size = 42.dp)
                                MakerSquareButton("▼", {
                                    if (index < week.songIds.lastIndex) {
                                        val list = week.songIds.toMutableList(); val item = list.removeAt(index); list.add(index + 1, item); replace(week.copy(songIds = list))
                                    }
                                }, Modifier.weight(1f), size = 42.dp)
                                MakerButton("REMOVER", { replace(week.copy(songIds = week.songIds - song.id)) }, Modifier.weight(2f), MakerButtonTone.DANGER)
                            }
                        }
                    }
                }
            }
        }
    }

    if (addNew) NewSongDialogV4({ addNew = false }) { song ->
        onChange(project.copy(songs = project.songs + song, weeks = project.weeks.map { if (it.id == week.id) it.copy(songIds = it.songIds + song.id) else it }))
        addNew = false
        onSong(song.id)
    }
    if (addExisting) SongPickerDialogV4(project.songs.filterNot { it.id in week.songIds }, { addExisting = false }) { song ->
        replace(week.copy(songIds = week.songIds + song.id)); addExisting = false
    }
}

@Composable
private fun FreeplayV4(project: Project, onBack: () -> Unit, onSong: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    val assigned = project.weeks.flatMap { it.songIds }.toSet()
    val songs = project.songs.filterNot { it.id in assigned }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Freeplay", onBack)
            MakerButton("＋ MÚSICA FREEPLAY", { create = true }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), MakerButtonTone.ADD)
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs, key = { it.id }) { song -> MakerMenuButtonV4(song.displayName, "${song.bpm.toInt()} BPM") { onSong(song.id) } }
            }
        }
    }
    if (create) NewSongDialogV4({ create = false }) { song ->
        onChange(project.copy(songs = project.songs + song)); create = false; onSong(song.id)
    }
}

@Composable
private fun SongEditorV4(
    project: Project,
    songId: String,
    store: ProjectStore,
    onBack: () -> Unit,
    onChart: (String) -> Unit,
    onStage: (String) -> Unit,
    onExport: () -> Unit,
    onChange: (Project) -> Unit
) {
    val song = project.songs.firstOrNull { it.id == songId } ?: return
    var name by remember(song.id, song.displayName) { mutableStateOf(song.displayName) }
    var bpm by remember(song.id, song.bpm) { mutableDoubleStateOf(song.bpm) }
    var stageId by remember(song.id, song.stageId) { mutableStateOf(song.stageId ?: "stage") }
    var playerId by remember(song.id, song.playerId) { mutableStateOf(song.playerId ?: "bf") }
    var opponentId by remember(song.id, song.opponentId) { mutableStateOf(song.opponentId ?: "dad") }
    var gfId by remember(song.id, song.girlfriendId) { mutableStateOf(song.girlfriendId ?: "gf") }
    var picker by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val assets = project.assets.associateBy { it.id }

    fun replaceSong(updated: Song, base: Project = project) {
        onChange(base.copy(songs = base.songs.map { if (it.id == song.id) updated else it }))
    }

    val instLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            replaceSong(song.copy(instrumentalAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }
    val vocalsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = store.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            replaceSong(song.copy(vocalsAssetId = asset.id), project.copy(assets = project.assets + asset))
        }
    }

    fun saveFields() {
        val updated = song.copy(
            displayName = name.trim().ifBlank { song.displayName },
            bpm = bpm,
            tempoMap = if (song.tempoMap.size == 1 && song.tempoMap.first().timeMs == 0.0) listOf(TempoPoint(0.0, bpm)) else song.tempoMap,
            stageId = stageId,
            playerId = playerId,
            opponentId = opponentId,
            girlfriendId = gfId
        )
        replaceSong(updated)
    }

    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar(song.displayName, onBack)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    MakerPanel("Passo 1 • Áudio", Modifier.fillMaxWidth()) {
                        MakerLabelValue("Instrumental", song.instrumentalAssetId?.let { assets[it]?.relativePath?.substringAfterLast('/') } ?: "nenhum")
                        MakerButton("📁 IMPORTAR / TROCAR INSTRUMENTAL", { instLauncher.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth())
                        MakerLabelValue("Vocals", song.vocalsAssetId?.let { assets[it]?.relativePath?.substringAfterLast('/') } ?: "nenhum")
                        MakerButton("📁 IMPORTAR / TROCAR VOCALS", { vocalsLauncher.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth())
                    }
                }
                item {
                    MakerPanel("Passo 2 • Valores", Modifier.fillMaxWidth()) {
                        OutlinedTextField(name, { name = it }, label = { Text("Nome da música") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        MakerValueStepper("BPM", bpm.toInt().toString(), { bpm = max(1.0, bpm - 1) }, { bpm += 1 }, bigArrows = true)
                        MakerLabelValue("Dificuldade", song.difficulties.firstOrNull()?.displayName ?: "Normal")
                    }
                }
                item {
                    MakerPanel("Passo 3 • Stage e personagens", Modifier.fillMaxWidth()) {
                        MakerSelectionRowV4("STAGE", stageName(project, stageId), stageId, project, project.stages.firstOrNull { it.id == stageId }?.previewAssetId, store) { picker = "stage" }
                        MakerSelectionRowV4("PLAYER", characterName(project, playerId), playerId, project, characterPreviewAssetId(project, playerId), store) { picker = "player" }
                        MakerSelectionRowV4("OPPONENT", characterName(project, opponentId), opponentId, project, characterPreviewAssetId(project, opponentId), store) { picker = "opponent" }
                        MakerSelectionRowV4("GIRLFRIEND", characterName(project, gfId), gfId, project, characterPreviewAssetId(project, gfId), store) { picker = "gf" }
                        MakerButton("SALVAR CONFIGURAÇÃO", { saveFields() }, Modifier.fillMaxWidth(), MakerButtonTone.ADD)
                    }
                }
                item {
                    MakerPanel("Passo 4 • Editar e testar", Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerButton("♫ CHART", { saveFields(); onChart(song.id) }, Modifier.weight(1f), MakerButtonTone.ACCENT)
                            MakerButton("▧ STAGE", { saveFields(); onStage(stageId) }, Modifier.weight(1f), MakerButtonTone.ACCENT)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerButton("▶ TESTAR", { saveFields(); onChart(song.id) }, Modifier.weight(1f))
                            MakerButton("⇩ EXPORTAR", { saveFields(); onExport() }, Modifier.weight(1f), MakerButtonTone.ADD)
                        }
                        Text("Eventos e câmeras temporárias ficam para uma etapa futura. O Chart já trabalha com notas e sustains.", color = MakerPalette.Muted)
                    }
                }
            }
        }
    }

    when (picker) {
        "stage" -> StagePickerDialog(project, store, stageId, { picker = null }) { stageId = it; picker = null }
        "player" -> CharacterPickerDialog(project, store, playerId, { picker = null }) { playerId = it; picker = null }
        "opponent" -> CharacterPickerDialog(project, store, opponentId, { picker = null }) { opponentId = it; picker = null }
        "gf" -> CharacterPickerDialog(project, store, gfId, { picker = null }) { gfId = it; picker = null }
    }
}

@Composable
private fun MakerSelectionRowV4(
    label: String,
    title: String,
    id: String,
    project: Project,
    imageAssetId: String?,
    store: ProjectStore,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MakerPalette.PanelAlt, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssetThumbnail(project, imageAssetId, store, Modifier.size(54.dp), label.take(2))
        Column(Modifier.weight(1f)) {
            Text(label, color = MakerPalette.Header, fontWeight = FontWeight.Black)
            Text(title, color = MakerPalette.White, fontWeight = FontWeight.Bold)
            Text("ID $id", color = MakerPalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("▶", color = MakerPalette.Button, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CharacterManagerV4(project: Project, store: ProjectStore, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Character Editor", onBack)
            MakerButton("＋ NOVO PERSONAGEM", { create = true }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), MakerButtonTone.ADD)
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(project.characters, key = { it.id }) { char ->
                    Row(
                        Modifier.fillMaxWidth().background(MakerPalette.Panel, RoundedCornerShape(8.dp)).clickable { onOpen(char.id) }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AssetThumbnail(project, char.iconAssetId ?: char.imageAssetId, store, Modifier.size(72.dp), "CH")
                        Column(Modifier.weight(1f)) {
                            Text(char.displayName, color = MakerPalette.White, fontWeight = FontWeight.Black)
                            Text("ID ${char.id}", color = MakerPalette.Muted)
                            Text("${char.animations.size} poses", color = MakerPalette.Header)
                        }
                    }
                }
            }
        }
    }
    if (create) SimpleIdDialogV4("Novo personagem", "Nome", { create = false }) { name, id ->
        onChange(project.copy(characters = project.characters + CharacterDefinition(id, name))); create = false; onOpen(id)
    }
}

@Composable
private fun StageManagerV4(project: Project, store: ProjectStore, onBack: () -> Unit, onOpen: (String) -> Unit, onChange: (Project) -> Unit) {
    var create by remember { mutableStateOf(false) }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Stage Editor", onBack)
            MakerButton("＋ NOVO STAGE", { create = true }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), MakerButtonTone.ADD)
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(project.stages, key = { it.id }) { stage ->
                    Row(
                        Modifier.fillMaxWidth().background(MakerPalette.Panel, RoundedCornerShape(8.dp)).clickable { onOpen(stage.id) }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AssetThumbnail(project, stage.previewAssetId, store, Modifier.size(86.dp), "ST")
                        Column(Modifier.weight(1f)) {
                            Text(stage.displayName, color = MakerPalette.White, fontWeight = FontWeight.Black)
                            Text("ID ${stage.id}", color = MakerPalette.Muted)
                            Text("${stage.objects.size} objetos • zoom ${"%.2f".format(stage.defaultZoom)}", color = MakerPalette.Header)
                        }
                    }
                }
            }
        }
    }
    if (create) SimpleIdDialogV4("Novo Stage", "Nome", { create = false }) { name, id ->
        onChange(project.copy(stages = project.stages + StageDefinition(id, name))); create = false; onOpen(id)
    }
}

@Composable
private fun AssetsV4(project: Project, store: ProjectStore, onBack: () -> Unit) {
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Assets", onBack)
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(project.assets, key = { it.id }) { asset ->
                    Row(
                        Modifier.fillMaxWidth().background(MakerPalette.Panel, RoundedCornerShape(7.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (asset.kind == AssetKind.IMAGE || asset.kind == AssetKind.FRAME) AssetThumbnail(project, asset.id, store, Modifier.size(54.dp), asset.kind.name.take(2))
                        Column(Modifier.weight(1f)) {
                            Text(asset.relativePath.substringAfterLast('/'), color = MakerPalette.White, fontWeight = FontWeight.Bold)
                            Text(asset.kind.name, color = MakerPalette.Header)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsV4(project: Project, onBack: () -> Unit, onChange: (Project) -> Unit) {
    var name by remember(project.id, project.name) { mutableStateOf(project.name) }
    var author by remember(project.id, project.author) { mutableStateOf(project.author) }
    var description by remember(project.id, project.description) { mutableStateOf(project.description) }
    var version by remember(project.id, project.version) { mutableStateOf(project.version) }
    var autosave by remember(project.id, project.settings.autosaveEnabled) { mutableStateOf(project.settings.autosaveEnabled) }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Configurações", onBack)
            MakerPanel("Projeto", Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(version, { version = it }, label = { Text("Versão") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AUTOSAVE", color = MakerPalette.White, fontWeight = FontWeight.Bold)
                    Switch(autosave, { autosave = it })
                }
                MakerButton("SALVAR", {
                    onChange(project.copy(name = name.trim().ifBlank { project.name }, author = author.trim(), description = description.trim(), version = version.trim().ifBlank { project.version }, settings = project.settings.copy(autosaveEnabled = autosave)))
                }, Modifier.fillMaxWidth(), MakerButtonTone.ADD)
            }
        }
    }
}

@Composable
private fun ExportV4(project: Project, store: ProjectStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { PsychV1Exporter() }
    val service = remember(store) { ProjectExportService(context, store, exporter) }
    val preview = remember(project) { exporter.export(project) }
    var status by remember { mutableStateOf<String?>(null) }
    MakerRoot {
        Column(Modifier.fillMaxSize()) {
            MakerTopBar("Exportar", onBack)
            MakerPanel("Psych Engine • psych_v1", Modifier.fillMaxWidth().padding(12.dp)) {
                MakerLabelValue("Arquivos", preview.artifacts.size.toString())
                MakerLabelValue("Avisos", preview.warnings.size.toString())
                preview.warnings.take(5).forEach { Text(it, color = MakerPalette.Warning) }
                MakerButton("⇩ GERAR ZIP E COMPARTILHAR", {
                    scope.launch {
                        runCatching { service.buildZip(project) }.onSuccess { result ->
                            status = "ZIP pronto • ${result.artifactCount} arquivos"
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, service.shareUri(result.file))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartilhar mod"))
                        }.onFailure { status = "Falha: ${it.message}" }
                    }
                }, Modifier.fillMaxWidth(), MakerButtonTone.ADD)
                status?.let { Text(it, color = MakerPalette.White) }
            }
        }
    }
}

@Composable
private fun CreateProjectDialogV4(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo projeto") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome do mod") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onCreate(name, author) }, enabled = name.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SimpleIdDialogV4(title: String, label: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = slugV4(value) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(id, { id = slugV4(it) }, label = { Text("ID interno") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), slugV4(id)) }, enabled = name.isNotBlank() && id.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NewSongDialogV4(onDismiss: () -> Unit, onCreate: (Song) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var bpm by remember { mutableStateOf("120") }
    val parsed = bpm.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova música") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = slugV4(value) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(id, { id = slugV4(it) }, label = { Text("ID interno") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onCreate(Song(slugV4(id), name.trim(), parsed ?: 120.0)) }, enabled = name.isNotBlank() && id.isNotBlank() && parsed != null && parsed > 0) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SongPickerDialogV4(songs: List<Song>, onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar música") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs, key = { it.id }) { song ->
                    Card(Modifier.fillMaxWidth().clickable { onPick(song) }) {
                        Column(Modifier.padding(10.dp)) { Text(song.displayName, fontWeight = FontWeight.Bold); Text("${song.bpm.toInt()} BPM") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

private fun slugV4(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").replace(Regex("-+"), "-").trim('-').ifBlank { "untitled" }
