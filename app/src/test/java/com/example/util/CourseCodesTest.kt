package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseCodesTest {

    @Test
    fun normalizeCourseCode_surroundingSpaces() {
        assertEquals("CS101", normalizeCourseCode("  CS101  "))
        assertEquals("CS101", normalizeCourseCode("   CS101"))
        assertEquals("CS101", normalizeCourseCode("CS101   "))
    }

    @Test
    fun normalizeCourseCode_lowerCase() {
        assertEquals("CS101", normalizeCourseCode("cs101"))
        assertEquals("MATH200", normalizeCourseCode("math200"))
    }

    @Test
    fun normalizeCourseCode_innerSpaces() {
        assertEquals("CS101", normalizeCourseCode("CS 101"))
        assertEquals("CS101", normalizeCourseCode("CS   101"))
        assertEquals("MATH200", normalizeCourseCode("M A T H 2 0 0"))
    }

    @Test
    fun normalizeCourseCode_alreadyNormalCode() {
        assertEquals("CS101", normalizeCourseCode("CS101"))
        assertEquals("MATH200", normalizeCourseCode("MATH200"))
    }

    @Test
    fun normalizeCourseCode_emptyString() {
        assertEquals("", normalizeCourseCode(""))
        assertEquals("", normalizeCourseCode("   "))
    }
}
