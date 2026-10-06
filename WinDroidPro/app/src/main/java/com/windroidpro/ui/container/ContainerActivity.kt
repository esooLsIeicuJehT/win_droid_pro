package com.windroidpro.ui.container

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.windroidpro.ui.theme.WinDroidProTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ContainerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("ContainerActivity created")

        setContent {
            WinDroidProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContainerScreen(
                        onBackClick = { finish() },
                        onContainerClick = { container ->
                            Timber.w(
                                "Launch requested for %s, but the production Wine/Box64 runtime launcher is not integrated",
                                container.name
                            )
                            Toast.makeText(
                                this@ContainerActivity,
                                "Runtime launch is blocked until the real Wine/Box64 payload and launcher are installed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }
}
