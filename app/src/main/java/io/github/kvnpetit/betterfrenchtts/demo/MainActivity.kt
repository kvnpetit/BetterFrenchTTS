package io.github.kvnpetit.betterfrenchtts.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
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
import io.github.kvnpetit.betterfrenchtts.QueueProgress
import io.github.kvnpetit.betterfrenchtts.SpeechPreset
import io.github.kvnpetit.betterfrenchtts.SpeechResult
import io.github.kvnpetit.betterfrenchtts.WordHighlight
import io.github.kvnpetit.betterfrenchtts.demo.ui.theme.BetterFrenchTtsTheme
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BetterFrenchTtsTheme { DemoScreen() } }
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
    var ssmlInput by remember {
        mutableStateOf(
            "<speak><prosody rate=\"slow\" pitch=\"+2st\">Ceci est du SSML brut.</prosody></speak>"
        )
    }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedAudioFocus by remember { mutableStateOf(BetterFrenchTts.AudioFocusMode.DUCK) }
    var ttsInstance by remember { mutableStateOf<BetterFrenchTts?>(null) }
    var highlight by remember { mutableStateOf<WordHighlight?>(null) }
    var queueProgress by remember { mutableStateOf<QueueProgress?>(null) }
    var queueStatus by remember { mutableStateOf("") }
    var normalizationPreview by remember { mutableStateOf("") }
    var comparisonStatus by remember { mutableStateOf("") }
    var comparisonStarted by remember { mutableLongStateOf(0L) }
    var rawEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var rawReady by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        var disposed = false
        val engine =
            TextToSpeech(context.applicationContext) { result ->
                handler.post { if (!disposed) rawReady = result == TextToSpeech.SUCCESS }
            }
        rawEngine = engine
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    val latency = SystemClock.elapsedRealtime() - comparisonStarted
                    handler.post {
                        if (!disposed)
                            comparisonStatus = "Android brut : début audio après ${latency} ms"
                    }
                }

                override fun onDone(id: String?) {}

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    handler.post { if (!disposed) comparisonStatus = "Échec Android brut" }
                }
            }
        )
        onDispose {
            disposed = true
            engine.stop()
            engine.shutdown()
        }
    }

    fun createTts(audioFocus: BetterFrenchTts.AudioFocusMode): BetterFrenchTts {
        return BetterFrenchTts(
                context,
                BetterFrenchTts.Config(
                    audioFocus = audioFocus,
                    onReady = { instance ->
                        status = "Prêt (focus: ${audioFocus.name})"
                        voiceInfo = "Voix : ${instance.currentVoice?.name ?: "par défaut"}"
                        availableVoices = instance.listAvailableVoices()
                    },
                    onInitError = { code -> status = "Erreur init TTS (code $code)" },
                ),
            )
            .onStart {
                status = "Lecture en cours... (focus: ${audioFocus.name})"
                if (comparisonStarted > 0) {
                    comparisonStatus =
                        "Bibliothèque : début audio après ${SystemClock.elapsedRealtime() - comparisonStarted} ms"
                    comparisonStarted = 0
                }
            }
            .onDone { status = "Prêt (focus: ${audioFocus.name})" }
            .onError { error -> status = "Erreur : $error" }
            .onWordHighlight { wh -> highlight = if (wh.start >= 0) wh else null }
            .onQueueProgress { progress ->
                queueProgress = progress
                queueStatus = "Item ${progress.currentIndex + 1}/${progress.totalItems}"
            }
            .onQueueFinished {
                queueStatus = "Queue terminée"
                queueProgress = null
            }
    }

    if (ttsInstance == null) {
        ttsInstance = createTts(selectedAudioFocus)
    }

    val tts = ttsInstance!!

    DisposableEffect(Unit) { onDispose { ttsInstance?.shutdown() } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) {
        innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                minLines = 2,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        rawEngine?.stop()
                        comparisonStarted = SystemClock.elapsedRealtime()
                        val result = tts.speak(inputText)
                        if (result != SpeechResult.Success) {
                            comparisonStarted = 0
                            comparisonStatus = result.toString()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Parler")
                }

                Button(
                    onClick = { tts.speak { spellOut(inputText) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Épeler")
                }
            }

            // --- Word highlighting ---
            val h = highlight
            if (
                h != null && h.start >= 0 && h.start < inputText.length && h.end <= inputText.length
            ) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            buildAnnotatedString {
                                append(inputText.substring(0, h.start))
                                withStyle(
                                    SpanStyle(
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                ) {
                                    append(inputText.substring(h.start, h.end))
                                }
                                append(inputText.substring(h.end))
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    comparisonStarted = 0
                    rawEngine?.stop()
                    tts.stop()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }

            OutlinedButton(
                onClick = {
                    val preview = tts.preview(inputText)
                    normalizationPreview =
                        preview.text +
                            "\nRègles : " +
                            preview.transformations.joinToString { it.rule }
                    val prepared = tts.previewSpeech(inputText)
                    normalizationPreview +=
                        "\nAprès dictionnaire : " +
                            prepared.nativeSteps.joinToString("") { it.text }
                    (prepared.result as? SpeechResult.Error)?.let {
                        normalizationPreview += "\n${it.reason}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Voir la normalisation")
            }
            if (normalizationPreview.isNotEmpty()) Text(normalizationPreview)

            OutlinedButton(
                onClick = {
                    val engine = rawEngine
                    val voice = tts.currentVoice
                    val sameVoice = engine?.voices?.firstOrNull { it.name == voice?.name }
                    if (
                        engine == null ||
                            sameVoice == null ||
                            engine.setVoice(sameVoice) != TextToSpeech.SUCCESS
                    ) {
                        comparisonStatus = "Même voix indisponible pour la comparaison"
                    } else {
                        tts.stop()
                        engine.setSpeechRate(1f)
                        engine.setPitch(1f)
                        comparisonStarted = SystemClock.elapsedRealtime()
                        val result =
                            engine.speak(
                                inputText,
                                TextToSpeech.QUEUE_FLUSH,
                                Bundle().apply {
                                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.8f)
                                },
                                "comparison",
                            )
                        if (result != TextToSpeech.SUCCESS)
                            comparisonStatus = "Android brut a refusé la lecture"
                    }
                },
                enabled = rawReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Comparer : Android brut, même voix")
            }
            if (comparisonStatus.isNotEmpty()) Text(comparisonStatus)
            Text(
                "Comparaison d'écoute, pas une mesure automatique de qualité. Les presets ne changent pas l'identité de la voix.",
                style = MaterialTheme.typography.bodySmall,
            )

            PresetSection(tts, inputText)

            DslSection(tts)

            NormalizationSection(tts)

            PronunciationSection(tts)

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
                    tts.enqueue(
                        "Et voici le troisième, plus rapide.",
                        preset = SpeechPreset.EXCITED,
                    )
                    tts.enqueue { emphasis { text("Le dernier élément, en emphase DSL.") } }
                    queueStatus = "4 éléments en file"
                    tts.playQueue()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Lancer une queue (4 items)")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(onClick = { tts.pauseQueue() }, modifier = Modifier.weight(1f)) {
                    Text("Pause")
                }
                FilledTonalButton(onClick = { tts.resumeQueue() }, modifier = Modifier.weight(1f)) {
                    Text("Reprendre")
                }
                FilledTonalButton(onClick = { tts.skipToNext() }, modifier = Modifier.weight(1f)) {
                    Text("Suivant")
                }
            }

            OutlinedButton(
                onClick = {
                    tts.clearQueue()
                    queueStatus = "Queue vidée"
                    queueProgress = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Vider la queue")
            }

            CoroutineSection(tts)

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
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            ElevatedButton(
                onClick = { tts.speakSsml(ssmlInput) },
                modifier = Modifier.fillMaxWidth(),
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
                    scope.launch {
                        val result = tts.synthesizeToFileAndAwait(inputText, file)
                        val message =
                            when (result) {
                                is SpeechResult.Success ->
                                    "Fichier enregistré : ${file.absolutePath}"
                                is SpeechResult.Error -> "Erreur : ${result.reason}"
                                SpeechResult.NotReady -> "TTS non prêt"
                            }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sauvegarder en WAV (cache)")
            }

            // ============================
            // SSML PREVIEW
            // ============================
            SectionDivider("Preview SSML")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { ssmlPreview = tts.buildSsml(inputText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Neutre", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { ssmlPreview = tts.buildSsml(inputText, SpeechPreset.STORYTELLING) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Storytelling", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        ssmlPreview =
                            tts.buildSsml {
                                text("Bonjour.")
                                pause(400)
                                slow { text("Lent.") }
                                emphasis { text("Important !") }
                            }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("DSL", maxLines = 1)
                }
            }

            if (ssmlPreview.isNotEmpty()) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = ssmlPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                    )
                }
            }

            // ============================
            // AUDIO FOCUS
            // ============================
            SectionDivider("Audio Focus")

            Text(
                "Lancez de la musique puis parlez pour tester l'effet.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ============================
            // VOICE SELECTION
            // ============================
            SectionDivider("Voix")

            ElevatedButton(
                onClick = { availableVoices = tts.listAvailableVoices() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rafraîchir les voix FR")
            }

            if (availableVoices.isNotEmpty()) {
                availableVoices.forEach { voice ->
                    val isSelected = voice.name == tts.currentVoice?.name
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            when (val result = tts.trySetVoice(voice)) {
                                SpeechResult.Success -> voiceInfo = "Voix : ${voice.name}"
                                is SpeechResult.Error ->
                                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                SpeechResult.NotReady ->
                                    Toast.makeText(context, "TTS non prêt", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        },
                        label = { Text("${voice.name} (q:${voice.quality})", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun SectionDivider(title: String) {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}
