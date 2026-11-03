package me.kavishdevar.librepods.presentation.screens

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.ai.AirPodsAiWhispererEngine
import me.kavishdevar.librepods.presentation.components.StyledInputField
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledToggle

@Composable
fun AiWhispererScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val aiMasterEnabled = remember { mutableStateOf(prefs.getBoolean("ai_whisperer_enabled", false)) }
    val headChimeEnabled = remember { mutableStateOf(prefs.getBoolean("ai_head_chime_enabled", false)) }
    val selectedProvider = remember { mutableStateOf(prefs.getString("ai_whisperer_provider", "openrouter") ?: "openrouter") }

    val openrouterKeySaved = prefs.getString("ai_key_openrouter", "") ?: ""
    val openrouterInputState = remember { TextFieldState(openrouterKeySaved) }
    val openrouterFocusRequester = remember { FocusRequester() }

    val geminiKeySaved = prefs.getString("ai_key_gemini", "") ?: ""
    val geminiInputState = remember { TextFieldState(geminiKeySaved) }
    val geminiFocusRequester = remember { FocusRequester() }

    val nvidiaKeySaved = prefs.getString("ai_key_nvidia", "") ?: ""
    val nvidiaInputState = remember { TextFieldState(nvidiaKeySaved) }
    val nvidiaFocusRequester = remember { FocusRequester() }

    val testStatus = remember { mutableStateOf("") }
    val isTesting = remember { mutableStateOf(false) }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding, bottom = bottomPadding, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StyledList(title = "Master Control & Privacy") {
            StyledToggle(
                label = "AI Ear Whisperer Master",
                description = "When OFF, disables all microphone access, gesture listening, and background queries.",
                checked = aiMasterEnabled.value,
                onCheckedChange = { newVal ->
                    aiMasterEnabled.value = newVal
                    prefs.edit().putBoolean("ai_whisperer_enabled", newVal).apply()
                }
            )

            StyledToggle(
                label = "Head Gesture Chime",
                description = "Play subtle ear chime when head nodding/gestures occur. Turn OFF to silence completely.",
                checked = headChimeEnabled.value,
                onCheckedChange = { newVal ->
                    headChimeEnabled.value = newVal
                    prefs.edit().putBoolean("ai_head_chime_enabled", newVal).apply()
                }
            )
        }

        StyledList(title = "Active AI Provider (Instant Answers)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select which provider powers your ear whispers:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val p = selectedProvider.value
                    OutlinedButton(
                        onClick = {
                            selectedProvider.value = "openrouter"
                            prefs.edit().putString("ai_whisperer_provider", "openrouter").apply()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (p == "openrouter") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("OpenRouter", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            selectedProvider.value = "gemini"
                            prefs.edit().putString("ai_whisperer_provider", "gemini").apply()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (p == "gemini") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("Gemini", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            selectedProvider.value = "nvidia"
                            prefs.edit().putString("ai_whisperer_provider", "nvidia").apply()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (p == "nvidia") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text("NVIDIA", maxLines = 1)
                    }
                }
            }
        }

        StyledList(title = "OpenRouter (Recommended - 100% Free)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "High-speed free models with zero delay (openrouter.ai). Uses free fast meta-routing with instant answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StyledInputField(
                    inputState = openrouterInputState,
                    focusRequester = openrouterFocusRequester,
                    placeholder = "Paste OpenRouter Key (sk-or-v1-...)",
                    singleLine = true,
                    forceApple = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val k = openrouterInputState.text.toString().trim()
                            prefs.edit().putString("ai_key_openrouter", k).apply()
                            Toast.makeText(context, "OpenRouter Key Saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Key")
                    }

                    OutlinedButton(
                        onClick = {
                            val k = openrouterInputState.text.toString().trim()
                            if (k.isBlank()) {
                                testStatus.value = "Please enter OpenRouter key first"
                                return@OutlinedButton
                            }
                            isTesting.value = true
                            testStatus.value = "Testing OpenRouter connection..."
                            val engine = AirPodsAiWhispererEngine(context)
                            engine.testApiConnection("openrouter", k) { success, msg ->
                                Handler(Looper.getMainLooper()).post {
                                    isTesting.value = false
                                    testStatus.value = "OpenRouter: $msg"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting.value
                    ) {
                        Text("Test Key")
                    }
                }
            }
        }

        StyledList(title = "Google Gemini (100% Free Tier)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Free API key from Google AI Studio (aistudio.google.com). 1500 free requests/day at \$0.00.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StyledInputField(
                    inputState = geminiInputState,
                    focusRequester = geminiFocusRequester,
                    placeholder = "Paste Gemini Key (AIzaSy... or AQ...)",
                    singleLine = true,
                    forceApple = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val k = geminiInputState.text.toString().trim()
                            prefs.edit().putString("ai_key_gemini", k).apply()
                            Toast.makeText(context, "Gemini Key Saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Key")
                    }

                    OutlinedButton(
                        onClick = {
                            val k = geminiInputState.text.toString().trim()
                            if (k.isBlank()) {
                                testStatus.value = "Please enter Gemini key first"
                                return@OutlinedButton
                            }
                            isTesting.value = true
                            testStatus.value = "Testing Gemini connection..."
                            val engine = AirPodsAiWhispererEngine(context)
                            engine.testApiConnection("gemini", k) { success, msg ->
                                Handler(Looper.getMainLooper()).post {
                                    isTesting.value = false
                                    testStatus.value = "Gemini: $msg"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting.value
                    ) {
                        Text("Test Key")
                    }
                }
            }
        }

        StyledList(title = "NVIDIA NIM (Free Developer API)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Free API key from NVIDIA Build (build.nvidia.com). Runs fast hosted models (deepseek, granite, etc).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StyledInputField(
                    inputState = nvidiaInputState,
                    focusRequester = nvidiaFocusRequester,
                    placeholder = "Paste NVIDIA Key (nvapi-...)",
                    singleLine = true,
                    forceApple = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val k = nvidiaInputState.text.toString().trim()
                            prefs.edit().putString("ai_key_nvidia", k).apply()
                            Toast.makeText(context, "NVIDIA Key Saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Key")
                    }

                    OutlinedButton(
                        onClick = {
                            val k = nvidiaInputState.text.toString().trim()
                            if (k.isBlank()) {
                                testStatus.value = "Please enter NVIDIA key first"
                                return@OutlinedButton
                            }
                            isTesting.value = true
                            testStatus.value = "Testing NVIDIA connection..."
                            val engine = AirPodsAiWhispererEngine(context)
                            engine.testApiConnection("nvidia", k) { success, msg ->
                                Handler(Looper.getMainLooper()).post {
                                    isTesting.value = false
                                    testStatus.value = "NVIDIA: $msg"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting.value
                    ) {
                        Text("Test Key")
                    }
                }
            }
        }

        if (testStatus.value.isNotBlank()) {
            val isSuccess = testStatus.value.contains("Success")
            Text(
                text = testStatus.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
