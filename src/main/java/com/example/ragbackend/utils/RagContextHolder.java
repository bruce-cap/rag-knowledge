package com.example.ragbackend.utils;

public class RagContextHolder {
    private static final ThreadLocal<Boolean> RAG_MODE_HOLDER = new ThreadLocal<>();

    public static void setRagMode(Boolean mode) {
        RAG_MODE_HOLDER.set(mode == null ? true : mode); // 默认为 true
    }

    public static boolean isRagMode() {
        Boolean mode = RAG_MODE_HOLDER.get();
        return mode == null ? true : mode; // 默认为 true
    }

    public static void clear() {
        RAG_MODE_HOLDER.remove();
    }
}
