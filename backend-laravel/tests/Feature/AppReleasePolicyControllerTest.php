<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\V1\AppReleasePolicyController;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Tests\TestCase;

final class AppReleasePolicyControllerTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        Schema::dropIfExists('academy_apps');
        Schema::create('academy_apps', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('academy_id');
            $table->string('app_code', 40)->unique();
            $table->string('platform', 12);
            $table->string('application_id', 255);
            $table->string('display_name', 160);
            $table->string('status', 20);
            $table->string('minimum_supported_version', 40)->nullable();
            $table->string('current_version', 40)->nullable();
            $table->string('store_url', 1000)->nullable();
            $table->text('release_notes')->nullable();
            $table->timestampTz('released_at')->nullable();
            $table->timestampsTz();
        });
        DB::table('academy_apps')->insert([
            'id' => '60000000-0000-0000-0000-000000000001',
            'academy_id' => '50000000-0000-0000-0000-000000000001',
            'app_code' => 'JIA_ANDROID',
            'platform' => 'ANDROID',
            'application_id' => 'com.jia.academy.app',
            'display_name' => 'JIA Android',
            'status' => 'PLANNED',
            'minimum_supported_version' => '1.2.0',
            'current_version' => '1.4.0',
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    }

    public function test_public_policy_checks_registry_identity_and_minimum_version(): void
    {
        $controller = new AppReleasePolicyController();
        $request = Request::create('/api/v1/apps/JIA_ANDROID/release-policy', 'GET', [
            'platform' => 'ANDROID',
            'applicationId' => 'com.jia.academy.app',
            'installedVersion' => '1.0.0',
        ]);
        $response = $controller->show($request, 'JIA_ANDROID');
        self::assertSame('UPDATE_REQUIRED', $response->getData()->updateStatus);
        self::assertSame('PLANNED', $response->getData()->status);

        $wrongIdentity = Request::create('/api/v1/apps/JIA_ANDROID/release-policy', 'GET', [
            'platform' => 'IOS',
            'applicationId' => 'com.jia.academy.app',
        ]);
        $this->expectException(HttpException::class);
        $controller->show($wrongIdentity, 'JIA_ANDROID');
    }

    public function test_release_update_suspends_app_without_affecting_registry_lookup(): void
    {
        $controller = new AppReleasePolicyController();
        $request = Request::create('/api/v1/apps/JIA_ANDROID/release-policy', 'PUT', [
            'status' => 'SUSPENDED',
            'minimumSupportedVersion' => '1.2.0',
            'currentVersion' => '1.4.0',
        ]);
        $response = $controller->update($request, 'JIA_ANDROID');
        self::assertSame('SUSPENDED', $response->getData()->status);
        $this->expectException(HttpException::class);
        $controller->show(Request::create('/policy', 'GET', [
            'platform' => 'ANDROID',
            'applicationId' => 'com.jia.academy.app',
        ]), 'JIA_ANDROID');
    }
}
