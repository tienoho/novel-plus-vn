package com.java2nb.novel.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpringAuthorAiModelClient implements AuthorAiModelClient {
    private final ChatClient chatClient;
    private final String modelName;

    public SpringAuthorAiModelClient(
        ChatClient chatClient,
        @Value("${spring.ai.openai.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClient;
        this.modelName = modelName;
    }

    @Override
    public String generate(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
