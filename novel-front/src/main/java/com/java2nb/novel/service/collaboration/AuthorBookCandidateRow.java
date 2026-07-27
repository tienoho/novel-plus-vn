package com.java2nb.novel.service.collaboration;

import lombok.Data;

@Data
public class AuthorBookCandidateRow {
    private Long authorId;
    private Long userId;
    private String username;
    private String penName;
}
