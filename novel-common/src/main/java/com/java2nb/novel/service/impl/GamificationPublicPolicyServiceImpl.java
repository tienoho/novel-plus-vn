package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationPublicPolicyMapper;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GamificationPublicPolicyServiceImpl implements GamificationPublicPolicyService {

    private final GamificationPublicPolicyMapper mapper;
    private final GamificationConfigProvider configProvider;

    @Override
    @Transactional(readOnly = true)
    public GamificationPublicPolicyRow getPublished() {
        return mapper.selectPublished(configProvider.current().getPolicyVersion());
    }

}
