package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts

@Composable
internal fun ColumnScope.NormalizationSection(tts: BetterFrenchTts) {
    // ============================
    // TEXT PREPROCESSING
    // ============================
    SectionDivider("Prétraitement du texte")

    Text(
        "Le texte est automatiquement normalisé avant synthèse.",
        style = MaterialTheme.typography.bodySmall,
    )

    ElevatedButton(
        onClick = { tts.speak("M. Dupont et Mme Martin ont rdv chez le Dr Morel bd Haussmann.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Abréviations (M., Mme, Dr, bd)")
    }

    ElevatedButton(
        onClick = { tts.speak("Le 1er janvier, la 3ème édition et le 20ème anniversaire.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Ordinaux (1er, 3ème, 20ème)")
    }

    ElevatedButton(
        onClick = { tts.speak("Le train part à 14h30 et arrive à 8h.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Heures (14h30, 8h)")
    }

    ElevatedButton(
        onClick = { tts.speak("Le trajet fait 42 km en 35 min à 120 km/h. Il fait 22°C dehors.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Unités (km, min, km/h, °C)")
    }

    ElevatedButton(
        onClick = { tts.speak("Ça coûte 15€, soit 20$ ou 12£. Une hausse de 8%.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Devises et pourcentages (€, $, £, %)")
    }

    ElevatedButton(
        onClick = { tts.speak("Louis XIV a vécu au XVIIe siècle. François Ier était roi.") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Chiffres romains (XIV, XVIIe)")
    }
}
