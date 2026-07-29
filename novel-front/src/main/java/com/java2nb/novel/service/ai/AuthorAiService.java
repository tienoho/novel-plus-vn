package com.java2nb.novel.service.ai;

import com.java2nb.novel.dto.author.AuthorAiRequest;

public interface AuthorAiService {
    String generate(long actorAuthorId, AuthorAiOperation operation, AuthorAiRequest input);
}
