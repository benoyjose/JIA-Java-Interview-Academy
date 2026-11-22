<?php

use App\Http\Middleware\RequireJiaPermission;
use App\Support\ApiErrorResponse;
use Illuminate\Auth\Access\AuthorizationException;
use Illuminate\Auth\AuthenticationException;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;
use Throwable;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        api: __DIR__.'/../routes/api.php',
        health: '/up',
    )
    ->withCommands([ValidateCurriculumImport::class])
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->alias([
            'jia.permission' => RequireJiaPermission::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $isApi = static fn (Request $request): bool => $request->is('api/*');

        $exceptions->render(function (ValidationException $exception, Request $request) use ($isApi) {
            if (! $isApi($request)) {
                return null;
            }

            return ApiErrorResponse::make(
                'VALIDATION_ERROR',
                'The request contains invalid fields.',
                422,
                $exception->errors(),
            );
        });

        $exceptions->render(function (AuthenticationException $exception, Request $request) use ($isApi) {
            return $isApi($request) ? ApiErrorResponse::forStatus(401) : null;
        });

        $exceptions->render(function (AuthorizationException $exception, Request $request) use ($isApi) {
            return $isApi($request) ? ApiErrorResponse::forStatus(403) : null;
        });

        $exceptions->render(function (ModelNotFoundException $exception, Request $request) use ($isApi) {
            return $isApi($request) ? ApiErrorResponse::forStatus(404) : null;
        });

        $exceptions->render(function (HttpExceptionInterface $exception, Request $request) use ($isApi) {
            if (! $isApi($request)) {
                return null;
            }

            return ApiErrorResponse::forStatus($exception->getStatusCode());
        });

        $exceptions->render(function (Throwable $exception, Request $request) use ($isApi) {
            return $isApi($request) ? ApiErrorResponse::forStatus(500) : null;
        });
    })->create();
