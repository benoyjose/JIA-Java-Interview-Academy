<?php

return [
    'jia' => [
        'google_client_id' => env('GOOGLE_OAUTH_CLIENT_ID'),
        'apple_client_id' => env('APPLE_SERVICE_ID'),
        'sms_endpoint' => env('JIA_SMS_ENDPOINT'),
        'sms_token' => env('JIA_SMS_TOKEN'),
        'otp_hmac_secret' => env('JIA_OTP_HMAC_SECRET'),
    ],
];
