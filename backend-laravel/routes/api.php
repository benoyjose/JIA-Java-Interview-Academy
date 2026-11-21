<?php

use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\FederatedAuthController;
use App\Http\Controllers\Api\V1\PhoneOtpController;
use App\Http\Controllers\Api\V1\CatalogueController;
use App\Http\Controllers\Api\V1\QuizController;
use App\Http\Controllers\Api\V1\LearnerLibraryController;
use App\Http\Controllers\Api\V1\LessonImportController;
use App\Http\Controllers\Api\V1\QuestionImportController;
use App\Http\Controllers\Api\V1\AppReleasePolicyController;
use App\Http\Middleware\AuthenticateJiaToken;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function (): void {
    Route::get('/apps/{appCode}/release-policy', [AppReleasePolicyController::class, 'show']);
    Route::post('/auth/register', [AuthController::class, 'register'])->middleware('throttle:5,1');
    Route::post('/auth/login', [AuthController::class, 'login'])->middleware('throttle:5,1');
    Route::post('/auth/federated/google', [FederatedAuthController::class, 'google'])->middleware('throttle:10,1');
    Route::post('/auth/federated/apple', [FederatedAuthController::class, 'apple'])->middleware('throttle:10,1');
    Route::post('/auth/phone/otp', [PhoneOtpController::class, 'request'])->middleware('throttle:5,1');
    Route::post('/auth/phone/verify', [PhoneOtpController::class, 'verify'])->middleware('throttle:10,1');

    Route::middleware(AuthenticateJiaToken::class)->group(function (): void {
        Route::get('/auth/me', [AuthController::class, 'me']);
        Route::post('/auth/logout', [AuthController::class, 'logout']);
        Route::post('/quiz-attempts', [QuizController::class, 'start']);
        Route::put('/apps/{appCode}/release-policy', [AppReleasePolicyController::class, 'update'])->middleware('jia.permission:releases.manage');
        Route::post('/admin/lessons/import', [LessonImportController::class, 'import'])->middleware('jia.permission:content.create');
        Route::post('/admin/questions/import', [QuestionImportController::class, 'import'])->middleware('jia.permission:content.create');
        Route::post('/quiz-attempts/{attemptId}/answers', [QuizController::class, 'saveAnswers']);
        Route::post('/quiz-attempts/{attemptId}/submit', [QuizController::class, 'submit']);
        Route::get('/me/quiz-performance', [QuizController::class, 'performance']);
        Route::get('/me/quiz-attempts', [QuizController::class, 'history']);
        Route::get('/me/lesson-progress', [LearnerLibraryController::class, 'progress']);
        Route::put('/me/lesson-progress/{lessonSlug}', [LearnerLibraryController::class, 'updateProgress']);
        Route::get('/me/bookmarks', [LearnerLibraryController::class, 'bookmarks']);
        Route::put('/me/bookmarks/{lessonSlug}', [LearnerLibraryController::class, 'bookmark']);
        Route::delete('/me/bookmarks/{lessonSlug}', [LearnerLibraryController::class, 'removeBookmark']);
    });

    Route::get('/technologies', [CatalogueController::class, 'technologies']);
    Route::get('/technologies/{slug}', [CatalogueController::class, 'technology']);
    Route::get('/technologies/{technologySlug}/categories', [CatalogueController::class, 'categories']);
    Route::get('/categories/{categorySlug}/lessons', [CatalogueController::class, 'lessons']);
    Route::get('/lessons/{slug}', [CatalogueController::class, 'lesson']);
    Route::get('/quizzes/availability', [QuizController::class, 'availability']);
});
