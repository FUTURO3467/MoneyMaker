package com.example.moneymaker

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.moneymaker.ui.theme.MoneyMakerTheme
import androidx.core.net.toUri


class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, FloatingService::class.java)

                intent.putExtra("resultCode", result.resultCode)
                intent.putExtra("data", result.data)
                intent.putExtra("height", windowManager.defaultDisplay.height)
                intent.putExtra("width", windowManager.defaultDisplay.width)

                ContextCompat.startForegroundService(this, intent)
            } else {
                println("Permission refusée")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        AutoService.instance = AutoService()
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingService::class.java))
        }
        enableEdgeToEdge()
        setContent {
            MoneyMakerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(name = "Android", modifier = Modifier.padding(innerPadding))
                    FilledButtonExample(onClick = fun(): Unit { },modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Composable
fun FilledButtonExample(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = { onClick() }, modifier=modifier, content = {
        Text("Filled")
    })
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MoneyMakerTheme {
        Greeting("Android")
    }
}

