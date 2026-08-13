package com.java2nb.novel.service.gamification;

import java.util.Date;

public interface GamificationPublicPolicyService {
    GamificationPublicPolicyRow getPublished();

    GamificationPublicPolicyRow createDraft(String policyVersion, String title,
                                             String contentText, long operatorId);

    GamificationPublicPolicyRow publish(long policyId, long expectedVersion,
                                        long operatorId, Date publishedAt);
}
