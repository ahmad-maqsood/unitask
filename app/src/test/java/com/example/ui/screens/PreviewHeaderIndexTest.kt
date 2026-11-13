package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewHeaderIndexTest {

    @Test
    fun withoutAHeaderBlocksAndListItemsAreTheSame() {
        assertEquals(0, previewItemIndexForBlock(0, hasHeader = false))
        assertEquals(4, previewItemIndexForBlock(4, hasHeader = false))
        assertEquals(4, previewBlockForItem(4, hasHeader = false))
    }

    @Test
    fun withAHeaderBlocksComeOneItemLater() {
        assertEquals(3, previewItemIndexForBlock(2, hasHeader = true))
        assertEquals(2, previewBlockForItem(3, hasHeader = true))
    }

    @Test
    fun theFirstBlockStaysAtTheTopSoTheHeaderIsInView() {
        assertEquals(0, previewItemIndexForBlock(0, hasHeader = true))
    }

    @Test
    fun theHeaderItselfCountsAsTheFirstBlock() {
        assertEquals(0, previewBlockForItem(0, hasHeader = true))
        assertEquals(0, previewBlockForItem(1, hasHeader = true))
    }
}
