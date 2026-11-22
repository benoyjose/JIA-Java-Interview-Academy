package com.jia.academy.app.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jia.academy.app.catalogue.CatalogueRepository
import com.jia.academy.app.catalogue.LessonDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class LessonLoadState(val loading: Boolean = false, val lesson: LessonDetail? = null, val error: String? = null)

class LessonViewModel(private val repository: CatalogueRepository) : ViewModel() {
    private val _state = MutableStateFlow(LessonLoadState())
    val state: StateFlow<LessonLoadState> = _state.asStateFlow()
    private var loadedSlug: String? = null
    private var loadJob: Job? = null
    private val requestGuard = LessonLoadRequestGuard()

    fun load(baseUrl: String, slug: String, force: Boolean = false) {
        if (!force && loadedSlug == slug && _state.value.lesson != null) return
        loadedSlug = slug
        val generation = requestGuard.begin()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = LessonLoadState(loading = true, lesson = _state.value.lesson)
            runCatching { repository.getLesson(baseUrl, slug) }
                .onSuccess {
                    if (requestGuard.isCurrent(generation)) _state.value = LessonLoadState(lesson = it)
                }
                .onFailure {
                    if (requestGuard.isCurrent(generation) && it !is kotlinx.coroutines.CancellationException) {
                        _state.value = LessonLoadState(error = it.message ?: "Unable to load lesson.")
                    }
                }
        }
    }
}

class LessonViewModelFactory(private val repository: CatalogueRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LessonViewModel::class.java)) return LessonViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}

/** Small deterministic guard used to prevent an older network response replacing newer route state. */
internal class LessonLoadRequestGuard {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun isCurrent(candidate: Long): Boolean = candidate == generation
}
