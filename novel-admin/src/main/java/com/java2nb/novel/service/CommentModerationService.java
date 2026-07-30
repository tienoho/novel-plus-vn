package com.java2nb.novel.service;

public interface CommentModerationService {

    int batchAudit(Long[] ids, byte auditStatus);
}
