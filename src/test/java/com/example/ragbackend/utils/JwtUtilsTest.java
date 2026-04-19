package com.example.ragbackend.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    @Test
    void createTokenShouldContainExpectedClaims() {
        String token = JwtUtils.createToken("alice", 7L, "ADMIN");

        assertTrue(JwtUtils.validateToken(token));
        assertEquals("alice", JwtUtils.getUsernameFromToken(token));
        assertEquals(7L, JwtUtils.getUserIdFromToken(token));
        assertEquals("ADMIN", JwtUtils.getRoleFromToken(token));
    }
}
