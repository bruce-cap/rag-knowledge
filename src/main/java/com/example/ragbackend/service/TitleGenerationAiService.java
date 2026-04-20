package com.example.ragbackend.service;

import dev.langchain4j.service.UserMessage;

public interface TitleGenerationAiService {

    @UserMessage("{{it}}")
    String generateTitle(String prompt);
}
