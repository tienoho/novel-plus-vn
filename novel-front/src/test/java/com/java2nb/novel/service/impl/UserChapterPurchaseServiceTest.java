package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.config.AuthorIncomeProperties;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.UserBuyRecord;
import com.java2nb.novel.mapper.*;
import com.java2nb.novel.service.chapter.ChapterAccessDecision;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserChapterPurchaseServiceTest {
    private UserBuyRecordMapper buyRecordMapper;
    private WalletLedgerService ledgerService;
    private BookIndexMapper bookIndexMapper;
    private ChapterCommercialPolicyService commercialPolicyService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        buyRecordMapper = mock(UserBuyRecordMapper.class);
        ledgerService = mock(WalletLedgerService.class);
        bookIndexMapper = mock(BookIndexMapper.class);
        commercialPolicyService = mock(ChapterCommercialPolicyService.class);
        AuthorIncomeProperties income = new AuthorIncomeProperties();
        income.setShareProportion(new BigDecimal("0.70"));
        service = new UserServiceImpl(mock(FrontUserMapper.class), mock(FrontUserBookshelfMapper.class),
            mock(FrontUserReadHistoryMapper.class), mock(UserFeedbackMapper.class), buyRecordMapper,
            ledgerService, income, bookIndexMapper, commercialPolicyService);
        when(bookIndexMapper.lockById(20L)).thenReturn(chapter());
        when(buyRecordMapper.count(any(CountDSLCompleter.class))).thenReturn(0L);
    }

    @Test
    void purchaseUsesLockedAuthoritativePriceInsteadOfClientAmount() {
        when(commercialPolicyService.evaluate(any(), eq(false), any()))
            .thenReturn(new ChapterAccessDecision(true, false, false, false));
        when(ledgerService.purchaseChapter(7L, 9L, 37L, 25L, "20", "CHAPTER_PURCHASE:7:20"))
            .thenReturn(WalletPostResult.POSTED);
        UserBuyRecord input = input();
        input.setBuyAmount(1);

        service.buyBookIndex(7L, 9L, input);

        assertThat(input.getBuyAmount()).isEqualTo(37);
        assertThat(input.getBookIndexName()).isEqualTo("Chương khóa");
        verify(buyRecordMapper).insertSelective(input);
    }

    @Test
    void temporaryFreeChapterNeverPostsLedgerOrPurchaseRecord() {
        when(commercialPolicyService.evaluate(any(), eq(false), any()))
            .thenReturn(new ChapterAccessDecision(false, false, true, false));

        service.buyBookIndex(7L, 9L, input());

        verifyNoInteractions(ledgerService);
        verify(buyRecordMapper, never()).insertSelective(any());
    }

    @Test
    void mismatchedBookIdFailsBeforeMoneyMovement() {
        UserBuyRecord input = input();
        input.setBookId(11L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.buyBookIndex(7L, 9L, input))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không thuộc");
        verifyNoInteractions(ledgerService);
    }

    private BookIndex chapter() {
        BookIndex chapter = new BookIndex();
        chapter.setId(20L);
        chapter.setBookId(10L);
        chapter.setIndexName("Chương khóa");
        chapter.setIsVip((byte) 1);
        chapter.setBookPrice(37);
        return chapter;
    }

    private UserBuyRecord input() {
        UserBuyRecord input = new UserBuyRecord();
        input.setBookId(10L);
        input.setBookIndexId(20L);
        input.setBookName("Truyện thử nghiệm");
        return input;
    }
}
