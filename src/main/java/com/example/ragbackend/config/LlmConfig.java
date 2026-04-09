package com.example.ragbackend.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {


//    // 先不用这个，先用本地ollama
//    @Bean
//    public ChatLanguageModel chatLanguageModel() {
//        return OpenAiChatModel.builder()
//                .apiKey("sk-b2b8cafb6e6b45e3a221c4e721dce7ad")
//                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
//                .modelName("qwen-plus")
//                .build();
//    }


}
