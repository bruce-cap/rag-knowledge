package com.example.ragbackend.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService // 如果依赖正确，这里会自动变为紫色/高亮
public interface ChatService {

    @SystemMessage("你是一个专业的知识库助手，请简洁明了地回答用户问题。")
    String chat(String userMessage);
}