package com.vitkkk.fnfmobilestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vitkkk.fnfmobilestudio.storage.ProjectStore
import com.vitkkk.fnfmobilestudio.ui.StudioAppV4

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectStore = ProjectStore(applicationContext)
        setContent {
            StudioAppV4(projectStore)
        }
    }
}
