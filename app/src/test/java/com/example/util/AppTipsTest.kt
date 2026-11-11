package com.example.util

import org.junit.Assert.assertTrue
import org.junit.Test

class AppTipsTest {

    @Test
    fun everyTipLinksToARealGuideTopic() {
        val titles = FeatureGuide.all.map { it.title }.toSet()
        AppTip.entries.forEach { tip ->
            assertTrue("No guide topic named '${tip.guideTitle}' for ${tip.name}", tip.guideTitle in titles)
        }
    }

    @Test
    fun everyTipHasText() {
        AppTip.entries.forEach { tip -> assertTrue(tip.text.isNotBlank()) }
    }
}
