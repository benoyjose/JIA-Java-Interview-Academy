package com.jia.academy.app.review

import java.time.Instant
import java.time.temporal.ChronoUnit

data class ReviewCard(val id: String, val prompt: String, val answer: String, val dueAt: Instant)
data class ReviewGrade(val cardId: String, val grade: Int, val reviewedAt: Instant, val dueAt: Instant)

data class ReviewSessionState(
    val cards: List<ReviewCard>,
    val index: Int = 0,
    val answerVisible: Boolean = false,
    val completed: List<ReviewGrade> = emptyList()
) {
    val current: ReviewCard? get() = cards.getOrNull(index)
    val isComplete: Boolean get() = index >= cards.size
    fun reveal(): ReviewSessionState = if (isComplete) this else copy(answerVisible = true)
    fun grade(value: Int, at: Instant): ReviewSessionState {
        require(value in 0..5) { "Grade must be 0..5" }
        val card = current ?: return this
        require(answerVisible) { "Reveal the answer before grading" }
        val due = at.plus(if (value < 3) 1 else when (completed.count { it.cardId == card.id }) {
            0 -> 1; 1 -> 6; else -> 14
        }, ChronoUnit.DAYS)
        return copy(index = index + 1, answerVisible = false,
            completed = completed + ReviewGrade(card.id, value, at, due))
    }
}
