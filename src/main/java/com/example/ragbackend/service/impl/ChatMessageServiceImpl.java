package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.service.ChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity> implements ChatMessageService {
}
