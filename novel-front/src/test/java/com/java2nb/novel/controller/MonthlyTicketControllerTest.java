package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.utils.DeviceCookieService;
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
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.TicketRiskDecision;
import com.java2nb.novel.service.gamification.TicketRiskService;
import com.java2nb.novel.service.gamification.TicketBookSummary;
import com.java2nb.novel.service.gamification.TicketVoteCommand;
import com.java2nb.novel.service.gamification.TicketVoteResult;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import com.java2nb.novel.service.gamification.config.GamificationIdentifierHasher;
import io.github.xxyopen.model.resp.RestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    private GamificationConfigProvider configProvider;
    private GamificationIdentifierHasher identifierHasher;
    private TicketRiskService ticketRiskService;
    private DeviceCookieService deviceCookieService;
    private MonthlyTicketController controller;

    @BeforeEach
    void setUp() {
        ticketService = mock(MonthlyTicketService.class);
        rankingService = mock(MonthlyRankingService.class);
        bookService = mock(BookService.class);
        userService = mock(UserService.class);
        configProvider = mock(GamificationConfigProvider.class);
        GamificationConfigSnapshot config = GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .ticketEnabled(true).voteEnabled(true).seasonEnabled(true).build();
        when(configProvider.current()).thenReturn(config);
        when(configProvider.currentForWrite()).thenReturn(config);
        identifierHasher = mock(GamificationIdentifierHasher.class);
        when(identifierHasher.hash(any(), any(), any())).thenReturn("a".repeat(64));
        ticketRiskService = mock(TicketRiskService.class);
        deviceCookieService = mock(DeviceCookieService.class);
        when(deviceCookieService.resolve(any(), any()))
            .thenReturn("f6ea0f4d-d365-44ff-a2b8-9d46b279b3c5");
        when(ticketRiskService.assess(any()))
            .thenReturn(new TicketRiskDecision(1L, 0, "ALLOW", ""));

        UserDetails details = mock(UserDetails.class);
        when(details.getId()).thenReturn(USER_ID);
        controller = new MonthlyTicketController(ticketService, rankingService, bookService,
            userService, configProvider, ticketRiskService, deviceCookieService, identifierHasher,
            FIXED_CLOCK) {
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
        when(rankingService.getRanking(null, null, 1, 20, Date.from(FIXED_CLOCK.instant())))
            .thenReturn(new MonthlyRankingPage(9L, "2026-08", "OPEN",
                Date.from(Instant.parse("2026-09-01T00:00:00Z")), false,
                List.of(), 0, 1, 20));

        var response = controller.getRanking(null, null, 1, 20);

        assertThat(response.getData().seasonId()).isEqualTo(9L);
        verify(rankingService).getRanking(null, null, 1, 20, Date.from(FIXED_CLOCK.instant()));
    }

    @Test
    void openSeasonEndpointPublishesStableSeasonIds() {
        MonthlySeasonRow regular = new MonthlySeasonRow();
        regular.setId(9L);
        regular.setPeriodCode("2026-08");
        regular.setSeasonType("REGULAR");
        regular.setStatus("OPEN");
        when(rankingService.listOpenSeasons(Date.from(FIXED_CLOCK.instant())))
            .thenReturn(List.of(regular));

        var response = controller.getOpenSeasons();

        assertThat(response.getData()).singleElement().satisfies(season -> {
            assertThat(season.seasonId()).isEqualTo(9L);
            assertThat(season.seasonType()).isEqualTo("REGULAR");
        });
    }

    @Test
    void bookSummaryPublishesConfiguredPerRequestLimitForTheClient() {
        when(ticketService.getBookSummary(any(Long.class), any(Long.class), any(Long.class),
            any(Date.class), any()))
            .thenReturn(new TicketBookSummary(9L, "2026-08", "OPEN", 17L, true, null));

        var response = controller.getBookSummary(BOOK_ID, 9L, new MockHttpServletRequest());

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
            new MonthlyTicketVoteRequest(9L, 2, "request-00000001"), request,
            new MockHttpServletResponse());

        assertThat(response.getData().voteId()).isEqualTo(7L);
        ArgumentCaptor<TicketVoteCommand> command = ArgumentCaptor.forClass(TicketVoteCommand.class);
        verify(ticketService).castVote(command.capture(), any());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().bookId()).isEqualTo(BOOK_ID);
        assertThat(command.getValue().seasonId()).isEqualTo(9L);
        assertThat(command.getValue().amount()).isEqualTo(2);
        assertThat(command.getValue().sourceIpHash())
            .hasSize(64)
            .doesNotContain("203.0.113.45");
        assertThat(command.getValue().sourceDeviceHash())
            .hasSize(64)
            .doesNotContain("f6ea0f4d-d365-44ff-a2b8-9d46b279b3c5");
    }

    @Test
    void disabledFeatureStopsBeforeReadingBookOrWritingLedger() {
        when(configProvider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().ticketEnabled(true).build());

        assertThatThrownBy(() -> controller.castVote(BOOK_ID,
            new MonthlyTicketVoteRequest(9L, 1, "request-00000002"),
            new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(BusinessException.class);
        verify(bookService, never()).queryBookDetail(any());
        verify(ticketService, never()).castVote(any(), any());
    }

    @Test
    void requestDtoCannotAcceptOwnerIdentifiers() {
        assertThat(Arrays.stream(MonthlyTicketVoteRequest.class.getRecordComponents())
            .map(component -> component.getName()))
            .contains("seasonId", "amount", "clientRequestId")
            .doesNotContain("count", "userId", "authorId");
    }
}
