package com.jia.academy.app.navigation

import com.jia.academy.app.catalogue.Category
import com.jia.academy.app.catalogue.Technology
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseRouteTest {
    private val technology = Technology("tech-1", "core-java", "Core Java", null)
    private val category = Category("cat-1", "collections", "Collections", "core-java")

    @Test fun simpleRoutesRemainDistinct() {
        assertEquals(BrowseRoute.Home, BrowseRoute.Home)
        assertEquals(BrowseRoute.QuizSelection, BrowseRoute.QuizSelection)
        assertEquals(BrowseRoute.Authentication, BrowseRoute.Authentication)
    }

    @Test fun categoryAndLessonRoutesRetainTypedPayloads() {
        val categories = BrowseRoute.Categories(technology)
        val lessons = BrowseRoute.Lessons(category, technology)
        val lesson = BrowseRoute.Lesson("hashmap", "HashMap")

        assertEquals(technology, categories.technology)
        assertEquals(category, lessons.category)
        assertEquals(technology, lessons.technology)
        assertEquals("hashmap", lesson.slug)
        assertEquals("HashMap", lesson.title)
    }
}
