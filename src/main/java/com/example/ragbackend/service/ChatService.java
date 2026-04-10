package com.example.ragbackend.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;


public interface ChatService {

    @SystemMessage("""
    你是一个专业的知识库助手。
    如果没有上下文，也要根据用户问题进行回答。
    """)
    String chat(@UserMessage String message);

    @SystemMessage("""
    你是一个专业的知识库助手。
    如果没有上下文，也要根据用户问题进行回答。
    """)
    TokenStream chatStream(@UserMessage String message);
}