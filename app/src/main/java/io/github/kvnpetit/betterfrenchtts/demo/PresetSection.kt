package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.SpeechPreset

@Composable
internal fun ColumnScope.PresetSection(tts: BetterFrenchTts, inputText: String) {
    SectionDivider("Presets")

    SpeechPreset.builtIn.chunked(3).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            row.forEach { preset ->
                FilledTonalButton(
                    onClick = { tts.speak(inputText, preset = preset) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(preset.name, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text("Preset personnalisé", style = MaterialTheme.typography.titleSmall)

    FilledTonalButton(
        onClick = {
            val myPreset =
                SpeechPreset(name = "Mon preset", rate = "80%", pitch = "+5st", volume = "loud")
            tts.speak(inputText, preset = myPreset)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Preset personnalisé (80%, +5st, loud)")
    }
}
