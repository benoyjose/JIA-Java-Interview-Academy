package com.jia.academy.app.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogueLoadState<T>(
    val loading: Boolean = false,
    val data: T? = null,
    val error: String? = null
)

class CatalogueViewModel(private val repository: CatalogueRepository) : ViewModel() {
    private val _technologies = MutableStateFlow(CatalogueLoadState<List<Technology>>(loading = true))
    val technologies: StateFlow<CatalogueLoadState<List<Technology>>> = _technologies.asStateFlow()

    private val _categories = MutableStateFlow(CatalogueLoadState<List<Category>>())
    val categories: StateFlow<CatalogueLoadState<List<Category>>> = _categories.asStateFlow()
    private var categoriesFor: String? = null

    private val _lessons = MutableStateFlow(CatalogueLoadState<List<LessonSummary>>())
    val lessons: StateFlow<CatalogueLoadState<List<LessonSummary>>> = _lessons.asStateFlow()
    private var lessonsFor: String? = null

    fun loadTechnologies(baseUrl: String, force: Boolean = false) {
        if (!force && _technologies.value.data != null) return
        viewModelScope.launch {
            _technologies.value = _technologies.value.copy(loading = true, error = null)
            runCatching { repository.getTechnologies(baseUrl) }
                .onSuccess { _technologies.value = CatalogueLoadState(data = it) }
                .onFailure { _technologies.value = CatalogueLoadState(data = _technologies.value.data,
                    error = it.message ?: "Unable to load technologies.") }
        }
    }

    fun loadCategories(baseUrl: String, technologySlug: String, force: Boolean = false) {
        if (!force && categoriesFor == technologySlug && _categories.value.data != null) return
        categoriesFor = technologySlug
        viewModelScope.launch {
            _categories.value = CatalogueLoadState(loading = true)
            runCatching { repository.getCategories(baseUrl, technologySlug) }
                .onSuccess { _categories.value = CatalogueLoadState(data = it) }
                .onFailure { _categories.value = CatalogueLoadState(error = it.message ?: "Unable to load categories.") }
        }
    }

    fun loadLessons(baseUrl: String, categorySlug: String, force: Boolean = false) {
        if (!force && lessonsFor == categorySlug && _lessons.value.data != null) return
        lessonsFor = categorySlug
        viewModelScope.launch {
            _lessons.value = CatalogueLoadState(loading = true)
            runCatching { repository.getLessons(baseUrl, categorySlug) }
                .onSuccess { _lessons.value = CatalogueLoadState(data = it) }
                .onFailure { _lessons.value = CatalogueLoadState(error = it.message ?: "Unable to load lessons.") }
        }
    }
}

class CatalogueViewModelFactory(
    private val repository: CatalogueRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CatalogueViewModel::class.java)) {
            return CatalogueViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
