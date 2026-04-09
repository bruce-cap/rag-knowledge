package com.example.ragbackend.service;

import com.example.ragbackend.entity.ChatMessage;
import com.example.ragbackend.entity.ChatSession;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.model.dto.ChatRequestDTO;
import com.example.ragbackend.model.vo.ChatResponseVO;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ChatService {

    @Autowired
    @Qualifier("ollamaChatLanguageModel")
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    public ChatResponseVO chat(ChatRequestDTO request) {
        Long sessionId = request.getSessionId();

        // 1. 如果没有sessionId，创建一个新会话
        if (sessionId == null) {
            ChatSession session = new ChatSession();
            session.setUserId(1L); // TODO: 从 SecurityContext 获取
            session.setTitle("新对话");
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
            sessionId = session.getId();
        }

        // 2. 保存用户消息到数据库
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        userMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);

        // 3. 调用 LLM 获取回复
        String aiResponse = chatLanguageModel.generate(request.getMessage());


        // 4. 保存 AI 回复到数据库
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(aiResponse);
        assistantMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(assistantMsg);

        // 5. 更新会话时间
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 6. 返回响应
        return new ChatResponseVO(aiResponse, sessionId, assistantMsg.getId());
    }
}
