package com.webgenius.spark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.webgenius.spark.ui.SparkApp
import com.webgenius.spark.ui.SparkTheme

class MainActivity : ComponentActivity() {
    private val vm: SparkViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { SparkTheme { SparkApp(vm) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "spark" && uri.host == "auth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            if (code != null) vm.recovery(code)
        }
        // Never import access/refresh tokens from arbitrary deep-link fragments.
    }
}
