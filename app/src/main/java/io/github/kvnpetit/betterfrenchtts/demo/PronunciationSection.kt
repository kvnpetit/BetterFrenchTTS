package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.PronunciationRule

@Composable
internal fun ColumnScope.PronunciationSection(tts: BetterFrenchTts) {
    var pronunciationStatus by remember { mutableStateOf("Aucune règle") }
    // ============================
    // PRONUNCIATION DICTIONARY
    // ============================
    SectionDivider("Dictionnaire de prononciation")

    Text(pronunciationStatus, style = MaterialTheme.typography.bodySmall)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ElevatedButton(
            onClick = {
                tts.addPronunciation(PronunciationRule.Alias("Huawei", "Oua-ouei"))
                tts.addPronunciation(PronunciationRule.Alias("Xiaomi", "Chao-mi"))
                pronunciationStatus = "Alias: Huawei→Oua-ouei, Xiaomi→Chao-mi"
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Ajouter alias", maxLines = 1)
        }

        ElevatedButton(
            onClick = {
                pronunciationStatus =
                    "IPA nécessite le mode SSML et un moteur compatible. Cette démo utilise le mode natif ; utilisez des alias."
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Ajouter IPA", maxLines = 1)
        }
    }

    ElevatedButton(
        onClick = {
            tts.speak("J'ai acheté un Huawei et un Xiaomi. Ma veste Lacoste sent le Nutella.")
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Tester : Huawei, Xiaomi, Lacoste, Nutella")
    }

    OutlinedButton(
        onClick = {
            tts.clearPronunciations()
            pronunciationStatus = "Aucune règle"
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Effacer toutes les règles")
    }
}
