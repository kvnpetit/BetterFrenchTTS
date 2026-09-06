package io.github.kvnpetit.betterfrenchtts.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI checks do not require an installed TTS engine or downloaded voice data. */
@RunWith(AndroidJUnit4::class)
class DemoSmokeTest {
    @Test
    fun normalizationPreviewShowsFrenchCorrections() {
        compose.onNodeWithText("Bonjour, bienvenue dans Better French TTS.").performTextReplacement("1h et 1€")
        compose.onNodeWithText("Voir la normalisation").performScrollTo().performClick()
        compose.onNodeWithText("1 heure et 1 euro", substring = true).assertIsDisplayed()
    }
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun applicationUsesThePublicDemoIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.kvnpetit.betterfrenchtts.demo", context.packageName)
        compose.onNodeWithText("Better French TTS Demo").assertIsDisplayed()
    }

    @Test
    fun speechInputCanBeEdited() {
        compose.onNodeWithText("Bonjour, bienvenue dans Better French TTS.")
            .performTextReplacement("Bonjour depuis le test Android.")
        compose.onNodeWithText("Bonjour depuis le test Android.").assertIsDisplayed()
    }
}
