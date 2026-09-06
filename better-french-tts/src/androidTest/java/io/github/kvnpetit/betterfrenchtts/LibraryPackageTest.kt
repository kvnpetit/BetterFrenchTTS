package io.github.kvnpetit.betterfrenchtts

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Verifies the instrumented library package after namespace migration.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPackageTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.kvnpetit.betterfrenchtts.test", appContext.packageName)
    }
}
