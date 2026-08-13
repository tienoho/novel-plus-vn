package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.entitlement.ReadingTicketUnlockRequest;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.chapter.ChapterAccessDecision;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.InsufficientReadingTicketException;
import com.java2nb.novel.service.entitlement.ReadingTicketAccountRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerPage;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLotHistoryRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLotPage;
import com.java2nb.novel.service.entitlement.ReadingTicketPostResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.entitlement.ReadingTicketUnlockCommand;
import com.java2nb.novel.service.entitlement.ReadingTicketUnlockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingTicketControllerTest {

    private static final long USER_ID = 101L;
    private static final long BOOK_ID = 202L;
    private static final long CHAPTER_ID = 303L;
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2027-01-01T03:00:00Z"), ZoneOffset.UTC);

    private ReadingTicketService readingTicketService;
    private BookService bookService;
    private UserService userService;
    private ChapterCommercialPolicyService commercialPolicyService;
    private ReaderEntitlementProperties properties;
    private ReadingTicketController controller;

    @BeforeEach
    void setUp() {
        readingTicketService = mock(ReadingTicketService.class);
        bookService = mock(BookService.class);
        userService = mock(UserService.class);
        commercialPolicyService = mock(ChapterCommercialPolicyService.class);
        properties = new ReaderEntitlementProperties();
        properties.setEnabled(true);
        UserDetails details = mock(UserDetails.class);
        when(details.getId()).thenReturn(USER_ID);
        controller = new ReadingTicketController(readingTicketService, bookService, userService,
            commercialPolicyService, properties, CLOCK) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return details;
            }
        };
        when(bookService.queryBookDetail(BOOK_ID)).thenReturn(book());
        when(bookService.queryBookIndex(CHAPTER_ID)).thenReturn(chapter());
        when(userService.userInfo(USER_ID)).thenReturn(new User());
        when(commercialPolicyService.evaluate(any(), eq(false), any()))
            .thenReturn(new ChapterAccessDecision(true, false, false, false));
    }

    @Test
    void unlockUsesOnlyServerOwnedBookAndUserData() {
        when(readingTicketService.unlockChapter(any())).thenReturn(
            new ReadingTicketUnlockResult(ReadingTicketPostResult.POSTED, 404L, 2L));

        var response = controller.unlockChapter(BOOK_ID, CHAPTER_ID,
            new ReadingTicketUnlockRequest("request_0001"), new MockHttpServletRequest());

        assertThat(response.getData().status()).isEqualTo("POSTED");
        assertThat(response.getData().entitlementId()).isEqualTo(404L);
        ArgumentCaptor<ReadingTicketUnlockCommand> command =
            ArgumentCaptor.forClass(ReadingTicketUnlockCommand.class);
        verify(readingTicketService).unlockChapter(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().bookId()).isEqualTo(BOOK_ID);
        assertThat(command.getValue().bookIndexId()).isEqualTo(CHAPTER_ID);
        assertThat(command.getValue().occurredAt()).isEqualTo(Date.from(CLOCK.instant()));
    }

    @Test
    void alreadyPurchasedChapterNeverSpendsReadingTicket() {
        ReadingTicketAccountRow account = new ReadingTicketAccountRow();
        account.setAvailableBalance(3L);
        when(userService.queryIsBuyBookIndex(USER_ID, CHAPTER_ID)).thenReturn(true);
        when(readingTicketService.getOrCreateAccount(USER_ID)).thenReturn(account);

        var response = controller.unlockChapter(BOOK_ID, CHAPTER_ID,
            new ReadingTicketUnlockRequest("request_0002"), new MockHttpServletRequest());

        assertThat(response.getData().status()).isEqualTo("ALREADY_ACCESSIBLE");
        assertThat(response.getData().availableBalance()).isEqualTo(3L);
        verify(readingTicketService, never()).unlockChapter(any());
    }

    @Test
    void disabledFeatureStopsBeforeReadingBookOrWritingLedger() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> controller.unlockChapter(BOOK_ID, CHAPTER_ID,
            new ReadingTicketUnlockRequest("request_0003"), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);
        verify(bookService, never()).queryBookDetail(any());
        verify(readingTicketService, never()).unlockChapter(any());
    }

    @Test
    void insufficientTicketBecomesPublicBusinessError() {
        when(readingTicketService.unlockChapter(any()))
            .thenThrow(new InsufficientReadingTicketException());

        assertThatThrownBy(() -> controller.unlockChapter(BOOK_ID, CHAPTER_ID,
            new ReadingTicketUnlockRequest("request_0004"), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void historyEndpointsUseOnlyAuthenticatedUserAndHideInternalFields() {
        ReadingTicketLedgerRow ledger = new ReadingTicketLedgerRow();
        ledger.setEntryNo("RT-LEDGER-1");
        ledger.setEntryType("GRANT");
        ledger.setAmount(3L);
        ledger.setBalanceAfter(3L);
        ReadingTicketLotHistoryRow lot = new ReadingTicketLotHistoryRow();
        lot.setGrantEntryNo("RT-LEDGER-1");
        lot.setSourceType("SUBSCRIPTION");
        lot.setGrantedAmount(3L);
        lot.setRemainingAmount(2L);
        when(readingTicketService.listLedgerHistory(USER_ID, 1, 20))
            .thenReturn(new ReadingTicketLedgerPage(List.of(ledger), 1L, 1, 20));
        when(readingTicketService.listLotHistory(USER_ID, 1, 20))
            .thenReturn(new ReadingTicketLotPage(List.of(lot), 1L, 1, 20));

        var ledgerResponse = controller.getLedgerHistory(1, 20, new MockHttpServletRequest());
        var lotResponse = controller.getLotHistory(1, 20, new MockHttpServletRequest());

        assertThat(ledgerResponse.getData().items()).singleElement()
            .satisfies(item -> assertThat(item.entryNo()).isEqualTo("RT-LEDGER-1"));
        assertThat(lotResponse.getData().items()).singleElement()
            .satisfies(item -> assertThat(item.grantEntryNo()).isEqualTo("RT-LEDGER-1"));
        verify(readingTicketService).listLedgerHistory(USER_ID, 1, 20);
        verify(readingTicketService).listLotHistory(USER_ID, 1, 20);
    }

    private Book book() {
        Book book = new Book();
        book.setId(BOOK_ID);
        book.setStatus((byte) 1);
        book.setAuditStatus((byte) 1);
        book.setCoverAuditStatus((byte) 1);
        book.setAgeRating((byte) 0);
        return book;
    }

    private BookIndex chapter() {
        BookIndex chapter = new BookIndex();
        chapter.setId(CHAPTER_ID);
        chapter.setBookId(BOOK_ID);
        chapter.setIsVip((byte) 1);
        chapter.setAuditStatus((byte) 1);
        chapter.setBookPrice(5);
        return chapter;
    }
}
