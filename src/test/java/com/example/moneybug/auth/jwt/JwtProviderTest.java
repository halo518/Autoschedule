package com.example.moneybug.auth.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripsUserId() {
        JwtProvider provider = new JwtProvider(SECRET, 60);
        String token = provider.createAccessToken(42L);
        assertEquals(42L, provider.parseUserId(token).orElseThrow());
    }

    @Test
    void rejectsExpiredAndTamperedTokens() {
        assertTrue(new JwtProvider(SECRET, -1).parseUserId(new JwtProvider(SECRET, -1).createAccessToken(1L)).isEmpty());

        JwtProvider provider = new JwtProvider(SECRET, 60);
        String token = provider.createAccessToken(1L);
        assertTrue(provider.parseUserId(token + "x").isEmpty());
        assertTrue(new JwtProvider("another-secret-another-secret-12", 60).parseUserId(token).isEmpty());
    }
}
