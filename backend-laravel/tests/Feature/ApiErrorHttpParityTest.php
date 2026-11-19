<?php

namespace Tests\Feature;

use App\Http\Middleware\RequireJiaPermission;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Validator;
use Symfony\Component\HttpKernel\Exception\BadRequestHttpException;
use Tests\TestCase;

final class ApiErrorHttpParityTest extends TestCase
{
    protected function setUp(): void
    {
        putenv('APP_ENV=testing');
        putenv('APP_DEBUG=false');
        putenv('APP_KEY=base64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=');

        parent::setUp();

        config(['app.debug' => false]);

        Route::post('/api/__parity/validation', function (Request $request) {
            Validator::make($request->all(), ['name' => ['required', 'string']])->validate();

            return response()->json(['ok' => true]);
        });

        Route::post('/api/__parity/bad-request', function () {
            throw new BadRequestHttpException('Private malformed request detail');
        });

        Route::get('/api/__parity/forbidden', function () {
            return response()->json(['ok' => true]);
        })->middleware(RequireJiaPermission::class.':test:permission');

        Route::get('/api/__parity/unexpected', function () {
            throw new \RuntimeException('database password must never leak');
        });
    }

    public function test_missing_authentication_returns_contract_401(): void
    {
        $this->getJson('/api/v1/auth/me')
            ->assertUnauthorized()
            ->assertJsonPath('code', 'UNAUTHENTICATED')
            ->assertJsonPath('message', 'Authentication is required.');
    }

    public function test_permission_denial_returns_contract_403(): void
    {
        $this->getJson('/api/__parity/forbidden')
            ->assertForbidden()
            ->assertJsonPath('code', 'FORBIDDEN')
            ->assertJsonPath('message', 'You do not have permission to perform this action.');
    }

    public function test_validation_failure_returns_contract_422_and_field_errors(): void
    {
        $this->postJson('/api/__parity/validation', ['name' => ''])
            ->assertUnprocessable()
            ->assertJsonPath('code', 'VALIDATION_ERROR')
            ->assertJsonPath('message', 'The request contains invalid fields.')
            ->assertJsonStructure(['code', 'message', 'errors' => ['name']]);
    }

    public function test_bad_request_returns_contract_400(): void
    {
        $this->postJson('/api/__parity/bad-request')
            ->assertBadRequest()
            ->assertJsonPath('code', 'BAD_REQUEST');
    }

    public function test_missing_route_returns_contract_404(): void
    {
        $this->getJson('/api/__parity/does-not-exist')
            ->assertNotFound()
            ->assertJsonPath('code', 'NOT_FOUND');
    }

    public function test_unexpected_exception_hides_internal_details(): void
    {
        $this->getJson('/api/__parity/unexpected')
            ->assertInternalServerError()
            ->assertJsonPath('code', 'INTERNAL_ERROR')
            ->assertJsonPath('message', 'An unexpected server error occurred.')
            ->assertDontSee('database password');
    }
}
