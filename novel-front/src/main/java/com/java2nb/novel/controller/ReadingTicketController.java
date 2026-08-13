package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.dto.entitlement.ReadingTicketAccountResponse;
import com.java2nb.novel.dto.entitlement.ReadingTicketLedgerHistoryResponse;
import com.java2nb.novel.dto.entitlement.ReadingTicketLotHistoryResponse;
import com.java2nb.novel.dto.entitlement.ReadingTicketUnlockRequest;
import com.java2nb.novel.dto.entitlement.ReadingTicketUnlockResponse;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.InsufficientReadingTicketException;
import com.java2nb.novel.service.entitlement.ReadingTicketAccountRow;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.entitlement.ReadingTicketUnlockCommand;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Date;

@Validated
@RestController
public class ReadingTicketController extends BaseController {
    private final ReadingTicketService readingTicketService;
    private final BookService bookService;
    private final UserService userService;
    private final ChapterCommercialPolicyService commercialPolicyService;
    private final ReaderEntitlementProperties properties;
    private final Clock clock;

    @Autowired
    public ReadingTicketController(ReadingTicketService readingTicketService, BookService bookService,
                                   UserService userService,
                                   ChapterCommercialPolicyService commercialPolicyService,
                                   ReaderEntitlementProperties properties) {
        this(readingTicketService, bookService, userService, commercialPolicyService, properties,
            Clock.systemUTC());
    }

    ReadingTicketController(ReadingTicketService readingTicketService, BookService bookService,
                            UserService userService,
                            ChapterCommercialPolicyService commercialPolicyService,
                            ReaderEntitlementProperties properties, Clock clock) {
        this.readingTicketService = readingTicketService;
        this.bookService = bookService;
        this.userService = userService;
        this.commercialPolicyService = commercialPolicyService;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("user/reading-tickets")
    public RestResult<ReadingTicketAccountResponse> getAccount(HttpServletRequest request) {
        requireEnabled();
        ReadingTicketAccountRow account = readingTicketService.getOrCreateAccount(requireUser(request).getId());
        return RestResult.ok(new ReadingTicketAccountResponse(account.getAvailableBalance()));
    }

    @PostMapping("book/{bookId}/chapter/{bookIndexId}/reading-ticket-unlock")
    @RateLimit(key = "reading_ticket_unlock", count = 20, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<ReadingTicketUnlockResponse> unlockChapter(
        @PathVariable("bookId") @Min(1) long bookId,
        @PathVariable("bookIndexId") @Min(1) long bookIndexId,
        @Valid @RequestBody ReadingTicketUnlockRequest input,
        HttpServletRequest request) {
        requireEnabled();
        long userId = requireUser(request).getId();
        Book book = bookService.queryBookDetail(bookId);
        ResponseStatus denial = AgeRatingUtil.publicBookDenialReason(book, userService.userInfo(userId));
        if (denial != null) {
            throw new BusinessException(denial);
        }
        BookIndex chapter = bookService.queryBookIndex(bookIndexId);
        denial = AgeRatingUtil.publicChapterDenialReason(chapter, bookId);
        if (denial != null) {
            throw new BusinessException(denial);
        }

        Date now = Date.from(clock.instant());
        if (userService.queryIsBuyBookIndex(userId, bookIndexId)
            || !commercialPolicyService.evaluate(chapter, false, now).purchaseRequired()) {
            return RestResult.ok(ReadingTicketUnlockResponse.alreadyAccessible(
                readingTicketService.getOrCreateAccount(userId).getAvailableBalance()));
        }
        try {
            return RestResult.ok(ReadingTicketUnlockResponse.from(
                readingTicketService.unlockChapter(new ReadingTicketUnlockCommand(
                    userId, bookId, bookIndexId, input.clientRequestId(), now,
                    properties.getPolicyVersion(), properties.getMaxLotsPerSpend()))));
        } catch (InsufficientReadingTicketException exception) {
            throw new BusinessException(ResponseStatus.READING_TICKET_INSUFFICIENT);
        }
    }

    @GetMapping("user/reading-tickets/ledger")
    public RestResult<ReadingTicketLedgerHistoryResponse> getLedgerHistory(
        @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
        HttpServletRequest request) {
        requireEnabled();
        long userId = requireUser(request).getId();
        return RestResult.ok(ReadingTicketLedgerHistoryResponse.from(
            readingTicketService.listLedgerHistory(userId, page, pageSize)));
    }

    @GetMapping("user/reading-tickets/lots")
    public RestResult<ReadingTicketLotHistoryResponse> getLotHistory(
        @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
        HttpServletRequest request) {
        requireEnabled();
        long userId = requireUser(request).getId();
        return RestResult.ok(ReadingTicketLotHistoryResponse.from(
            readingTicketService.listLotHistory(userId, page, pageSize)));
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            throw new BusinessException(ResponseStatus.READING_TICKET_DISABLED);
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
