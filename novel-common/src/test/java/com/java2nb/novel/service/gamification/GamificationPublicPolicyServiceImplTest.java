package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationPublicPolicyMapper;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import com.java2nb.novel.service.impl.GamificationPublicPolicyServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GamificationPublicPolicyServiceImplTest {

    @Test
    void exposesOnlyThePolicyPublishedThroughTheVersionedBundleLifecycle() {
        GamificationPublicPolicyMapper mapper = mock(GamificationPublicPolicyMapper.class);
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        GamificationPublicPolicyRow published = new GamificationPublicPolicyRow();
        published.setPolicyVersion("v1");
        published.setStatus("PUBLISHED");
        when(provider.current()).thenReturn(GamificationConfigSnapshot.bootstrapDisabled());
        when(mapper.selectPublished("v1")).thenReturn(published);

        assertThat(new GamificationPublicPolicyServiceImpl(mapper, provider).getPublished())
            .isSameAs(published);
    }
}
