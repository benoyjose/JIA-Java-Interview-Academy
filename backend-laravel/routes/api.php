<?php

use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\CatalogueController;
use App\Http\Middleware\AuthenticateJiaToken;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function (): void {
    Route::post('/auth/register', [AuthController::class, 'register'])->middleware('throttle:5,1');
    Route::post('/auth/login', [AuthController::class, 'login'])->middleware('throttle:5,1');
    Route::post('/auth/federated/google', [FederatedAuthController::class, 'google'])->middleware('throttle:10,1');
    Route::post('/auth/federated/apple', [FederatedAuthController::class, 'apple'])->middleware('throttle:10,1');
    Route::middleware(AuthenticateJiaToken::class)->group(function (): void {
        Route::get('/auth/me', [AuthController::class, 'me']);
        Route::post('/auth/logout', [AuthController::class, 'logout']);
    });
    Route::get('/technologies', [CatalogueController::class, 'technologies']);
    Route::get('/technologies/{slug}', [CatalogueController::class, 'technology']);
    Route::get('/technologies/{technologySlug}/categories', [CatalogueController::class, 'categories']);
    Route::get('/categories/{categorySlug}/lessons', [CatalogueController::class, 'lessons']);
    Route::get('/lessons/{slug}', [CatalogueController::class, 'lesson']);
});
