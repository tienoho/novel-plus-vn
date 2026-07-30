package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.MonthlyTicketVoteRequest;
import com.java2nb.novel.dto.gamification.MonthlyTicketVoteResponse;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlyRankingPage;
import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.TicketBookSummary;
import com.java2nb.novel.service.gamification.TicketVoteCommand;
import com.java2nb.novel.service.gamification.TicketVoteResult;
import io.github.xxyopen.model.resp.RestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyTicketControllerTest {

    private static final long USER_ID = 101L;
    private static final long BOOK_ID = 202L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-15T03:00:00Z"), ZoneOffset.UTC);

    private MonthlyTicketService ticketService;
    private MonthlyRankingService rankingService;
    private BookService bookService;
    private UserService userService;
    private GamificationProperties properties;
    private MonthlyTicketController controller;

    @BeforeEach
    void setUp() {
        ticketService = mock(MonthlyTicketService.class);
        rankingService = mock(MonthlyRankingService.class);
        bookService = mock(BookService.class);
        userService = mock(UserService.class);
        properties = new GamificationProperties();
        properties.getTicket().setEnabled(true);
        properties.getVote().setEnabled(true);
        properties.getSeason().setEnabled(true);
        properties.getVote().setIpHashSalt("0123456789abcdef0123456789abcdef");

        UserDetails details = mock(UserDetails.class);
        when(details.getId()).thenReturn(USER_ID);
        controller = new MonthlyTicketController(ticketService, rankingService, bookService,
            userService, properties, FIXED_CLOCK) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return details;
            }
        };

        Book book = new Book();
        book.setId(BOOK_ID);
        book.setStatus((byte) 1);
        book.setAuditStatus((byte) 1);
        book.setCoverAuditStatus((byte) 1);
        book.setAgeRating((byte) 0);
        when(bookService.queryBookDetail(BOOK_ID)).thenReturn(book);
        when(userService.userInfo(USER_ID)).thenReturn(new User());
    }

    @Test
    void rankingIsPublicAndUsesServerClock() {
        when(rankingService.getRanking(null, 1, 20, Date.from(FIXED_CLOCK.instant())))
            .thenReturn(new MonthlyRankingPage(9L, "2026-08", "OPEN",
                Date.from(Instant.parse("2026-09-01T00:00:00Z")), false,
                List.of(), 0, 1, 20));

        var response = controller.getRanking(null, 1, 20);

        assertThat(response.getData().seasonId()).isEqualTo(9L);
        verify(rankingService).getRanking(null, 1, 20, Date.from(FIXED_CLOCK.instant()));
    }

    @Test
    void bookSummaryPublishesConfiguredPerRequestLimitForTheClient() {
        when(ticketService.getBookSummary(any(Long.class), any(Long.class), any(Date.class), any()))
            .thenReturn(new TicketBookSummary(9L, "2026-08", "OPEN", 17L, true, null));

        var response = controller.getBookSummary(BOOK_ID, new MockHttpServletRequest());

        assertThat(response.getData().maxTicketsPerRequest()).isEqualTo(10);
    }

    @Test
    void voteUsesAuthenticatedUserAndStoresOnlySaltedIpHash() {
        when(ticketService.castVote(any(), any()))
            .thenReturn(new TicketVoteResult(TicketPostResult.POSTED, 7L, 19L, 3L));
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
            "/book/202/monthly-ticket-votes");
        request.setRemoteAddr("203.0.113.45");

        RestResult<MonthlyTicketVoteResponse> response = controller.castVote(BOOK_ID,
            new MonthlyTicketVoteRequest(2, "request-00000001"), request);

        assertThat(response.getData().voteId()).isEqualTo(7L);
        ArgumentCaptor<TicketVoteCommand> command = ArgumentCaptor.forClass(TicketVoteCommand.class);
        verify(ticketService).castVote(command.capture(), any());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().bookId()).isEqualTo(BOOK_ID);
        assertThat(command.getValue().sourceIpHash())
            .hasSize(64)
            .doesNotContain("203.0.113.45");
    }

    @Test
    void disabledFeatureStopsBeforeReadingBookOrWritingLedger() {
        properties.getVote().setEnabled(false);

        assertThatThrownBy(() -> controller.castVote(BOOK_ID,
            new MonthlyTicketVoteRequest(1, "request-00000002"), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);
        verify(bookService, never()).queryBookDetail(any());
        verify(ticketService, never()).castVote(any(), any());
    }

    @Test
    void requestDtoCannotAcceptOwnerIdentifiers() {
        assertThat(Arrays.stream(MonthlyTicketVoteRequest.class.getRecordComponents())
            .map(component -> component.getName()))
            .doesNotContain("userId", "authorId");
    }
}
