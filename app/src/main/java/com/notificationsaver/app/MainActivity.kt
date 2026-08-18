package com.notificationsaver.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.notificationsaver.app.ui.AppRoot
import com.notificationsaver.app.ui.theme.NotificationSaverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationSaverTheme {
                AppRoot()
            }
        }
    }
}
