package com.jia.academy.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class FederatedJwtConfiguration {
    @Bean("googleIdTokenDecoder")
    JwtDecoder googleIdTokenDecoder(
            @Value("${jia.auth.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwks,
            @Value("${jia.auth.google.issuer:https://accounts.google.com}") String issuer) {
        return decoder(jwks, issuer);
    }

    @Bean("appleIdTokenDecoder")
    JwtDecoder appleIdTokenDecoder(
            @Value("${jia.auth.apple.jwk-set-uri:https://appleid.apple.com/auth/keys}") String jwks,
            @Value("${jia.auth.apple.issuer:https://appleid.apple.com}") String issuer) {
        return decoder(jwks, issuer);
    }

    private JwtDecoder decoder(String jwks, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
