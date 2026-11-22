package com.jia.academy.app.navigation

import com.jia.academy.app.catalogue.Category
import com.jia.academy.app.catalogue.Technology

sealed interface BrowseRoute {
    data object Home : BrowseRoute
    data object QuizSelection : BrowseRoute
    data object Authentication : BrowseRoute
    data class Categories(val technology: Technology) : BrowseRoute
    data class Lessons(val category: Category, val technology: Technology) : BrowseRoute
    data class Lesson(val slug: String, val title: String) : BrowseRoute
}
