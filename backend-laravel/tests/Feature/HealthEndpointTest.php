<?php

namespace Tests\Feature;

use Tests\TestCase;

class HealthEndpointTest extends TestCase
{
    public function test_liveness_is_public_and_does_not_depend_on_database(): void
    {
        $this->getJson('/api/v1/health/live')
            ->assertOk()
            ->assertExactJson(['status' => 'UP']);
    }

    public function test_readiness_reports_database_query_success(): void
    {
        $this->getJson('/api/v1/health/ready')
            ->assertOk()
            ->assertExactJson(['status' => 'READY']);
    }

    public function test_health_response_does_not_return_diagnostic_secrets(): void
    {
        $response = $this->getJson('/api/v1/health/live')->assertOk();
        $this->assertStringNotContainsString('password', strtolower($response->getContent()));
        $this->assertStringNotContainsString('database', strtolower($response->getContent()));
    }
}
