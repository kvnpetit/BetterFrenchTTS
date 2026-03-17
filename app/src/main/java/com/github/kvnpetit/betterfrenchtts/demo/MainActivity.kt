package com.github.kvnpetit.betterfrenchtts.demo

import android.os.Bundle
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import com.github.kvnpetit.betterfrenchtts.SpeechPreset
import com.github.kvnpetit.betterfrenchtts.WordHighlight
import com.github.kvnpetit.betterfrenchtts.demo.ui.theme.BetterFrenchTTSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetterFrenchTTSTheme {
                DemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Initialisation...") }
    var voiceInfo by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("Bonjour, bienvenue dans BetterFrenchTTS.") }
    var ssmlPreview by remember { mutableStateOf("") }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedAudioFocus by remember { mutableStateOf(BetterFrenchTts.AudioFocusMode.DUCK) }
    var ttsInstance by remember { mutableStateOf<BetterFrenchTts?>(null) }
    var highlight by remember { mutableStateOf<WordHighlight?>(null) }

    fun createTts(audioFocus: BetterFrenchTts.AudioFocusMode): BetterFrenchTts {
        return BetterFrenchTts(context, BetterFrenchTts.Config(
            audioFocus = audioFocus,
            onReady = { instance ->
                status = "Prêt (focus: ${audioFocus.name})"
                voiceInfo = "Voix : ${instance.currentVoice?.name ?: "par défaut"}"
                availableVoices = instance.listAvailableVoices()
            },
            onInitError = { code ->
                status = "Erreur init TTS (code $code)"
            }
        )).onStart {
            status = "Lecture en cours... (focus: ${audioFocus.name})"
        }.onDone {
            status = "Prêt (focus: ${audioFocus.name})"
        }.onError { error ->
            status = "Erreur : $error"
        }.onWordHighlight { wh ->
            highlight = if (wh.start >= 0) wh else null
        }
    }

    if (ttsInstance == null) {
        ttsInstance = createTts(selectedAudioFocus)
    }

    val tts = ttsInstance!!

    DisposableEffect(Unit) {
        onDispose { ttsInstance?.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BetterFrenchTTS Demo") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = status, style = MaterialTheme.typography.bodyMedium)
            if (voiceInfo.isNotEmpty()) {
                Text(text = voiceInfo, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Texte à prononcer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { tts.speak(inputText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Parler")
                }

                Button(
                    onClick = {
                        tts.speak {
                            spellOut(inputText)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Épeler")
                }
            }

            // --- Word highlighting ---
            val h = highlight
            if (h != null && h.start >= 0 && h.start < inputText.length && h.end <= inputText.length) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(inputText.substring(0, h.start))
                            withStyle(SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )) {
                                append(inputText.substring(h.start, h.end))
                            }
                            append(inputText.substring(h.end))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { tts.stop() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Built-in presets ---
            Text("Presets", style = MaterialTheme.typography.titleMedium)

            SpeechPreset.builtIn.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { preset ->
                        FilledTonalButton(
                            onClick = { tts.speak(inputText, preset = preset) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.name, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Preset personnalisé", style = MaterialTheme.typography.titleSmall)

            FilledTonalButton(
                onClick = {
                    val myPreset = SpeechPreset(
                        name = "Mon preset",
                        rate = "80%",
                        pitch = "+5st",
                        volume = "loud"
                    )
                    tts.speak(inputText, preset = myPreset)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preset personnalisé (80%, +5st, loud)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- DSL demos ---
            Text("Démos DSL", style = MaterialTheme.typography.titleMedium)

            ElevatedButton(
                onClick = {
                    tts.speak {
                        text("La présentation générale est prête.")
                        pause(400)
                        slow {
                            text("Vérifions les résultats à tête reposée.")
                        }
                        pause(300)
                        fast {
                            text("Dépêchez-vous, l'événement a déjà commencé !")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Débit variable")
            }

            ElevatedButton(
                onClick = {
                    tts.speak {
                        soft {
                            text("Le bébé s'est endormi près de la fenêtre.")
                        }
                        pause(500)
                        loud {
                            text("Réveillez-vous, c'est l'heure de la fête !")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volume variable")
            }

            ElevatedButton(
                onClick = {
                    tts.speak {
                        text("Mon identifiant est ")
                        spellOut("café@résé")
                        pause(300)
                        text(". Appelez le ")
                        telephone("01 23 45 67 89")
                        text(".")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Épeler et téléphone")
            }

            ElevatedButton(
                onClick = {
                    tts.speak {
                        text("Il y a ")
                        number("1500")
                        text(" participants. C'est le ")
                        ordinal("3")
                        text(" événement cette année.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nombres et ordinaux")
            }

            ElevatedButton(
                onClick = {
                    tts.speak {
                        paragraph {
                            sentence {
                                text("Première phrase du paragraphe.")
                            }
                            sentence {
                                strong {
                                    text("Deuxième phrase avec emphase forte.")
                                }
                            }
                        }
                        pause(600)
                        paragraph {
                            sentence {
                                lowPitch {
                                    text("Un nouveau paragraphe, ton grave.")
                                }
                            }
                            sentence {
                                pitch(semitones = 5) {
                                    text("Et celui-ci en ton aigu.")
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Paragraphes, emphase et pitch")
            }

            ElevatedButton(
                onClick = {
                    tts.speakWithPreset(SpeechPreset.STORYTELLING) {
                        text("Il était une fois, dans un royaume lointain,")
                        pause(500)
                        emphasis {
                            text("un dragon magnifique")
                        }
                        text(" qui gardait un trésor inestimable.")
                        pause(700)
                        soft {
                            text("Mais personne n'osait s'en approcher.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preset + DSL combiné")
            }

            ElevatedButton(
                onClick = {
                    tts.speak {
                        withPreset(SpeechPreset.CALM) {
                            text("La journée a été très agréable.")
                        }
                        pause(400)
                        withPreset(SpeechPreset.EXCITED) {
                            text("Quelle réussite inespérée !")
                        }
                        pause(400)
                        withPreset(SpeechPreset.WHISPER) {
                            text("C'est un mystère bien gardé.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enchaîner les presets dans le DSL")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- SSML Preview ---
            Text("Preview SSML", style = MaterialTheme.typography.titleMedium)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        ssmlPreview = tts.buildSsml(inputText)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Neutre", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        ssmlPreview = tts.buildSsml(inputText, SpeechPreset.STORYTELLING)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Storytelling", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        ssmlPreview = tts.buildSsml {
                            text("Bonjour.")
                            pause(400)
                            slow { text("Lent.") }
                            emphasis { text("Important !") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("DSL", maxLines = 1)
                }
            }

            if (ssmlPreview.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ssmlPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Audio Focus ---
            Text("Audio Focus", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lancez de la musique puis parlez pour tester l'effet.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BetterFrenchTts.AudioFocusMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedAudioFocus == mode,
                        onClick = {
                            if (selectedAudioFocus != mode) {
                                ttsInstance?.shutdown()
                                selectedAudioFocus = mode
                                ttsInstance = createTts(mode)
                            }
                        },
                        label = { Text(mode.name, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Voice selection ---
            Text("Voix", style = MaterialTheme.typography.titleMedium)

            ElevatedButton(
                onClick = {
                    availableVoices = tts.listAvailableVoices()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rafraîchir les voix FR")
            }

            if (availableVoices.isNotEmpty()) {
                availableVoices.forEach { voice ->
                    val isSelected = voice.name == tts.currentVoice?.name
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            tts.setVoice(voice)
                            voiceInfo = "Voix : ${voice.name}"
                        },
                        label = {
                            Text(
                                "${voice.name} (q:${voice.quality})",
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
