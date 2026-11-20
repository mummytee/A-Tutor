package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ThreeDPresets
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Interactive3DModelStudio(
    scene3dJson: String,
    subject: String,
    onSelectPreset: (String) -> Unit,
    onAskAiBuild3D: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveJson = remember(scene3dJson, subject) {
        if (scene3dJson.isNotBlank()) {
            scene3dJson
        } else {
            val matched = ThreeDPresets.presets.firstOrNull {
                it.subject.equals(subject, ignoreCase = true)
            } ?: ThreeDPresets.presets.first()
            ThreeDPresets.toJson(matched.spec)
        }
    }

    var isStudioReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastInjectedJson by remember { mutableStateOf("") }
    var showCustom3DDialog by remember { mutableStateOf(false) }
    var customPrompt by remember { mutableStateOf("") }

    LaunchedEffect(effectiveJson, isStudioReady, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isStudioReady && effectiveJson != lastInjectedJson) {
            lastInjectedJson = effectiveJson
            val quoted = JSONObject.quote(effectiveJson)
            wv.evaluateJavascript("window.loadSceneSpec($quoted);", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface("AndroidBridge")
                destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E221F))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        customPrompt = ""
                        showCustom3DDialog = true
                    },
                    modifier = Modifier.testTag("ai_build_3d_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ask AI for 3D", style = MaterialTheme.typography.labelMedium)
                }

                ThreeDPresets.presets.forEach { preset ->
                    val presetJson = remember(preset.id) { ThreeDPresets.toJson(preset.spec) }
                    val isSelected = effectiveJson.contains(preset.spec.title)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(presetJson) },
                        leadingIcon = {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("preset_3d_${preset.id}")
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("three_d_webview"),
                factory = {
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#0E221F"))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onStudioReady() {
                                    post {
                                        isStudioReady = true
                                    }
                                }

                                @JavascriptInterface
                                fun onPartSelected(label: String, description: String) {
                                    // Handled natively in HTML HUD inspector pill
                                }
                            },
                            "AndroidBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isStudioReady = true
                            }
                        }

                        webViewRef = this
                        loadUrl("file:///android_asset/three_studio.html")
                    }
                }
            )
        }
    }

    if (showCustom3DDialog) {
        AlertDialog(
            onDismissRequest = { showCustom3DDialog = false },
            title = { Text("Generate 3D Interactive Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Describe any 3D scientific, mathematical, or engineering structure and A-Tutor will build it interactively in 3D space.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        label = { Text("3D Model Prompt") },
                        placeholder = { Text("e.g. Water molecule H2O with bond angle, or Rocket turbine") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_3d_prompt_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customPrompt.trim()
                        if (trimmed.isNotEmpty()) {
                            onAskAiBuild3D("Build an interactive 3D model on the 3D studio and explain its parts: $trimmed")
                        }
                        showCustom3DDialog = false
                    },
                    enabled = customPrompt.isNotBlank(),
                    modifier = Modifier.testTag("confirm_custom_3d_btn")
                ) {
                    Text("Build 3D Model")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustom3DDialog = false }) { Text("Cancel") }
            }
        )
    }
}
