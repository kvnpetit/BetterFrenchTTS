package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kvnpetit.betterfrenchtts.BetterFrenchTts
import io.github.kvnpetit.betterfrenchtts.SpeechPreset

@Composable
internal fun ColumnScope.DslSection(tts: BetterFrenchTts) {
    SectionDivider("Démos DSL")

    ElevatedButton(
        onClick = {
            tts.speak {
                text("La présentation générale est prête.")
                pause(400)
                slow { text("Vérifions les résultats à tête reposée.") }
                pause(300)
                fast { text("Dépêchez-vous, l'événement a déjà commencé !") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Débit variable")
    }

    ElevatedButton(
        onClick = {
            tts.speak {
                soft { text("Le bébé s'est endormi près de la fenêtre.") }
                pause(500)
                loud { text("Réveillez-vous, c'est l'heure de la fête !") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Nombres et ordinaux")
    }

    ElevatedButton(
        onClick = {
            tts.speak {
                paragraph {
                    sentence { text("Première phrase du paragraphe.") }
                    sentence { strong { text("Deuxième phrase avec emphase forte.") } }
                }
                pause(600)
                paragraph {
                    sentence { lowPitch { text("Un nouveau paragraphe, ton grave.") } }
                    sentence { pitch(semitones = 5) { text("Et celui-ci en ton aigu.") } }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Paragraphes, emphase et pitch")
    }

    ElevatedButton(
        onClick = {
            tts.speakWithPreset(SpeechPreset.STORYTELLING) {
                text("Il était une fois, dans un royaume lointain,")
                pause(500)
                emphasis { text("un dragon magnifique") }
                text(" qui gardait un trésor inestimable.")
                pause(700)
                soft { text("Mais personne n'osait s'en approcher.") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Preset + DSL combiné")
    }

    ElevatedButton(
        onClick = {
            tts.speak {
                withPreset(SpeechPreset.CALM) { text("La journée a été très agréable.") }
                pause(400)
                withPreset(SpeechPreset.EXCITED) { text("Quelle réussite inespérée !") }
                pause(400)
                withPreset(SpeechPreset.WHISPER) { text("C'est un mystère bien gardé.") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Enchaîner les presets dans le DSL")
    }
}
