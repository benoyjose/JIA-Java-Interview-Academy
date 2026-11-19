<?php

use App\Http\Controllers\Api\V1\CatalogueController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function (): void {
    Route::get('/technologies', [CatalogueController::class, 'technologies']);
    Route::get('/technologies/{slug}', [CatalogueController::class, 'technology']);
    Route::get('/technologies/{technologySlug}/categories', [CatalogueController::class, 'categories']);
    Route::get('/categories/{categorySlug}/lessons', [CatalogueController::class, 'lessons']);
    Route::get('/lessons/{slug}', [CatalogueController::class, 'lesson']);
});
