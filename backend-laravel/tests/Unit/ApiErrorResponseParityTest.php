<?php

namespace Tests\Unit;

use App\Support\ApiErrorResponse;
use PHPUnit\Framework\TestCase;

final class ApiErrorResponseParityTest extends TestCase
{
    public function test_laravel_status_codes_match_shared_cross_backend_fixtures(): void
    {
        $fixturePath = dirname(__DIR__, 3).'/docs/api/error-response-parity.json';
        $fixture = json_decode((string) file_get_contents($fixturePath), true, flags: JSON_THROW_ON_ERROR);

        foreach ($fixture['cases'] as $case) {
            self::assertSame(
                $case['code'],
                ApiErrorResponse::codeForStatus($case['status']),
                'Unexpected API error code for HTTP '.$case['status'],
            );
        }
    }

    public function test_unlisted_client_error_uses_generic_http_error_code(): void
    {
        self::assertSame('HTTP_ERROR', ApiErrorResponse::codeForStatus(418));
    }
}
