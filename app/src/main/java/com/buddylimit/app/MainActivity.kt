package com.buddylimit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buddylimit.app.ui.apps.AppListScreen
import com.buddylimit.app.ui.theme.BuddyLimitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddyLimitTheme {
                AppListScreen()
            }
        }
    }
}
