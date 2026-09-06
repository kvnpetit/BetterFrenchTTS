package io.github.kvnpetit.betterfrenchtts.demo

import android.os.Bundle
import android.speech.tts.Voice
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.PronunciationRule
import io.github.kvnpetit.betterfrenchtts.QueueProgress
import io.github.kvnpetit.betterfrenchtts.SpeechPreset
import io.github.kvnpetit.betterfrenchtts.SpeechResult
import io.github.kvnpetit.betterfrenchtts.WordHighlight
import io.github.kvnpetit.betterfrenchtts.demo.ui.theme.BetterFrenchTtsTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetterFrenchTtsTheme {
                DemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Initialisation...") }
    var voiceInfo by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("Bonjour, bienvenue dans Better French TTS.") }
    var ssmlPreview by remember { mutableStateOf("") }
    var ssmlInput by remember { mutableStateOf("<speak><prosody rate=\"slow\" pitch=\"+2st\">Ceci est du SSML brut.</prosody></speak>") }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedAudioFocus by remember { mutableStateOf(BetterFrenchTts.AudioFocusMode.DUCK) }
    var ttsInstance by remember { mutableStateOf<BetterFrenchTts?>(null) }
    var highlight by remember { mutableStateOf<WordHighlight?>(null) }
    var queueProgress by remember { mutableStateOf<QueueProgress?>(null) }
    var queueStatus by remember { mutableStateOf("") }
    var pronunciationStatus by remember { mutableStateOf("Aucune règle") }
    var coroutineStatus by remember { mutableStateOf("") }

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
        }.onQueueProgress { progress ->
            queueProgress = progress
            queueStatus = "Item ${progress.currentIndex + 1}/${progress.totalItems}"
        }.onQueueFinished {
            queueStatus = "Queue terminée"
            queueProgress = null
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
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
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

            SectionDivider("Presets")

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

            SectionDivider("Démos DSL")

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

            // ============================
            // TEXT PREPROCESSING
            // ============================
            SectionDivider("Prétraitement du texte")

            Text(
                "Le texte est automatiquement normalisé avant synthèse.",
                style = MaterialTheme.typography.bodySmall
            )

            ElevatedButton(
                onClick = {
                    tts.speak("M. Dupont et Mme Martin ont rdv chez le Dr Morel bd Haussmann.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abréviations (M., Mme, Dr, bd)")
            }

            ElevatedButton(
                onClick = {
                    tts.speak("Le 1er janvier, la 3ème édition et le 20ème anniversaire.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ordinaux (1er, 3ème, 20ème)")
            }

            ElevatedButton(
                onClick = {
                    tts.speak("Le train part à 14h30 et arrive à 8h.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Heures (14h30, 8h)")
            }

            ElevatedButton(
                onClick = {
                    tts.speak("Le trajet fait 42 km en 35 min à 120 km/h. Il fait 22°C dehors.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unités (km, min, km/h, °C)")
            }

            ElevatedButton(
                onClick = {
                    tts.speak("Ça coûte 15€, soit 20$ ou 12£. Une hausse de 8%.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Devises et pourcentages (€, $, £, %)")
            }

            ElevatedButton(
                onClick = {
                    tts.speak("Louis XIV a vécu au XVIIe siècle. François Ier était roi.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chiffres romains (XIV, XVIIe)")
            }

            // ============================
            // PRONUNCIATION DICTIONARY
            // ============================
            SectionDivider("Dictionnaire de prononciation")

            Text(pronunciationStatus, style = MaterialTheme.typography.bodySmall)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ElevatedButton(
                    onClick = {
                        tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
                        tts.addPronunciation(PronunciationRule.Alias("Xiaomi", "Chao-mi"))
                        pronunciationStatus = "Alias: Huawei→Oua-ouei, Xiaomi→Chao-mi"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ajouter alias", maxLines = 1)
                }

                ElevatedButton(
                    onClick = {
                        tts.addPronunciation(PronunciationRule.Ipa("Lacoste", "la.kɔst"))
                        tts.addPronunciation(PronunciationRule.Ipa("Nutella", "nu.tɛ.la"))
                        pronunciationStatus = "IPA: Lacoste→la.kɔst, Nutella→nu.tɛ.la"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ajouter IPA", maxLines = 1)
                }
            }

            ElevatedButton(
                onClick = {
                    tts.speak("J'ai acheté un Huawei et un Xiaomi. Ma veste Lacoste sent le Nutella.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tester : Huawei, Xiaomi, Lacoste, Nutella")
            }

            OutlinedButton(
                onClick = {
                    tts.clearPronunciations()
                    pronunciationStatus = "Aucune règle"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Effacer toutes les règles")
            }

            // ============================
            // SPEECH QUEUE
            // ============================
            SectionDivider("File d'attente")

            if (queueStatus.isNotEmpty()) {
                Text(queueStatus, style = MaterialTheme.typography.bodySmall)
            }

            ElevatedButton(
                onClick = {
                    tts.enqueue("Bienvenue dans la démonstration de la file d'attente.")
                    tts.enqueue("Ceci est le deuxième élément.", preset = SpeechPreset.CALM)
                    tts.enqueue("Et voici le troisième, plus rapide.", preset = SpeechPreset.EXCITED)
                    tts.enqueue {
                        emphasis { text("Le dernier élément, en emphase DSL.") }
                    }
                    queueStatus = "4 éléments en file"
                    tts.playQueue()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lancer une queue (4 items)")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = { tts.pauseQueue() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pause")
                }
                FilledTonalButton(
                    onClick = { tts.resumeQueue() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reprendre")
                }
                FilledTonalButton(
                    onClick = { tts.skipToNext() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Suivant")
                }
            }

            OutlinedButton(
                onClick = {
                    tts.clearQueue()
                    queueStatus = "Queue vidée"
                    queueProgress = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Vider la queue")
            }

            // ============================
            // COROUTINES
            // ============================
            SectionDivider("Coroutines (speakAndAwait)")

            if (coroutineStatus.isNotEmpty()) {
                Text(coroutineStatus, style = MaterialTheme.typography.bodySmall)
            }

            ElevatedButton(
                onClick = {
                    scope.launch {
                        coroutineStatus = "Lecture séquentielle en cours..."
                        val r1 = tts.speakAndAwait("Première phrase, j'attends qu'elle finisse.")
                        if (r1 is SpeechResult.Success) {
                            coroutineStatus = "Phrase 1 terminée, lancement phrase 2..."
                            val r2 = tts.speakAndAwait("Deuxième phrase, enchaînée automatiquement.", preset = SpeechPreset.CALM)
                            coroutineStatus = if (r2 is SpeechResult.Success) "Lecture séquentielle terminée" else "Erreur phrase 2"
                        } else {
                            coroutineStatus = "Erreur phrase 1"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("speakAndAwait séquentiel (2 phrases)")
            }

            ElevatedButton(
                onClick = {
                    scope.launch {
                        coroutineStatus = "Lecture DSL await en cours..."
                        val result = tts.speakAndAwait {
                            slow { text("Ceci est lent.") }
                            pause(300)
                            fast { text("Et ceci est rapide !") }
                        }
                        coroutineStatus = if (result is SpeechResult.Success) "DSL await terminé" else "Erreur DSL await"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("speakAndAwait DSL")
            }

            // ============================
            // RAW SSML
            // ============================
            SectionDivider("SSML brut")

            OutlinedTextField(
                value = ssmlInput,
                onValueChange = { ssmlInput = it },
                label = { Text("SSML à envoyer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )

            ElevatedButton(
                onClick = { tts.speakSsml(ssmlInput) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lire le SSML brut")
            }

            // ============================
            // SYNTHESIZE TO FILE
            // ============================
            SectionDivider("Synthèse vers fichier")

            ElevatedButton(
                onClick = {
                    val file = File(context.cacheDir, "tts_output.wav")
                    val result = tts.synthesizeToFile(inputText, file)
                    val message = when (result) {
                        is SpeechResult.Success -> "Fichier enregistré : ${file.absolutePath}"
                        is SpeechResult.Error -> "Erreur : ${result.reason}"
                        SpeechResult.NotReady -> "TTS non prêt"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sauvegarder en WAV (cache)")
            }

            // ============================
            // SSML PREVIEW
            // ============================
            SectionDivider("Preview SSML")

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

            // ============================
            // AUDIO FOCUS
            // ============================
            SectionDivider("Audio Focus")

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

            // ============================
            // VOICE SELECTION
            // ============================
            SectionDivider("Voix")

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

@Composable
private fun SectionDivider(title: String) {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}
