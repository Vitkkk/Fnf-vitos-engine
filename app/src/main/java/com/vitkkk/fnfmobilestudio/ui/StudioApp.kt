package com.vitkkk.fnfmobilestudio.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitkkk.fnfmobilestudio.AppConfig
import com.vitkkk.fnfmobilestudio.export.ProjectExportService
import com.vitkkk.fnfmobilestudio.export.PsychV1Exporter
import com.vitkkk.fnfmobilestudio.model.AssetKind
import com.vitkkk.fnfmobilestudio.model.CharacterDefinition
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.model.Song
import com.vitkkk.fnfmobilestudio.model.StageDefinition
import com.vitkkk.fnfmobilestudio.model.TempoPoint
import com.vitkkk.fnfmobilestudio.model.Week
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch

private sealed interface StudioRoute {
    data object Home : StudioRoute
    data object Dashboard : StudioRoute
    data object Weeks : StudioRoute
    data class WeekEditor(val weekId: String) : StudioRoute
    data object Freeplay : StudioRoute
    data class SongEditor(val songId: String) : StudioRoute
    data object Characters : StudioRoute
    data object Stages : StudioRoute
    data object Assets : StudioRoute
    data object Settings : StudioRoute
    data object Export : StudioRoute
}

@Composable
fun StudioApp(projectStore: ProjectStore) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var project by remember { mutableStateOf<Project?>(null) }
    var route by remember { mutableStateOf<StudioRoute>(StudioRoute.Home) }
    var showCreateProject by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reloadProjects() {
        projects = projectStore.listProjects()
    }

    fun saveProject(updated: Project) {
        project = updated
        projects = projects.map { if (it.id == updated.id) updated else it }
        scope.launch {
            projectStore.save(updated)
            reloadProjects()
        }
    }

    LaunchedEffect(Unit) { reloadProjects() }

    StudioTheme {
        Surface(Modifier.fillMaxSize()) {
            val active = project
            when {
                route == StudioRoute.Home || active == null -> HomeScreen(
                    projects = projects,
                    onNewProject = { showCreateProject = true },
                    onOpen = {
                        project = it
                        route = StudioRoute.Dashboard
                    },
                    onDelete = { pendingDelete = it }
                )

                route == StudioRoute.Dashboard -> DashboardScreen(
                    project = active,
                    onBack = {
                        project = null
                        route = StudioRoute.Home
                    },
                    onNavigate = { route = it }
                )

                route == StudioRoute.Weeks -> WeeksScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard },
                    onOpenWeek = { route = StudioRoute.WeekEditor(it) },
                    onProjectChange = ::saveProject
                )

                route is StudioRoute.WeekEditor -> WeekEditorScreen(
                    project = active,
                    weekId = (route as StudioRoute.WeekEditor).weekId,
                    onBack = { route = StudioRoute.Weeks },
                    onOpenSong = { route = StudioRoute.SongEditor(it) },
                    onProjectChange = ::saveProject
                )

                route == StudioRoute.Freeplay -> FreeplayScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard },
                    onOpenSong = { route = StudioRoute.SongEditor(it) },
                    onProjectChange = ::saveProject
                )

                route is StudioRoute.SongEditor -> SongEditorScreen(
                    project = active,
                    songId = (route as StudioRoute.SongEditor).songId,
                    projectStore = projectStore,
                    onBack = {
                        route = if (active.weeks.any { (route as StudioRoute.SongEditor).songId in it.songIds }) {
                            StudioRoute.Weeks
                        } else StudioRoute.Freeplay
                    },
                    onProjectChange = ::saveProject
                )

                route == StudioRoute.Characters -> CharacterManagerScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard },
                    onProjectChange = ::saveProject
                )

                route == StudioRoute.Stages -> StageManagerScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard },
                    onProjectChange = ::saveProject
                )

                route == StudioRoute.Assets -> AssetsScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard }
                )

                route == StudioRoute.Settings -> SettingsScreen(
                    project = active,
                    onBack = { route = StudioRoute.Dashboard },
                    onProjectChange = ::saveProject
                )

                route == StudioRoute.Export -> ExportScreen(
                    project = active,
                    projectStore = projectStore,
                    onBack = { route = StudioRoute.Dashboard }
                )
            }
        }
    }

    if (showCreateProject) {
        CreateProjectDialog(
            onDismiss = { showCreateProject = false },
            onCreate = { name, author ->
                scope.launch {
                    val created = projectStore.createProject(name, author)
                    reloadProjects()
                    showCreateProject = false
                    project = created
                    route = StudioRoute.Dashboard
                }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir mod?") },
            text = { Text("O projeto '${target.name}' e seus arquivos internos serão removidos.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        projectStore.delete(target.id)
                        pendingDelete = null
                        reloadProjects()
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeScreen(
    projects: List<Project>,
    onNewProject: () -> Unit,
    onOpen: (Project) -> Unit,
    onDelete: (Project) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(14.dp)) }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(AppConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("MODS DE FNF • MOBILE-FIRST", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Crie o mod visualmente no celular e exporte para a Psych Engine.")
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onNewProject, modifier = Modifier.fillMaxWidth()) { Text("＋ Novo Mod") }
                }
            }
        }
        item {
            Text("Projetos recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (projects.isEmpty()) {
            item { EmptyCard("Nenhum projeto ainda", "Crie o primeiro mod para começar.") }
        }
        items(projects, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (item.author.isNotBlank()) Text("por ${item.author}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${item.weeks.size} Weeks  •  ${item.songs.size} músicas  •  ${item.characters.size} personagens")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(item) }) { Text("Abrir") }
                        OutlinedButton(onClick = { onDelete(item) }) { Text("Excluir") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DashboardScreen(project: Project, onBack: () -> Unit, onNavigate: (StudioRoute) -> Unit) {
    val freeplayCount = project.songs.count { song -> project.weeks.none { song.id in it.songIds } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StudioHeader(project.name, "${project.weeks.size} Weeks • ${project.songs.size} músicas", onBack) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Seu mod", style = MaterialTheme.typography.labelLarge)
                    Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Edite cada parte separadamente. Tudo é salvo no projeto interno.")
                }
            }
        }
        item {
            DashboardRow(
                left = Triple("Weeks", project.weeks.size, StudioRoute.Weeks),
                right = Triple("Freeplay", freeplayCount, StudioRoute.Freeplay),
                onNavigate = onNavigate
            )
        }
        item {
            DashboardRow(
                left = Triple("Personagens", project.characters.size, StudioRoute.Characters),
                right = Triple("Stages", project.stages.size, StudioRoute.Stages),
                onNavigate = onNavigate
            )
        }
        item {
            DashboardRow(
                left = Triple("Assets", project.assets.size, StudioRoute.Assets),
                right = Triple("Configurações", 0, StudioRoute.Settings),
                onNavigate = onNavigate
            )
        }
        item {
            Button(onClick = { onNavigate(StudioRoute.Export) }, modifier = Modifier.fillMaxWidth()) {
                Text("Exportar para Psych Engine")
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DashboardRow(
    left: Triple<String, Int, StudioRoute>,
    right: Triple<String, Int, StudioRoute>,
    onNavigate: (StudioRoute) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardCard(left.first, left.second, Modifier.weight(1f)) { onNavigate(left.third) }
        DashboardCard(right.first, right.second, Modifier.weight(1f)) { onNavigate(right.third) }
    }
}

@Composable
private fun DashboardCard(label: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (count > 0) Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            else Text("Abrir →", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeksScreen(
    project: Project,
    onBack: () -> Unit,
    onOpenWeek: (String) -> Unit,
    onProjectChange: (Project) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeader("Weeks", "Organize as músicas do Story Mode.", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Nova Week") } }
        if (project.weeks.isEmpty()) item { EmptyCard("Nenhuma Week", "Crie uma Week e adicione músicas em ordem.") }
        items(project.weeks, key = { it.id }) { week ->
            Card(Modifier.fillMaxWidth().clickable { onOpenWeek(week.id) }, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(week.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${week.songIds.size} músicas • ID ${week.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Editar ordem e músicas →", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (showCreate) NewWeekDialog(
        onDismiss = { showCreate = false },
        onCreate = { display, id ->
            val week = Week(id = id, internalName = id, displayName = display)
            onProjectChange(project.copy(weeks = project.weeks + week))
            showCreate = false
            onOpenWeek(week.id)
        }
    )
}

@Composable
private fun WeekEditorScreen(
    project: Project,
    weekId: String,
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val week = project.weeks.firstOrNull { it.id == weekId } ?: return
    var showCreateSong by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val songMap = project.songs.associateBy { it.id }

    fun replaceWeek(updated: Week) {
        onProjectChange(project.copy(weeks = project.weeks.map { if (it.id == week.id) updated else it }))
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeader(week.displayName, "Week • ${week.songIds.size} músicas", onBack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreateSong = true }, modifier = Modifier.weight(1f)) { Text("＋ Nova música") }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) { Text("Adicionar existente") }
            }
        }
        if (week.songIds.isEmpty()) item { EmptyCard("Week vazia", "Adicione a primeira música.") }
        items(week.songIds) { songId ->
            val song = songMap[songId]
            if (song != null) {
                val index = week.songIds.indexOf(songId)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${song.displayName}", fontWeight = FontWeight.Bold)
                        Text("${song.bpm} BPM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { onOpenSong(song.id) }) { Text("Editar") }
                            TextButton(onClick = {
                                if (index > 0) {
                                    val ids = week.songIds.toMutableList()
                                    val value = ids.removeAt(index)
                                    ids.add(index - 1, value)
                                    replaceWeek(week.copy(songIds = ids))
                                }
                            }) { Text("↑") }
                            TextButton(onClick = {
                                if (index < week.songIds.lastIndex) {
                                    val ids = week.songIds.toMutableList()
                                    val value = ids.removeAt(index)
                                    ids.add(index + 1, value)
                                    replaceWeek(week.copy(songIds = ids))
                                }
                            }) { Text("↓") }
                            TextButton(onClick = { replaceWeek(week.copy(songIds = week.songIds - song.id)) }) { Text("Remover") }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSong) NewSongDialog(
        onDismiss = { showCreateSong = false },
        onCreate = { song ->
            onProjectChange(
                project.copy(
                    songs = project.songs + song,
                    weeks = project.weeks.map { if (it.id == week.id) it.copy(songIds = it.songIds + song.id) else it }
                )
            )
            showCreateSong = false
            onOpenSong(song.id)
        }
    )

    if (showPicker) SongPickerDialog(
        songs = project.songs.filterNot { it.id in week.songIds },
        onDismiss = { showPicker = false },
        onPick = { song ->
            replaceWeek(week.copy(songIds = week.songIds + song.id))
            showPicker = false
        }
    )
}

@Composable
private fun FreeplayScreen(
    project: Project,
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onProjectChange: (Project) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    val assigned = project.weeks.flatMap { it.songIds }.toSet()
    val songs = project.songs.filterNot { it.id in assigned }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeader("Freeplay", "Músicas que não pertencem a uma Week.", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar música Freeplay") } }
        if (songs.isEmpty()) item { EmptyCard("Nenhuma música exclusiva", "Crie uma música aqui ou remova uma música de uma Week.") }
        items(songs, key = { it.id }) { song ->
            Card(Modifier.fillMaxWidth().clickable { onOpenSong(song.id) }, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(song.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${song.bpm} BPM • ${song.difficulties.size} dificuldade(s)")
                    Text("Editar música →", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (showCreate) NewSongDialog(
        onDismiss = { showCreate = false },
        onCreate = { song ->
            onProjectChange(project.copy(songs = project.songs + song))
            showCreate = false
            onOpenSong(song.id)
        }
    )
}

@Composable
private fun SongEditorScreen(
    project: Project,
    songId: String,
    projectStore: ProjectStore,
    onBack: () -> Unit,
    onProjectChange: (Project) -> Unit
) {
    val song = project.songs.firstOrNull { it.id == songId } ?: return
    var name by remember(song.id, song.displayName) { mutableStateOf(song.displayName) }
    var bpmText by remember(song.id, song.bpm) { mutableStateOf(song.bpm.toString()) }
    var stage by remember(song.id, song.stageId) { mutableStateOf(song.stageId.orEmpty()) }
    var player by remember(song.id, song.playerId) { mutableStateOf(song.playerId.orEmpty()) }
    var opponent by remember(song.id, song.opponentId) { mutableStateOf(song.opponentId.orEmpty()) }
    var girlfriend by remember(song.id, song.girlfriendId) { mutableStateOf(song.girlfriendId.orEmpty()) }
    val scope = rememberCoroutineScope()
    val assetsById = project.assets.associateBy { it.id }

    fun replaceSong(updated: Song, updatedProject: Project = project) {
        onProjectChange(updatedProject.copy(songs = updatedProject.songs.map { if (it.id == song.id) updated else it }))
    }

    val instLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = projectStore.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            val updatedProject = project.copy(assets = project.assets + asset)
            replaceSong(song.copy(instrumentalAssetId = asset.id), updatedProject)
        }
    }
    val vocalsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val asset = projectStore.importAsset(project.id, uri, AssetKind.AUDIO, "audio")
            val updatedProject = project.copy(assets = project.assets + asset)
            replaceSong(song.copy(vocalsAssetId = asset.id), updatedProject)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeader(song.displayName, "Editor da música", onBack) }
        item {
            EditorCard("Dados") {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    bpmText,
                    { bpmText = it },
                    label = { Text("BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(stage, { stage = it }, label = { Text("Stage ID (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(player, { player = it }, label = { Text("Player ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(opponent, { opponent = it }, label = { Text("Opponent ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(girlfriend, { girlfriend = it }, label = { Text("Girlfriend ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val bpm = bpmText.toDoubleOrNull()?.takeIf { it > 0 } ?: song.bpm
                        replaceSong(
                            song.copy(
                                displayName = name.trim().ifBlank { song.displayName },
                                bpm = bpm,
                                tempoMap = song.tempoMap.let { map ->
                                    if (map.size == 1 && map.first().timeMs == 0.0) listOf(TempoPoint(0.0, bpm)) else map
                                },
                                stageId = stage.trim().ifBlank { null },
                                playerId = player.trim().ifBlank { null },
                                opponentId = opponent.trim().ifBlank { null },
                                girlfriendId = girlfriend.trim().ifBlank { null }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar dados") }
            }
        }
        item {
            EditorCard("Áudio") {
                Text("Instrumental: ${song.instrumentalAssetId?.let { assetsById[it]?.relativePath } ?: "nenhum"}")
                Button(onClick = { instLauncher.launch(arrayOf("audio/ogg", "audio/mpeg", "audio/wav", "audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar / trocar Instrumental")
                }
                Text("Vocals: ${song.vocalsAssetId?.let { assetsById[it]?.relativePath } ?: "nenhum"}")
                OutlinedButton(onClick = { vocalsLauncher.launch(arrayOf("audio/ogg", "audio/mpeg", "audio/wav", "audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar / trocar Vocals")
                }
                Text("OGG já pode ser materializado no ZIP. MP3/WAV ficam preservados no projeto e o export mostra aviso até o transcoder OGG entrar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            EditorCard("Chart") {
                Text("${song.difficulties.sumOf { it.chart.notes.size }} notas no projeto")
                Text("O renderer touch completo do Chart Editor é o próximo editor grande; o modelo de chart e o exporter já estão ativos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CharacterManagerScreen(project: Project, onBack: () -> Unit, onProjectChange: (Project) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeader("Personagens", "Catálogo global do projeto.", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar personagem") } }
        if (project.characters.isEmpty()) item { EmptyCard("Nenhum personagem", "Crie as definições agora; animações e atlas entram no Character Editor visual.") }
        items(project.characters, key = { it.id }) { character ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(character.displayName, fontWeight = FontWeight.Bold)
                    Text("ID ${character.id} • ${character.animations.size} animações", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showCreate) SimpleIdDialog(
        title = "Novo personagem",
        nameLabel = "Nome",
        onDismiss = { showCreate = false },
        onCreate = { name, id ->
            onProjectChange(project.copy(characters = project.characters + CharacterDefinition(id = id, displayName = name)))
            showCreate = false
        }
    )
}

@Composable
private fun StageManagerScreen(project: Project, onBack: () -> Unit, onProjectChange: (Project) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeader("Stages", "Cenários do projeto.", onBack) }
        item { Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Criar Stage") } }
        if (project.stages.isEmpty()) item { EmptyCard("Nenhum Stage", "Crie um Stage; o editor visual de posicionamento será construído sobre ele.") }
        items(project.stages, key = { it.id }) { stage ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(stage.displayName, fontWeight = FontWeight.Bold)
                    Text("ID ${stage.id} • ${stage.objects.size} objetos • zoom ${stage.defaultZoom}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showCreate) SimpleIdDialog(
        title = "Novo Stage",
        nameLabel = "Nome",
        onDismiss = { showCreate = false },
        onCreate = { name, id ->
            onProjectChange(project.copy(stages = project.stages + StageDefinition(id = id, displayName = name)))
            showCreate = false
        }
    )
}

@Composable
private fun AssetsScreen(project: Project, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StudioHeader("Assets", "Arquivos importados para o projeto.", onBack) }
        if (project.assets.isEmpty()) item { EmptyCard("Nenhum asset", "Importe Instrumental/Vocals no editor de uma música. Imagens entram nos editores visuais.") }
        items(project.assets, key = { it.id }) { asset ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(asset.kind.name, fontWeight = FontWeight.Bold)
                    Text(asset.relativePath, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(project: Project, onBack: () -> Unit, onProjectChange: (Project) -> Unit) {
    var name by remember(project.id, project.name) { mutableStateOf(project.name) }
    var author by remember(project.id, project.author) { mutableStateOf(project.author) }
    var description by remember(project.id, project.description) { mutableStateOf(project.description) }
    var version by remember(project.id, project.version) { mutableStateOf(project.version) }
    var autosave by remember(project.id, project.settings.autosaveEnabled) { mutableStateOf(project.settings.autosaveEnabled) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeader("Configurações", "Metadata e comportamento do projeto.", onBack) }
        item {
            EditorCard("Mod") {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(version, { version = it }, label = { Text("Versão") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Autosave", fontWeight = FontWeight.Bold)
                        Text("Salvar alterações automaticamente", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autosave, onCheckedChange = { autosave = it })
                }
                Text("Perfil de exportação: Psych Engine • psych_v1", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ID interno: ${project.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        onProjectChange(
                            project.copy(
                                name = name.trim().ifBlank { project.name },
                                author = author.trim(),
                                description = description.trim(),
                                version = version.trim().ifBlank { project.version },
                                settings = project.settings.copy(autosaveEnabled = autosave)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar configurações") }
            }
        }
    }
}

@Composable
private fun ExportScreen(project: Project, projectStore: ProjectStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { PsychV1Exporter() }
    val service = remember(projectStore) { ProjectExportService(context, projectStore, exporter) }
    val preview = remember(project) { exporter.export(project) }
    var status by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StudioHeader("Exportar", "Psych Engine • psych_v1", onBack) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (preview.warnings.isEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pré-validação", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${preview.artifacts.size} arquivos serão materializados")
                    Text("${project.weeks.size} Weeks • ${project.songs.size} músicas • ${project.stages.size} stages")
                    if (preview.warnings.isEmpty()) Text("Sem avisos estruturais.", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        if (preview.warnings.isNotEmpty()) {
            item { Text("Avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(preview.warnings) { warning ->
                Card(Modifier.fillMaxWidth()) { Text(warning, Modifier.padding(13.dp)) }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { service.buildZip(project) }
                            .onSuccess { result ->
                                status = "ZIP criado: ${result.artifactCount} arquivos${if (result.warnings.isNotEmpty()) " • ${result.warnings.size} aviso(s)" else ""}"
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, service.shareUri(result.file))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Compartilhar mod"))
                            }
                            .onFailure { status = "Falha ao exportar: ${it.message}" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Gerar ZIP e compartilhar") }
        }
        status?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Text(
                "Músicas exclusivas de Freeplay recebem automaticamente uma Week técnica oculta do Story Mode no export. Você não precisa criar essa Week manualmente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudioHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        TextButton(onClick = onBack) { Text("← Voltar") }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCard(title: String, text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorCard(title: String, content: @Composable Column.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Mod") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome do Mod") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, author) }, enabled = name.isNotBlank()) { Text("Criar projeto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NewWeekDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Week") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = slug(value) }, label = { Text("Nome exibido") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(id, { id = slug(it) }, label = { Text("ID interno") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onCreate(name.trim(), slug(id)) }, enabled = name.isNotBlank() && id.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NewSongDialog(onDismiss: () -> Unit, onCreate: (Song) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var bpm by remember { mutableStateOf("120") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova música") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = slug(value) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(id, { id = slug(it) }, label = { Text("ID interno") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    bpm,
                    { bpm = it },
                    label = { Text("BPM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val parsed = bpm.toDoubleOrNull()
            Button(onClick = { onCreate(Song(id = slug(id), displayName = name.trim(), bpm = parsed ?: 120.0)) }, enabled = name.isNotBlank() && id.isNotBlank() && parsed != null && parsed > 0) {
                Text("Criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SimpleIdDialog(title: String, nameLabel: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { value -> name = value; if (id.isBlank()) id = slug(value) }, label = { Text(nameLabel) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(id, { id = slug(it) }, label = { Text("ID interno") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onCreate(name.trim(), slug(id)) }, enabled = name.isNotBlank() && id.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SongPickerDialog(songs: List<Song>, onDismiss: () -> Unit, onPick: (Song) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar música") },
        text = {
            if (songs.isEmpty()) Text("Não há outras músicas disponíveis.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs, key = { it.id }) { song ->
                    Card(Modifier.fillMaxWidth().clickable { onPick(song) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(song.displayName, fontWeight = FontWeight.Bold)
                            Text("${song.bpm} BPM")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

private fun slug(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_-]+"), "-")
    .replace(Regex("-+"), "-")
    .trim('-')
    .ifEmpty { "untitled" }
