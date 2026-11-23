<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Support\Facades\DB;
use Throwable;

final class HealthController
{
    public function live()
    {
        return response()->json(['status' => 'UP']);
    }

    public function ready()
    {
        try {
            DB::select('SELECT 1');
            return response()->json(['status' => 'READY']);
        } catch (Throwable) {
            return response()->json(['status' => 'NOT_READY'], 503);
        }
    }
}
