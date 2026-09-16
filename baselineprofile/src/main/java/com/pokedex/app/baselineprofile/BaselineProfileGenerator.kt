package com.pokedex.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/release/generated/baselineProfiles/baseline-prof.txt` by
 * exercising the hot paths — cold start, scrolling the Pokédex grid, opening a
 * detail page, and switching to the Teams tab. Run with:
 *
 *   ./gradlew :app:generateBaselineProfile
 *
 * on a connected device.
 *
 * UI objects are re-found before every interaction: a `UiObject2` reference goes
 * stale as soon as the tree it points at changes (e.g. after a fling), which
 * throws `StaleObjectException` if reused.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.pokedex.app",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Wait for the Pokédex grid, then fling through it, re-finding each pass.
        device.wait(Until.hasObject(By.scrollable(true)), 15_000)
        repeat(4) {
            device.findObject(By.scrollable(true))?.apply {
                setGestureMargin(device.displayWidth / 5)
                fling(Direction.DOWN)
            }
            device.waitForIdle()
        }
        device.findObject(By.scrollable(true))?.fling(Direction.UP)
        device.waitForIdle()

        // Open a detail page and return.
        if (device.wait(Until.hasObject(By.textStartsWith("Bulbasaur")), 5_000)) {
            device.findObject(By.textStartsWith("Bulbasaur"))?.click()
            device.wait(Until.hasObject(By.textContains("cry")), 8_000)
            device.waitForIdle()
            device.pressBack()
            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        }

        // Teams tab, then back to Pokédex.
        device.findObject(By.text("Teams"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Pokédex"))?.click()
        device.waitForIdle()
    }
}
