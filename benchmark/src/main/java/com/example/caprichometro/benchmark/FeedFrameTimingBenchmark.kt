package com.example.caprichometro.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mide la fluidez de frames (jank / FrameTimingMetric) al interactuar y hacer scroll
 * en la pantalla principal (requiere sesión iniciada o muestra la pantalla activa).
 */
@RunWith(AndroidJUnit4::class)
class FeedFrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollMainContainerFrames() = benchmarkRule.measureRepeated(
        packageName = "com.example.caprichometro",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial()
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        val scrollable = device.wait(Until.findObject(By.scrollable(true)), 3_000)
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 5)
            scrollable.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
