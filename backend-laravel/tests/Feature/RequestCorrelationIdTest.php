<?php

namespace Tests\Feature;

use App\Http\Middleware\RequestCorrelationId;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;
use Tests\TestCase;

class RequestCorrelationIdTest extends TestCase
{
    public function test_safe_client_id_is_returned(): void
    {
        $request = Request::create('/api/ping');
        $request->headers->set('X-Request-Id', 'trace-456');

        $response = (new RequestCorrelationId())->handle(
            $request,
            fn (Request $request) => new Response('ok')
        );

        $this->assertSame('trace-456', $response->headers->get('X-Request-Id'));
        $this->assertSame('trace-456', $request->attributes->get('request_id'));
    }

    public function test_invalid_client_id_is_replaced(): void
    {
        $request = Request::create('/api/ping');
        $request->headers->set('X-Request-Id', "bad id\r\nInjected: yes");

        $response = (new RequestCorrelationId())->handle(
            $request,
            fn (Request $request) => new Response('ok')
        );

        $this->assertMatchesRegularExpression('/^[0-9a-f-]{36}$/', $response->headers->get('X-Request-Id'));
    }
}
