package com.jia.academy.app.assessment

enum class ReviewAction { REVIEW, RETRY, ADVANCE }

data class PracticeReviewInput(
    val accuracyPercent: Int,
    val confidencePercent: Int,
    val elapsedSeconds: Int,
    val minimumSeconds: Int = 1
)

object PracticeReviewGate {
    fun decide(input: PracticeReviewInput): ReviewAction {
        require(input.accuracyPercent in 0..100)
        require(input.confidencePercent in 0..100)
        require(input.elapsedSeconds >= 0 && input.minimumSeconds >= 0)
        return when {
            input.accuracyPercent < 60 -> ReviewAction.RETRY
            input.accuracyPercent < 80 || input.confidencePercent < 45 -> ReviewAction.REVIEW
            input.elapsedSeconds < input.minimumSeconds -> ReviewAction.REVIEW
            else -> ReviewAction.ADVANCE
        }
    }
}
