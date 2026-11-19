package com.jia.academy.auth.api;

import com.jia.academy.auth.api.AuthDtos.TokenResponse;
import com.jia.academy.auth.service.PhoneOtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/phone")
public class PhoneOtpController {
    private final PhoneOtpService otp;

    public PhoneOtpController(PhoneOtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse request(@Valid @RequestBody PhoneRequest request) {
        otp.requestCode(request.phoneNumber());
        return new MessageResponse("If the number can receive SMS, a verification code has been sent.");
    }

    @PostMapping("/verify")
    public TokenResponse verify(@Valid @RequestBody VerifyRequest request) {
        return otp.verify(request.phoneNumber(), request.code(), request.deviceName());
    }

    public record PhoneRequest(@NotBlank @Pattern(regexp="^\\+[1-9][0-9]{7,14}$") String phoneNumber) {}
    public record VerifyRequest(@NotBlank @Pattern(regexp="^\\+[1-9][0-9]{7,14}$") String phoneNumber,
                                @NotBlank @Pattern(regexp="^[0-9]{6}$") String code,
                                @Size(max=100) String deviceName) {}
    public record MessageResponse(String message) {}
}
