package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.core.utils.DeviceCookieService;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.dto.gamification.MonthlyTicketAccountResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketHistoryResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketLotResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketRankingResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketSummaryResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketSeasonResponse;
import com.java2nb.novel.dto.gamification.MonthlyTicketVoteRequest;
import com.java2nb.novel.dto.gamification.MonthlyTicketVoteResponse;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.TicketAccountRow;
import com.java2nb.novel.service.gamification.TicketBookSummary;
import com.java2nb.novel.service.gamification.TicketPolicy;
import com.java2nb.novel.service.gamification.TicketRiskCommand;
import com.java2nb.novel.service.gamification.TicketRiskDecision;
import com.java2nb.novel.service.gamification.TicketRiskService;
import com.java2nb.novel.service.gamification.TicketVoteCommand;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import com.java2nb.novel.service.gamification.config.GamificationIdentifierHasher;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Validated
@RestController
public class MonthlyTicketController extends BaseController {

    private final MonthlyTicketService monthlyTicketService;
    private final MonthlyRankingService monthlyRankingService;
    private final BookService bookService;
    private final UserService userService;
    private final GamificationConfigProvider configProvider;
    private final GamificationIdentifierHasher identifierHasher;
    private final TicketRiskService ticketRiskService;
    private final DeviceCookieService deviceCookieService;
    private final Clock clock;

    @Autowired
    public MonthlyTicketController(MonthlyTicketService monthlyTicketService,
                                   MonthlyRankingService monthlyRankingService, BookService bookService,
                                   UserService userService, GamificationConfigProvider configProvider,
                                   TicketRiskService ticketRiskService,
                                   DeviceCookieService deviceCookieService,
                                   GamificationIdentifierHasher identifierHasher) {
        // Clock chỉ cung cấp Instant; múi giờ nghiệp vụ được resolve sau feature guard để một
        // cấu hình đang tắt nhưng gõ sai zone không làm toàn ứng dụng thất bại lúc khởi động.
        this(monthlyTicketService, monthlyRankingService, bookService, userService, configProvider,
            ticketRiskService, deviceCookieService, identifierHasher, Clock.systemUTC());
    }

    MonthlyTicketController(MonthlyTicketService monthlyTicketService,
                            MonthlyRankingService monthlyRankingService, BookService bookService,
                            UserService userService, GamificationConfigProvider configProvider,
                            TicketRiskService ticketRiskService,
                            DeviceCookieService deviceCookieService,
                            GamificationIdentifierHasher identifierHasher, Clock clock) {
        this.monthlyTicketService = monthlyTicketService;
        this.monthlyRankingService = monthlyRankingService;
        this.bookService = bookService;
        this.userService = userService;
        this.configProvider = configProvider;
        this.identifierHasher = identifierHasher;
        this.ticketRiskService = ticketRiskService;
        this.deviceCookieService = deviceCookieService;
        this.clock = clock;
    }

    @GetMapping("book/monthly-ticket-ranking")
    public RestResult<MonthlyTicketRankingResponse> getRanking(
        @RequestParam(value = "seasonId", required = false) Long seasonId,
        @RequestParam(value = "period", required = false) String period,
        @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        GamificationConfigSnapshot config = configProvider.current();
        requireRankingEnabled(config);
        return RestResult.ok(MonthlyTicketRankingResponse.from(
            monthlyRankingService.getRanking(seasonId, period, page, limit,
                Date.from(clock.instant()))));
    }

    @GetMapping("book/monthly-ticket-seasons")
    public RestResult<List<MonthlyTicketSeasonResponse>> getOpenSeasons() {
        GamificationConfigSnapshot config = configProvider.current();
        requireRankingEnabled(config);
        return RestResult.ok(monthlyRankingService.listOpenSeasons(Date.from(clock.instant())).stream()
            .map(MonthlyTicketSeasonResponse::from).toList());
    }

    @GetMapping("user/monthly-tickets")
    public RestResult<MonthlyTicketAccountResponse> getAccount(HttpServletRequest request) {
        GamificationConfigSnapshot config = configProvider.current();
        requireTicketEnabled(config);
        long userId = requireUser(request).getId();
        TicketAccountRow account = monthlyTicketService.getOrCreateAccount(userId);
        Date now = Date.from(clock.instant());
        return RestResult.ok(new MonthlyTicketAccountResponse(account.getAvailableBalance(),
            monthlyTicketService.listActiveLots(userId, now, 20).stream()
                .map(MonthlyTicketLotResponse::from).toList()));
    }

    @GetMapping("user/monthly-tickets/history")
    public RestResult<MonthlyTicketHistoryResponse> getHistory(
        @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
        HttpServletRequest request) {
        GamificationConfigSnapshot config = configProvider.current();
        requireTicketEnabled(config);
        long userId = requireUser(request).getId();
        return RestResult.ok(MonthlyTicketHistoryResponse.from(
            monthlyTicketService.listHistory(userId, page, limit)));
    }

    @GetMapping("book/{bookId}/monthly-ticket-summary")
    public RestResult<MonthlyTicketSummaryResponse> getBookSummary(
        @PathVariable("bookId") @Min(1) long bookId,
        @RequestParam("seasonId") @Min(1) long seasonId,
        HttpServletRequest request) {
        GamificationConfigSnapshot config = configProvider.current();
        requireVoteEnabled(config);
        long userId = requireUser(request).getId();
        TicketBookSummary summary = monthlyTicketService.getBookSummary(
            userId, bookId, seasonId, Date.from(clock.instant()), ticketPolicy(config));
        if (summary.eligible()) {
            ResponseStatus denial = AgeRatingUtil.publicBookDenialReason(
                bookService.queryBookDetail(bookId), userService.userInfo(userId));
            if (denial != null) {
                summary = summary.withEligibility(false, denial.name());
            }
        }
        return RestResult.ok(MonthlyTicketSummaryResponse.from(summary,
            config.getVoteMaxTicketsPerRequest()));
    }

    @PostMapping("book/{bookId}/monthly-ticket-votes")
    @RateLimit(key = "monthly_ticket_vote", count = 30, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<MonthlyTicketVoteResponse> castVote(
        @PathVariable("bookId") @Min(1) long bookId,
        @Valid @RequestBody MonthlyTicketVoteRequest input,
        HttpServletRequest request, HttpServletResponse response) {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        requireVoteEnabled(config);
        long userId = requireUser(request).getId();
        Book book = bookService.queryBookDetail(bookId);
        User user = userService.userInfo(userId);
        ResponseStatus denial = AgeRatingUtil.publicBookDenialReason(book, user);
        if (denial != null) {
            throw new BusinessException(denial);
        }

        Instant now = clock.instant();
        ZoneId zoneId = ZoneId.of(config.getZoneId());
        LocalDate localDate = now.atZone(zoneId).toLocalDate();
        String ipHash = identifierHasher.hash("IP", IpUtil.getRealIp(request),
            config.getVoteIpHashKeyId());
        String deviceHash = identifierHasher.hash("DEVICE",
            deviceCookieService.resolve(request, response), config.getVoteIpHashKeyId());
        TicketRiskDecision risk = ticketRiskService.assess(new TicketRiskCommand(userId,
            input.seasonId(), bookId, input.clientRequestId(), deviceHash, ipHash,
            Date.from(now), config.getPolicyVersion()));
        if (risk.blocked()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_VOTE_RISK_BLOCKED);
        }
        TicketVoteCommand command = new TicketVoteCommand(userId, bookId, input.seasonId(), input.amount(),
            input.clientRequestId(), ipHash, deviceHash, config.getVoteIpHashKeyId(), Date.from(now),
            localDate, config.getRuntimeRevision());
        return RestResult.ok(MonthlyTicketVoteResponse.from(
            monthlyTicketService.castVote(command, ticketPolicy(config))));
    }

    private TicketPolicy ticketPolicy(GamificationConfigSnapshot config) {
        return new TicketPolicy(config.getPolicyVersion(), config.getTicketLotValidityDays(),
            config.getVoteMaxTicketsPerRequest(), config.getVoteMaxVotesPerDay(),
            config.getVoteMaxTicketsPerDay(), config.getVoteMaxTicketsPerBookPerSeason(),
            config.getVoteMaxLotsPerSpend(), config.isVoteAllowCrawledBooks());
    }

    private void requireTicketEnabled(GamificationConfigSnapshot config) {
        if (!config.isTicketEnabled()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }

    private void requireVoteEnabled(GamificationConfigSnapshot config) {
        if (!config.isTicketEnabled() || !config.isVoteEnabled()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }

    private void requireRankingEnabled(GamificationConfigSnapshot config) {
        if (!config.isTicketEnabled() || !config.isSeasonEnabled()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }

    private UserDetails requireUser(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        return user;
    }

}
