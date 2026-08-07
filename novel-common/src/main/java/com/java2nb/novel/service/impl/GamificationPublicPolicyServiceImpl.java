package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationPublicPolicyMapper;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GamificationPublicPolicyServiceImpl implements GamificationPublicPolicyService {

    private final GamificationPublicPolicyMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public GamificationPublicPolicyRow getPublished() {
        return mapper.selectPublished();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationPublicPolicyRow createDraft(String policyVersion, String title,
                                                    String contentText, long operatorId) {
        String normalizedVersion = normalizeVersion(policyVersion);
        String normalizedTitle = normalize(title, 5, 160, "Tiêu đề chính sách");
        String normalizedContent = normalize(contentText, 100, 20_000, "Nội dung chính sách");
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Quản trị viên tạo chính sách không hợp lệ");
        }
        GamificationPublicPolicyRow existing = mapper.selectByVersion(normalizedVersion);
        if (existing != null) {
            if (!"DRAFT".equals(existing.getStatus())
                || !Objects.equals(existing.getTitle(), normalizedTitle)
                || !Objects.equals(existing.getContentText(), normalizedContent)) {
                throw new IllegalStateException("Phiên bản chính sách đã tồn tại với nội dung khác");
            }
            return existing;
        }
        if (mapper.insertDraft(normalizedVersion, normalizedTitle, normalizedContent) != 1) {
            throw new IllegalStateException("Không thể tạo bản nháp chính sách gamification");
        }
        GamificationPublicPolicyRow created = mapper.selectByVersion(normalizedVersion);
        if (created == null || mapper.insertAudit(created.getId(), "CREATED", null,
            "DRAFT", operatorId) != 1) {
            throw new IllegalStateException("Không thể audit bản nháp chính sách gamification");
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationPublicPolicyRow publish(long policyId, long expectedVersion,
                                               long operatorId, Date publishedAt) {
        if (policyId <= 0 || expectedVersion < 0 || operatorId <= 0 || publishedAt == null) {
            throw new IllegalArgumentException("Yêu cầu phát hành chính sách không hợp lệ");
        }
        GamificationPublicPolicyRow target = mapper.lockById(policyId);
        if (target == null) {
            throw new IllegalArgumentException("Chính sách gamification không tồn tại");
        }
        if ("PUBLISHED".equals(target.getStatus())) {
            return target;
        }
        if (!"DRAFT".equals(target.getStatus())
            || !Objects.equals(target.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Chính sách đã đổi trạng thái hoặc version");
        }
        GamificationPublicPolicyRow current = mapper.selectPublished();
        if (current != null) {
            if (mapper.archivePublished(publishedAt) != 1
                || mapper.insertAudit(current.getId(), "ARCHIVED", "PUBLISHED",
                    "ARCHIVED", operatorId) != 1) {
                throw new IllegalStateException("Không thể lưu trữ chính sách đang phát hành");
            }
        }
        if (mapper.publish(policyId, expectedVersion, operatorId, publishedAt) != 1
            || mapper.insertAudit(policyId, "PUBLISHED", "DRAFT", "PUBLISHED", operatorId) != 1) {
            throw new IllegalStateException("Không thể phát hành chính sách gamification");
        }
        GamificationPublicPolicyRow published = mapper.selectById(policyId);
        if (published == null || !"PUBLISHED".equals(published.getStatus())) {
            throw new IllegalStateException("Không đọc được chính sách sau phát hành");
        }
        return published;
    }

    private String normalizeVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
            throw new IllegalArgumentException("Phiên bản chính sách không hợp lệ");
        }
        return normalized;
    }

    private String normalize(String value, int min, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(field + " phải dài từ " + min + " đến " + max + " ký tự");
        }
        return normalized;
    }
}
