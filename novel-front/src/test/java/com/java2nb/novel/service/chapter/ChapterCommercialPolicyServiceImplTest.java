package com.java2nb.novel.service.chapter;

import com.java2nb.novel.core.config.BookPriceProperties;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.mapper.ChapterCommercialPolicyMapper;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChapterCommercialPolicyServiceImplTest {
    private ChapterCommercialPolicyMapper mapper;
    private ChapterCommercialPolicyServiceImpl service;
    private BookIndex chapter;
    private final Date start = Date.from(Instant.parse("2026-08-01T00:00:00Z"));
    private final Date end = Date.from(Instant.parse("2026-08-02T00:00:00Z"));

    @BeforeEach
    void setUp() {
        mapper = mock(ChapterCommercialPolicyMapper.class);
        BookPriceProperties price = new BookPriceProperties();
        price.setWordCount(BigDecimal.valueOf(1000));
        price.setValue(BigDecimal.valueOf(5));
        service = new ChapterCommercialPolicyServiceImpl(mapper, mock(BookIndexMapper.class),
            mock(AuthorBookCollaborationService.class), price);
        chapter = new BookIndex();
        chapter.setId(10L);
        chapter.setBookId(20L);
        chapter.setIsVip((byte) 1);
        chapter.setBookPrice(5);
    }

    @Test
    void temporaryWindowIsStartInclusiveEndExclusiveAndNeverOffline() {
        ChapterCommercialPolicy policy = policy(null, start, end);
        when(mapper.selectByBookIndexId(10L)).thenReturn(policy);

        ChapterAccessDecision before = service.evaluate(chapter, false,
            Date.from(start.toInstant().minusMillis(1)));
        ChapterAccessDecision atStart = service.evaluate(chapter, false, start);
        ChapterAccessDecision beforeEnd = service.evaluate(chapter, false,
            Date.from(end.toInstant().minusMillis(1)));
        ChapterAccessDecision atEnd = service.evaluate(chapter, false, end);

        assertThat(before.purchaseRequired()).isTrue();
        assertThat(atStart.purchaseRequired()).isFalse();
        assertThat(atStart.temporaryFree()).isTrue();
        assertThat(atStart.offlineEligible()).isFalse();
        assertThat(beforeEnd.purchaseRequired()).isFalse();
        assertThat(atEnd.purchaseRequired()).isTrue();
    }

    @Test
    void unlockIsInclusivePermanentAndOfflineEligible() {
        when(mapper.selectByBookIndexId(10L)).thenReturn(policy(start, null, null));

        assertThat(service.evaluate(chapter, false, Date.from(start.toInstant().minusMillis(1)))
            .purchaseRequired()).isTrue();
        ChapterAccessDecision unlocked = service.evaluate(chapter, false, start);
        assertThat(unlocked.purchaseRequired()).isFalse();
        assertThat(unlocked.permanentlyFree()).isTrue();
        assertThat(unlocked.offlineEligible()).isTrue();
    }

    @Test
    void purchaseAndNonVipAlwaysAllowReadingButOnlyPermanentFreeAllowsOffline() {
        when(mapper.selectByBookIndexId(10L)).thenReturn(null);
        assertThat(service.evaluate(chapter, true, start).purchaseRequired()).isFalse();
        assertThat(service.evaluate(chapter, true, start).offlineEligible()).isFalse();

        chapter.setIsVip((byte) 0);
        ChapterAccessDecision free = service.evaluate(chapter, false, start);
        assertThat(free.purchaseRequired()).isFalse();
        assertThat(free.offlineEligible()).isTrue();
    }

    @Test
    void priceUsesCustomValueOrAutomaticMinimumOneXu() {
        assertThat(service.calculateAutomaticPrice(50)).isEqualTo(1);
        assertThat(service.calculateAutomaticPrice(2000)).isEqualTo(10);
        assertThat(service.resolveEffectivePrice((byte) 1, null, 0)).isEqualTo(1);
        assertThat(service.resolveEffectivePrice((byte) 1, 37, 10)).isEqualTo(37);
        assertThat(service.resolveEffectivePrice((byte) 0, null, 10)).isZero();
    }

    @Test
    void invalidPriceAndIncompleteOrReversedWindowAreRejected() {
        assertThatThrownBy(() -> service.validate((byte) 1, 0, null, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 đến 1.000.000");
        assertThatThrownBy(() -> service.validate((byte) 1, null, null, start, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đủ thời gian");
        assertThatThrownBy(() -> service.validate((byte) 1, null, null, end, start))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sau thời gian bắt đầu");
        assertThatThrownBy(() -> service.validate((byte) 0, 10, null, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chương thu phí");
    }

    private ChapterCommercialPolicy policy(Date unlockAt, Date freeFrom, Date freeUntil) {
        ChapterCommercialPolicy policy = new ChapterCommercialPolicy();
        policy.setBookIndexId(10L);
        policy.setBookId(20L);
        policy.setUnlockAt(unlockAt);
        policy.setFreeFrom(freeFrom);
        policy.setFreeUntil(freeUntil);
        return policy;
    }
}
