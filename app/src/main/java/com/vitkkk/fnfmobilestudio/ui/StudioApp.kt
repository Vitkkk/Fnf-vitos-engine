package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.vitkkk.fnfmobilestudio.AppConfig
import com.vitkkk.fnfmobilestudio.model.Project
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import kotlinx.coroutines.launch

@Composable
fun StudioApp(projectStore: ProjectStore) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        projects = projectStore.listProjects()
    }

    LaunchedEffect(Unit) { reload() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (selectedProject == null) {
                HomeScreen(
                    projects = projects,
                    onNewProject = { showCreate = true },
                    onOpen = { selectedProject = it },
                    onDelete = { pendingDelete = it }
                )
            } else {
                Dashboard(
                    project = selectedProject!!,
                    onBack = { selectedProject = null }
                )
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, author ->
                scope.launch {
                    val project = projectStore.createProject(name, author)
                    reload()
                    showCreate = false
                    selectedProject = project
                }
            }
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir mod?") },
            text = { Text("O projeto '${project.name}' será removido do armazenamento interno do app.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        projectStore.delete(project.id)
                        pendingDelete = null
                        reload()
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
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
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(AppConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium)
                Text("Crie mods de FNF no celular sem programar.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNewProject, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Novo Mod")
                }
                Spacer(Modifier.height(12.dp))
                Text("Projetos", style = MaterialTheme.typography.titleLarge)
            }

            if (projects.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Nenhum projeto ainda. Crie o primeiro mod para começar.", Modifier.padding(18.dp))
                    }
                }
            }

            items(projects, key = { it.id }) { project ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        if (project.author.isNotBlank()) Text("por ${project.author}")
                        Text("${project.weeks.size} Weeks • ${project.songs.size} músicas • ${project.characters.size} personagens • ${project.stages.size} stages")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(project) }) { Text("Abrir") }
                            OutlinedButton(onClick = { onDelete(project) }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(project: Project, onBack: () -> Unit) {
    val entries = listOf(
        "Weeks" to project.weeks.size,
        "Freeplay" to project.songs.count { song -> project.weeks.none { song.id in it.songIds } },
        "Personagens" to project.characters.size,
        "Stages" to project.stages.size,
        "Assets" to project.assets.size
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Projetos") }
            Text(project.name, style = MaterialTheme.typography.headlineMedium)
            Text("${project.weeks.size} Weeks • ${project.songs.size} músicas")
        }
        items(entries) { (label, count) ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(count.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Exportar") }
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Configurações") }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Mod") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Mod") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Autor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, author) }, enabled = name.isNotBlank()) {
                Text("Criar projeto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
