package de.carcophan.snorerec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.carcophan.snorerec.ui.SnoreAppScreen
import de.carcophan.snorerec.ui.theme.SnoreRecTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnoreRecTheme {
                SnoreAppScreen()
            }
        }
    }
}
