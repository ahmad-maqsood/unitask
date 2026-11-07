package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePdfLayoutTest {

    @Test
    fun headingSizeReturnsExpectedSizesForLevels() {
        assertEquals(20f, NotePdf.headingSize(1), 0.001f)
        assertEquals(16f, NotePdf.headingSize(2), 0.001f)
        assertEquals(13f, NotePdf.headingSize(3), 0.001f)
        assertEquals(11f, NotePdf.headingSize(4), 0.001f)
        assertEquals(11f, NotePdf.headingSize(5), 0.001f)
        assertEquals(11f, NotePdf.headingSize(6), 0.001f)
    }

    @Test
    fun headingSpacingAboveReturnsExpectedSpacing() {
        assertEquals(18f, NotePdf.headingSpacingAbove(1), 0.001f)
        assertEquals(14f, NotePdf.headingSpacingAbove(2), 0.001f)
        assertEquals(10f, NotePdf.headingSpacingAbove(3), 0.001f)
        assertEquals(8f, NotePdf.headingSpacingAbove(4), 0.001f)
    }

    @Test
    fun listIndentScalesWithDepth() {
        assertEquals(0f, NotePdf.listIndent(0), 0.001f)
        assertEquals(16f, NotePdf.listIndent(1), 0.001f)
        assertEquals(32f, NotePdf.listIndent(2), 0.001f)
        assertEquals(0f, NotePdf.listIndent(-1), 0.001f)
    }

    @Test
    fun tableColumnWidthsDividesWidthEqually() {
        assertTrue(NotePdf.tableColumnWidths(0).isEmpty())
        val twoCols = NotePdf.tableColumnWidths(2, 500f)
        assertEquals(2, twoCols.size)
        assertEquals(250f, twoCols[0], 0.001f)
        assertEquals(250f, twoCols[1], 0.001f)

        val threeCols = NotePdf.tableColumnWidths(3, 300f)
        assertEquals(3, threeCols.size)
        assertEquals(100f, threeCols[0], 0.001f)
        assertEquals(100f, threeCols[1], 0.001f)
        assertEquals(100f, threeCols[2], 0.001f)
    }

    @Test
    fun blockFitsReturnsTrueOnlyWhenBlockWithinMaxY() {
        assertTrue(NotePdf.blockFits(50f, 700f, 794f))
        assertTrue(NotePdf.blockFits(94f, 700f, 794f))
        assertFalse(NotePdf.blockFits(95f, 700f, 794f))
    }

    @Test
    fun headingNeedsNewPageReturnsTrueWhenRemainingSpaceUnderThreshold() {
        // Remaining space = 794 - 740 = 54 (< 60) -> needs new page
        assertTrue(NotePdf.headingNeedsNewPage(740f, 794f, 60f))
        // Remaining space = 794 - 730 = 64 (>= 60) -> does not need new page
        assertFalse(NotePdf.headingNeedsNewPage(730f, 794f, 60f))
    }
}
