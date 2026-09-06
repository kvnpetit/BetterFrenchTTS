package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.SpeechPreset
import io.github.kvnpetit.betterfrenchtts.SpeechResult
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.CoroutineSection(tts: BetterFrenchTts) {
    val scope = rememberCoroutineScope()
    var coroutineStatus by remember { mutableStateOf("") }
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
                    val r2 =
                        tts.speakAndAwait(
                            "Deuxième phrase, enchaînée automatiquement.",
                            preset = SpeechPreset.CALM,
                        )
                    coroutineStatus =
                        if (r2 is SpeechResult.Success) "Lecture séquentielle terminée"
                        else "Erreur phrase 2"
                } else {
                    coroutineStatus = "Erreur phrase 1"
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("speakAndAwait séquentiel (2 phrases)")
    }

    ElevatedButton(
        onClick = {
            scope.launch {
                coroutineStatus = "Lecture DSL await en cours..."
                val result =
                    tts.speakAndAwait {
                        slow { text("Ceci est lent.") }
                        pause(300)
                        fast { text("Et ceci est rapide !") }
                    }
                coroutineStatus =
                    if (result is SpeechResult.Success) "DSL await terminé" else "Erreur DSL await"
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("speakAndAwait DSL")
    }
}
