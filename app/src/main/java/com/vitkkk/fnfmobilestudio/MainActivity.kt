package com.vitkkk.fnfmobilestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import com.vitkkk.fnfmobilestudio.ui.StudioApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectStore = ProjectStore(applicationContext)
        setContent {
            StudioApp(projectStore)
        }
    }
}
