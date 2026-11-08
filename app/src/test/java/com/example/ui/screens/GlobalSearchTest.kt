package com.example.ui.screens

import com.example.data.local.Course
import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.data.repository.NoteRepository
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {

    private fun makeTask(
        id: Long = 1,
        title: String = "Test Task",
        description: String = "",
        courseId: Long = 10,
        course: Course? = Course(id = 10, name = "Intro to CS", code = "CS 101")
    ): TaskWithCourse {
        return TaskWithCourse(
            task = TaskItem(
                id = id,
                courseId = courseId,
                title = title,
                description = description,
                dueDateEpochMillis = 1700000000000L
            ),
            course = course
        )
    }

    private fun makeNote(
        id: Long = 1,
        title: String = "Test Note",
        body: String = "Note body text",
        courseCode: String? = "CS 101"
    ): NoteListItem {
        return NoteListItem(
            note = Note(
                id = id,
                title = title,
                body = body,
                courseId = 10L
            ),
            courseCode = courseCode,
            courseName = "Intro to CS",
            courseColorHex = "#4F46E5"
        )
    }

    private fun makeCourse(
        id: Long = 10,
        name: String = "Intro to CS",
        code: String = "CS 101",
        instructor: String = "Dr. Alan Turing",
        location: String = "Room 404"
    ): Course {
        return Course(
            id = id,
            name = name,
            code = code,
            instructor = instructor,
            location = location
        )
    }

    @Test
    fun testMultiWordMatch() {
        val task1 = makeTask(title = "Exam review session", description = "Bring slides")
        val task2 = makeTask(title = "Only exam", description = "No other words")
        val note1 = makeNote(title = "Midterm exam", body = "Complete chapter review and practice problems")
        val course1 = makeCourse(name = "Exam Review Workshop", code = "REV 100")

        val results = searchEverything(
            query = "exam review",
            tasks = listOf(task1, task2),
            notes = listOf(note1),
            courses = listOf(course1)
        )

        assertEquals(1, results.tasks.size)
        assertEquals("Exam review session", results.tasks[0].task.title)

        assertEquals(1, results.notes.size)
        assertEquals("Midterm exam", results.notes[0].noteItem.note.title)

        assertEquals(1, results.courses.size)
        assertEquals("REV 100", results.courses[0].course.code)
    }

    @Test
    fun testWordsInDifferentFields() {
        // Task: "homework" in title, "database" in course name
        val dbCourse = Course(id = 20, name = "Database Systems", code = "CS 305")
        val task = makeTask(title = "Homework 1", description = "Queries", course = dbCourse)

        // Note: "meeting" in title, "database" in body
        val note = makeNote(title = "Project Meeting", body = "Discuss database schema and indexes")

        // Course: "database" in name, "auditorium" in location
        val course = Course(id = 21, name = "Database Lab", code = "CS 306", location = "Auditorium A")

        val results = searchEverything(
            query = "database homework",
            tasks = listOf(task),
            notes = listOf(note),
            courses = listOf(course)
        )

        assertEquals(1, results.tasks.size)
        assertEquals("Homework 1", results.tasks[0].task.title)

        // Note does not contain "homework" so it should not match
        assertEquals(0, results.notes.size)

        // Query with words split across note title and body
        val noteResults = searchEverything(
            query = "meeting database",
            tasks = emptyList(),
            notes = listOf(note),
            courses = emptyList()
        )
        assertEquals(1, noteResults.notes.size)
        assertEquals("Project Meeting", noteResults.notes[0].noteItem.note.title)
    }

    @Test
    fun testCaseInsensitive() {
        val task = makeTask(title = "Binary Search", description = "ALGORITHM complexity analysis")
        val note = makeNote(title = "Sorting Algorithms", body = "Merge sort and quick sort")
        val course = makeCourse(name = "Design of Algorithms", code = "CS 401")

        val results = searchEverything(
            query = "aLgOrItHm",
            tasks = listOf(task),
            notes = listOf(note),
            courses = listOf(course)
        )

        assertEquals(1, results.tasks.size)
        assertEquals(1, results.notes.size)
        assertEquals(1, results.courses.size)
    }

    @Test
    fun testMatchDeepInsideLongNoteBodyQuickly() {
        val padding = "x".repeat(NoteRepository.MAX_BODY - 20)
        val target = "SPECIAL_NEEDLE"
        val suffix = "y".repeat(6)
        val longBody = padding + target + suffix
        assertEquals(NoteRepository.MAX_BODY, longBody.length)

        val note = makeNote(title = "Massive Note", body = longBody)

        val startTime = System.currentTimeMillis()
        val results = searchEverything(
            query = "special_needle",
            tasks = emptyList(),
            notes = listOf(note),
            courses = emptyList()
        )
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Search took too long: ${elapsed}ms", elapsed < 500)
        assertEquals(1, results.notes.size)

        val noteResult = results.notes[0]
        val range = noteResult.matchRange
        assertNotNull(range)
        val matchedTextInSnippet = noteResult.snippet.slice(range!!)
        assertEquals(target.lowercase(), matchedTextInSnippet.lowercase())
    }

    @Test
    fun testSnippetAndHighlightRangeAtStartMiddleAndEnd() {
        val words = listOf("needle")

        // Match at start of body
        val bodyStart = "needle is right at the start of a fairly long document with plenty of content to fill out the eighty character limit"
        val (snippetStart, rangeStart) = buildNoteSnippet(bodyStart, words, targetLength = 50)
        assertNotNull(rangeStart)
        assertEquals(0, rangeStart!!.first)
        assertEquals("needle", snippetStart.slice(rangeStart).lowercase())
        assertFalse("Should not have prefix ellipsis when matching at start", snippetStart.startsWith("..."))
        assertTrue("Should have suffix ellipsis when text is cut off", snippetStart.endsWith("..."))

        // Match in the middle of body
        val bodyMiddle = "A".repeat(100) + "needle" + "B".repeat(100)
        val (snippetMiddle, rangeMiddle) = buildNoteSnippet(bodyMiddle, words, targetLength = 50)
        assertNotNull(rangeMiddle)
        assertTrue("Should have prefix ellipsis in middle", snippetMiddle.startsWith("..."))
        assertTrue("Should have suffix ellipsis in middle", snippetMiddle.endsWith("..."))
        assertEquals("needle", snippetMiddle.slice(rangeMiddle!!).lowercase())

        // Match at the end of body
        val bodyEnd = "A".repeat(100) + "needle"
        val (snippetEnd, rangeEnd) = buildNoteSnippet(bodyEnd, words, targetLength = 50)
        assertNotNull(rangeEnd)
        assertTrue("Should have prefix ellipsis at end", snippetEnd.startsWith("..."))
        assertFalse("Should not have suffix ellipsis when matching at end", snippetEnd.endsWith("..."))
        assertEquals("needle", snippetEnd.slice(rangeEnd!!).lowercase())

        // Match in title only (body does not contain query words)
        val bodyNoMatch = "Body text without any search keywords here."
        val (snippetNoMatch, rangeNoMatch) = buildNoteSnippet(bodyNoMatch, listOf("nonexistent"), targetLength = 30)
        assertNull(rangeNoMatch)
        assertTrue(snippetNoMatch.startsWith("Body text without"))
    }

    @Test
    fun testMyTasksLabel() {
        val myTasksCourse = Course(id = 99, name = "My tasks", code = "MYTASKS")
        val task = makeTask(title = "Buy groceries", course = myTasksCourse)

        val results = searchEverything(
            query = "My tasks",
            tasks = listOf(task),
            notes = emptyList(),
            courses = listOf(myTasksCourse)
        )

        assertEquals(1, results.tasks.size)
        assertEquals("Buy groceries", results.tasks[0].task.title)

        assertEquals(1, results.courses.size)
        assertEquals("MYTASKS", results.courses[0].course.code)
    }

    @Test
    fun testEmptyQuery() {
        val task = makeTask()
        val note = makeNote()
        val course = makeCourse()

        val emptyResults = searchEverything("", listOf(task), listOf(note), listOf(course))
        assertTrue(emptyResults.isEmpty)
        assertEquals(0, emptyResults.totalCount)

        val whitespaceResults = searchEverything("     ", listOf(task), listOf(note), listOf(course))
        assertTrue(whitespaceResults.isEmpty)
        assertEquals(0, whitespaceResults.totalCount)
    }

    @Test
    fun testNoMatch() {
        val task = makeTask(title = "Operating Systems Lab")
        val note = makeNote(title = "Compiler Notes")
        val course = makeCourse(name = "Networks", code = "CS 301")

        val results = searchEverything(
            query = "unmatchedkeyword12345",
            tasks = listOf(task),
            notes = listOf(note),
            courses = listOf(course)
        )

        assertTrue(results.isEmpty)
        assertEquals(0, results.tasks.size)
        assertEquals(0, results.notes.size)
        assertEquals(0, results.courses.size)
    }

    @Test
    fun testArchivedItemsOnlyReturnedWhenPassed() {
        val activeTask = makeTask(id = 1, title = "Research Project Active")
        val archivedTask = makeTask(id = 2, title = "Research Project Archived")
        val activeNote = makeNote(id = 1, title = "Research Notes Active")
        val archivedNote = makeNote(id = 2, title = "Research Notes Archived")
        val activeCourse = makeCourse(id = 10, name = "Research Seminar Active", code = "RES 101")
        val archivedCourse = makeCourse(id = 20, name = "Research Seminar Archived", code = "RES 102")

        // 1. Without passing archived lists: only active items are returned
        val defaultResults = searchEverything(
            query = "research",
            tasks = listOf(activeTask),
            notes = listOf(activeNote),
            courses = listOf(activeCourse)
        )

        assertEquals(1, defaultResults.tasks.size)
        assertFalse(defaultResults.tasks[0].isArchived)
        assertNull(defaultResults.tasks[0].archivedSemester)

        assertEquals(1, defaultResults.notes.size)
        assertFalse(defaultResults.notes[0].isArchived)
        assertNull(defaultResults.notes[0].archivedSemester)

        assertEquals(1, defaultResults.courses.size)
        assertFalse(defaultResults.courses[0].isArchived)
        assertNull(defaultResults.courses[0].archivedSemester)

        // 2. Passing archived items: both active and archived items are returned with semester flags
        val withArchivedResults = searchEverything(
            query = "research",
            tasks = listOf(activeTask),
            notes = listOf(activeNote),
            courses = listOf(activeCourse),
            archivedTasks = listOf(ArchivedItem(archivedTask, "Fall 2026")),
            archivedNotes = listOf(ArchivedItem(archivedNote, "Fall 2026")),
            archivedCourses = listOf(ArchivedItem(archivedCourse, "Fall 2026"))
        )

        assertEquals(2, withArchivedResults.tasks.size)
        assertFalse(withArchivedResults.tasks[0].isArchived)
        assertNull(withArchivedResults.tasks[0].archivedSemester)
        assertTrue(withArchivedResults.tasks[1].isArchived)
        assertEquals("Fall 2026", withArchivedResults.tasks[1].archivedSemester)

        assertEquals(2, withArchivedResults.notes.size)
        assertFalse(withArchivedResults.notes[0].isArchived)
        assertNull(withArchivedResults.notes[0].archivedSemester)
        assertTrue(withArchivedResults.notes[1].isArchived)
        assertEquals("Fall 2026", withArchivedResults.notes[1].archivedSemester)

        assertEquals(2, withArchivedResults.courses.size)
        assertFalse(withArchivedResults.courses[0].isArchived)
        assertNull(withArchivedResults.courses[0].archivedSemester)
        assertTrue(withArchivedResults.courses[1].isArchived)
        assertEquals("Fall 2026", withArchivedResults.courses[1].archivedSemester)
    }
}
