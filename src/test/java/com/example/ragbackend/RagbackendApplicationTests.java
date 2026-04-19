package com.example.ragbackend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RagbackendApplicationTests {

    @Test
    void applicationClassLoads() {
        assertNotNull(RagbackendApplication.class);
    }
}
