<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpException;

class AppReleasePolicyController
{
    public function show(Request $request, string $appCode)
    {
        $request->validate([
            'platform' => ['required', 'in:ANDROID,IOS'],
            'applicationId' => ['required', 'string', 'max:255'],
            'installedVersion' => ['nullable', 'regex:/^[0-9]{1,5}(\.[0-9]{1,5}){2}$/'],
        ]);
        $app = $this->find($appCode);
        if ($app->platform !== $request->string('platform')->toString()
            || $app->application_id !== $request->string('applicationId')->toString()) {
            abort(404, 'Registered app identity not found');
        }
        if (in_array($app->status, ['SUSPENDED', 'RETIRED'], true)) {
            abort(410, 'This application is no longer supported');
        }
        $updateStatus = 'CURRENT';
        $installed = $request->query('installedVersion');
        if ($installed && $app->minimum_supported_version
            && version_compare($installed, $app->minimum_supported_version, '<')) {
            $updateStatus = 'UPDATE_REQUIRED';
        } elseif ($installed && $app->current_version
            && version_compare($installed, $app->current_version, '<')) {
            $updateStatus = 'UPDATE_AVAILABLE';
        }
        return response()->json($this->response($app, $updateStatus));
    }

    public function update(Request $request, string $appCode)
    {
        $data = $request->validate([
            'status' => ['required', 'in:PLANNED,ACTIVE,SUSPENDED,RETIRED'],
            'minimumSupportedVersion' => ['nullable', 'regex:/^[0-9]{1,5}(\.[0-9]{1,5}){2}$/'],
            'currentVersion' => ['nullable', 'regex:/^[0-9]{1,5}(\.[0-9]{1,5}){2}$/'],
            'storeUrl' => ['nullable', 'url:https', 'max:1000'],
            'releaseNotes' => ['nullable', 'string', 'max:5000'],
        ]);
        if (($data['minimumSupportedVersion'] ?? null) && ($data['currentVersion'] ?? null)
            && version_compare($data['minimumSupportedVersion'], $data['currentVersion'], '>')) {
            throw ValidationException::withMessages([
                'minimumSupportedVersion' => 'Minimum supported version cannot exceed current version.',
            ]);
        }
        $app = $this->find($appCode);
        $wasReleased = $app->released_at !== null;
        DB::table('academy_apps')->where('app_code', $appCode)->update([
            'status' => $data['status'],
            'minimum_supported_version' => $data['minimumSupportedVersion'] ?? null,
            'current_version' => $data['currentVersion'] ?? null,
            'store_url' => $data['storeUrl'] ?? null,
            'release_notes' => $data['releaseNotes'] ?? null,
            'released_at' => $data['status'] === 'ACTIVE' ? ($wasReleased ? $app->released_at : now()) : null,
            'updated_at' => now(),
        ]);
        $updated = $this->find($appCode);
        return response()->json($this->response($updated, 'CURRENT'));
    }

    private function find(string $appCode): object
    {
        if (!preg_match('/^[A-Z][A-Z0-9_]{2,39}$/', $appCode)) {
            abort(404, 'Registered app not found');
        }
        return DB::table('academy_apps')->where('app_code', $appCode)->first()
            ?? abort(404, 'Registered app not found');
    }

    private function response(object $app, string $updateStatus): array
    {
        return [
            'appCode' => $app->app_code,
            'platform' => $app->platform,
            'applicationId' => $app->application_id,
            'displayName' => $app->display_name,
            'status' => $app->status,
            'minimumSupportedVersion' => $app->minimum_supported_version,
            'currentVersion' => $app->current_version,
            'storeUrl' => $app->store_url,
            'releaseNotes' => $app->release_notes,
            'releasedAt' => $app->released_at,
            'updateStatus' => $updateStatus,
        ];
    }
}
