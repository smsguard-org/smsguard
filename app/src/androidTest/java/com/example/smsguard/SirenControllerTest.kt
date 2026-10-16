package com.example.smsguard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app stop button reads a persisted flag instead of asking the service, so these tests pin
 * that the flag round-trips and that [SirenController.stop] always clears it, even when nothing was
 * playing (which is what lets the dashboard keep a plain stop button).
 */
@RunWith(AndroidJUnit4::class)
class SirenControllerTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var hadValueBefore = false

    @Before
    fun capture() {
        hadValueBefore = Prefs.sirenActive(context)
    }

    @After
    fun restore() {
        Prefs.setSirenActive(context, hadValueBefore)
    }

    @Test
    fun sirenFlag_roundTrips() {
        Prefs.setSirenActive(context, true)
        assertTrue(Prefs.sirenActive(context))

        Prefs.setSirenActive(context, false)
        assertFalse(Prefs.sirenActive(context))
    }

    @Test
    fun stop_clearsTheFlagEvenWhenNothingIsPlaying() {
        Prefs.setSirenActive(context, true)

        SirenController.stop(context)

        assertFalse(
            "Pressing stop must always leave the UI in the not-playing state",
            Prefs.sirenActive(context)
        )
    }
}
