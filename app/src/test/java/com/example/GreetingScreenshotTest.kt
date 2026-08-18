package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.domain.model.TrackingMetrics
import com.example.domain.model.TrackingState
import com.example.ui.home.HeroTrackingCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun hero_tracking_card_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        HeroTrackingCard(
          metrics = TrackingMetrics(
            state = TrackingState.TRACKING,
            currentSpeedKmh = 28.5f,
            distanceMeters = 3400.0,
            elapsedTimeMs = 600000L,
            pointCount = 42
          ),
          onStart = {},
          onPause = {},
          onResume = {},
          onStop = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/hero_tracking_card.png")
  }
}
