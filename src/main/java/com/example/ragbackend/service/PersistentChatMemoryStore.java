package com.example.ragbackend.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.ragbackend.entity.ChatMessageEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PersistentChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    // 每次对话开始前，LangChain4j 自动调用此方法获取历史
    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        Long sessionId = (Long) memoryId;
        log.info("=== 获取会话历史 === Session ID: {}", sessionId);

        List<ChatMessageEntity> dbMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreateTime)
//                        .last("LIMIT 20")
        );

        log.debug("从数据库查询到 {} 条历史记录", dbMessages.size());
//        Collections.reverse(dbMessages);
        System.out.println("Session " + sessionId + " 查到历史记录条数: " + dbMessages.size());

        if (dbMessages == null || dbMessages.isEmpty()) {
            log.info("首次对话，无历史记录");
            return new ArrayList<>();
        }

        List<dev.langchain4j.data.message.ChatMessage> result = dbMessages.stream().map(m -> {
            if ("user".equalsIgnoreCase(m.getRole())) {
                log.debug("历史用户消息: {}", m.getContent());
                return UserMessage.from(m.getContent());
            } else {
                log.debug("历史AI消息: {}", m.getContent());
                return AiMessage.from(m.getContent());
            }
        }).collect(Collectors.toCollection(ArrayList::new));

        log.info("准备发送给 Ollama 的消息数: {}", result.size());
        return result;
    }

    // 每次对话结束后，LangChain4j 自动调用此方法保存新产生的消息
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long sessionId = (Long) memoryId;
        log.info("=== 保存对话消息 === Session ID: {}, 消息总数: {}", sessionId, messages.size());

        if (messages.isEmpty()) {
            log.warn("消息列表为空，跳过保存");
            return;
        }

        ChatMessage msg = messages.get(messages.size() - 1);
        log.debug("处理最后一条消息，类型: {}", msg.getClass().getSimpleName());

        if (!(msg instanceof UserMessage || msg instanceof AiMessage)) {
            log.warn("消息类型不支持: {}", msg.getClass().getName());
            return;
        }

        String role = (msg instanceof UserMessage) ? "user" : "assistant";
        String content = (msg instanceof UserMessage)
                ? ((UserMessage) msg).singleText()
                : ((AiMessage) msg).text();

        if (content == null || content.trim().isEmpty()) {
            log.warn("消息内容为空，跳过保存");
            return;
        }

        log.info("保存消息 - 角色: {}, 内容长度: {}", role, content.length());
        log.debug("消息内容: {}", content);

        log.info("保存消息 - 角色: {}, 内容长度: {}", role, content.length());
        log.debug("消息内容: {}", content);

        if ("assistant".equalsIgnoreCase(role)) {
            log.info("🤖 Ollama回复: {}", content);
        } else {
            log.info("👤 用户提问: {}", content);
        }

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreateTime(LocalDateTime.now());

        chatMessageMapper.insert(entity);
        log.info("消息已保存到数据库，ID: {}", entity.getId());

        // 核心优化：更新会话的最后活跃时间，以便前端置顶
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        log.info("会话 {} 的最后活跃时间已更新", sessionId);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Long sessionId = (Long) memoryId;
        log.info("=== 删除会话消息 === Session ID: {}", sessionId);

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, memoryId));

        log.info("会话 {} 的消息已删除", sessionId);
    }
}
