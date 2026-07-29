package com.java2nb.novel.service.ai;

public interface AuthorAiModelClient {
    String generate(String prompt);

    String modelName();
}
