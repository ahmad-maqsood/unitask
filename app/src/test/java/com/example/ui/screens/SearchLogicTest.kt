package com.example.ui.screens

import com.example.data.local.Course
import com.example.data.local.Note
import com.example.data.local.NoteListItem
import com.example.data.local.TaskItem
import com.example.data.local.TaskWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {

    private fun makeTask(
        id: Long = 1,
        title: String = "Sample Task",
        courseCode: String = "CS 101"
    ): TaskWithCourse {
        val course = Course(id = 10, name = "Computer Science", code = courseCode)
        return TaskWithCourse(
            task = TaskItem(id = id, courseId = course.id, title = title, dueDateEpochMillis = 0L),
            course = course
        )
    }

    private fun makeNote(id: Long = 1, title: String = "Sample Note"): NoteListItem {
        return NoteListItem(
            note = Note(id = id, title = title, body = "body text"),
            courseCode = "CS 101",
            courseName = "Computer Science",
            courseColorHex = "#4F46E5"
        )
    }

    private fun makeCourse(id: Long = 1, code: String = "CS 101", name: String = "Computer Science"): Course {
        return Course(id = id, code = code, name = name)
    }

    @Test
    fun testShouldShowExpandButton() {
        assertFalse(shouldShowExpandButton(totalCount = 4, isExpanded = false, limit = 5))
        assertFalse(shouldShowExpandButton(totalCount = 5, isExpanded = false, limit = 5))
        assertTrue(shouldShowExpandButton(totalCount = 6, isExpanded = false, limit = 5))
        assertFalse(shouldShowExpandButton(totalCount = 6, isExpanded = true, limit = 5))
    }

    @Test
    fun testVisibleSectionItems() {
        val items = (1..7).toList()
        assertEquals(listOf(1, 2, 3, 4, 5), visibleSectionItems(items, isExpanded = false, limit = 5))
        assertEquals(items, visibleSectionItems(items, isExpanded = true, limit = 5))

        val shortItems = listOf(1, 2, 3)
        assertEquals(shortItems, visibleSectionItems(shortItems, isExpanded = false, limit = 5))
    }

    @Test
    fun testFormatShowAllButtonText() {
        assertEquals("Show all 12", formatShowAllButtonText(12))
        assertEquals("Show all 5", formatShowAllButtonText(5))
    }

    @Test
    fun testResolveTaskClickTargetArchived() {
        val taskWithCourse = makeTask(id = 1, title = "Archived Task")
        val searchResult = SearchTaskResult(taskWithCourse = taskWithCourse, archivedSemester = "Fall 2024")
        val target = resolveTaskClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.ShowArchivedDialog)
        assertEquals("Fall 2024", (target as SearchNavigationTarget.ShowArchivedDialog).semesterName)
    }

    @Test
    fun testResolveTaskClickTargetGroupTask() {
        val groupTask = makeTask(id = -100L, title = "Group Presentation")
        val searchResult = SearchTaskResult(taskWithCourse = groupTask, archivedSemester = null)
        val target = resolveTaskClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.DeadlinesGroupTask)
        assertEquals("Group Presentation", (target as SearchNavigationTarget.DeadlinesGroupTask).taskTitle)
    }

    @Test
    fun testResolveTaskClickTargetMyTask() {
        val myTask = makeTask(id = 5L, title = "Buy Groceries", courseCode = "MYTASKS")
        val searchResult = SearchTaskResult(taskWithCourse = myTask, archivedSemester = null)
        val target = resolveTaskClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.EditPersonalTask)
        val editTarget = target as SearchNavigationTarget.EditPersonalTask
        assertTrue(editTarget.isMyTask)
        assertEquals(5L, editTarget.taskWithCourse.task.id)
    }

    @Test
    fun testResolveTaskClickTargetCourseTask() {
        val courseTask = makeTask(id = 8L, title = "Homework 1", courseCode = "MATH 201")
        val searchResult = SearchTaskResult(taskWithCourse = courseTask, archivedSemester = null)
        val target = resolveTaskClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.EditPersonalTask)
        val editTarget = target as SearchNavigationTarget.EditPersonalTask
        assertFalse(editTarget.isMyTask)
        assertEquals(8L, editTarget.taskWithCourse.task.id)
    }

    @Test
    fun testResolveNoteClickTargetArchived() {
        val noteItem = makeNote(id = 12L, title = "Past Lecture")
        val searchResult = SearchNoteResult(
            noteItem = noteItem,
            snippet = "snippet",
            matchRange = null,
            archivedSemester = "Spring 2023"
        )
        val target = resolveNoteClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.ShowArchivedDialog)
        assertEquals("Spring 2023", (target as SearchNavigationTarget.ShowArchivedDialog).semesterName)
    }

    @Test
    fun testResolveNoteClickTargetActive() {
        val noteItem = makeNote(id = 22L, title = "Current Note")
        val searchResult = SearchNoteResult(
            noteItem = noteItem,
            snippet = "snippet",
            matchRange = null,
            archivedSemester = null
        )
        val target = resolveNoteClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.OpenNote)
        assertEquals(22L, (target as SearchNavigationTarget.OpenNote).noteId)
    }

    @Test
    fun testResolveCourseClickTargetArchived() {
        val course = makeCourse(id = 31L, code = "HIST 101")
        val searchResult = SearchCourseResult(course = course, archivedSemester = "Fall 2022")
        val target = resolveCourseClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.ShowArchivedDialog)
        assertEquals("Fall 2022", (target as SearchNavigationTarget.ShowArchivedDialog).semesterName)
    }

    @Test
    fun testResolveCourseClickTargetActive() {
        val course = makeCourse(id = 42L, code = "CHEM 101")
        val searchResult = SearchCourseResult(course = course, archivedSemester = null)
        val target = resolveCourseClickTarget(searchResult)

        assertTrue(target is SearchNavigationTarget.OpenCourse)
        assertEquals(42L, (target as SearchNavigationTarget.OpenCourse).courseId)
    }
}
