package com.java2nb.novel.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình liên quan đến AI
 *
 * @author xiongxiaoyang
 * @date 2025/2/19
 */
@Configuration
@Slf4j
public class AiConfig {

    /**
     * Cấu hình đối tượng RestClientBuilder tùy chỉnh
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Thời gian chờ kết nối
        factory.setConnectTimeout(5000);
        // Thời gian chờ đọc
        factory.setReadTimeout(60000);
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

}
