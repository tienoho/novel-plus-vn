package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.dto.gamification.GamificationProfileResponse;
import com.java2nb.novel.dto.gamification.RealmUpdateRequest;
import com.java2nb.novel.dto.gamification.RealmUpdateResponse;
import com.java2nb.novel.dto.gamification.TickerPreferenceRequest;
import com.java2nb.novel.dto.gamification.QuestProgressResponse;
import com.java2nb.novel.dto.gamification.QuestClaimResponse;
import com.java2nb.novel.dto.gamification.CheckInResponse;
import com.java2nb.novel.service.gamification.QuestClaimCommand;
import com.java2nb.novel.service.gamification.GamificationCheckInService;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@Validated
@RestController
@RequestMapping("user/gamification")
public class GamificationController extends BaseController {

    private final GamificationProgressService progressService;
    private final GamificationProperties properties;
    private final Messages messages;
    private final GamificationCheckInService checkInService;
    private final Clock clock;

    @Autowired
    public GamificationController(GamificationProgressService progressService,
                                  GamificationProperties properties, Messages messages,
                                  GamificationCheckInService checkInService) {
        this(progressService, properties, messages, checkInService, Clock.systemUTC());
    }

    GamificationController(GamificationProgressService progressService,
                           GamificationProperties properties, Messages messages,
                           GamificationCheckInService checkInService, Clock clock) {
        this.progressService = progressService;
        this.properties = properties;
        this.messages = messages;
        this.checkInService = checkInService;
        this.clock = clock;
    }

    @GetMapping("profile")
    public RestResult<GamificationProfileResponse> getProfile(HttpServletRequest request) {
        requireProgressEnabled();
        long userId = requireUser(request).getId();
        return RestResult.ok(GamificationProfileResponse.from(
            progressService.getProfileSnapshot(userId, properties.getPolicyVersion())));
    }

    @PatchMapping("realm")
    @RateLimit(key = "gamification-realm", count = 10, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<RealmUpdateResponse> updateRealm(@Valid @RequestBody RealmUpdateRequest input,
                                                       HttpServletRequest request) {
        requireRealmEnabled();
        long userId = requireUser(request).getId();
        var result = progressService.updateRealm(userId,
            input.realmType(), input.expectedVersion(), Date.from(clock.instant()),
            properties.resolveZoneId(), properties.getRealm().getChangeCooldownHours(),
            properties.getPolicyVersion());
        return RestResult.ok(RealmUpdateResponse.from(result,
            progressService.getProfileSnapshot(userId, properties.getPolicyVersion())));
    }

    @GetMapping("quests")
    public RestResult<List<QuestProgressResponse>> getQuests(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        HttpServletRequest request) {
        requireQuestEnabled();
        long userId = requireUser(request).getId();
        Instant now = clock.instant();
        LocalDate effectiveDate = date == null
            ? LocalDate.ofInstant(now, properties.resolveZoneId()) : date;
        List<QuestProgressResponse> response = progressService.listQuests(
                userId, effectiveDate, Date.from(now))
            .stream()
            .map(row -> QuestProgressResponse.from(row, messages.get(row.getNameKey())))
            .toList();
        return RestResult.ok(response);
    }

    @PostMapping("quests/{questCode}/claim")
    @RateLimit(key = "gamification-quest-claim", count = 30, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<QuestClaimResponse> claimQuest(@PathVariable String questCode,
                                                     HttpServletRequest request) {
        requireQuestEnabled();
        long userId = requireUser(request).getId();
        Instant now = clock.instant();
        Date claimedAt = Date.from(now);
        LocalDate localDate = LocalDate.ofInstant(now, properties.resolveZoneId());
        var result = progressService.claimQuest(new QuestClaimCommand(userId, questCode, localDate,
            claimedAt, properties.resolveZoneId(), properties.getTicket().getLotValidityDays(),
            properties.getPolicyVersion(), properties.getPolicyVersion()));
        return RestResult.ok(QuestClaimResponse.from(result));
    }

    @PatchMapping("ticker-preference")
    @RateLimit(key = "gamification-ticker-preference", count = 10, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<GamificationProfileResponse> updateTickerPreference(
        @Valid @RequestBody TickerPreferenceRequest input, HttpServletRequest request) {
        requireProgressEnabled();
        long userId = requireUser(request).getId();
        var profile = progressService.updateTickerOptOut(userId, input.optOut(),
            input.expectedVersion(), properties.getPolicyVersion());
        return RestResult.ok(GamificationProfileResponse.from(profile,
            progressService.getProfileSnapshot(userId, properties.getPolicyVersion()).nextLevelExp()));
    }

    @PostMapping("check-in")
    @RateLimit(key = "gamification-check-in", count = 10, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<CheckInResponse> checkIn(HttpServletRequest request) {
        requireQuestEnabled();
        long userId = requireUser(request).getId();
        return RestResult.ok(CheckInResponse.from(checkInService.checkIn(userId)));
    }

    private UserDetails requireUser(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        return user;
    }

    private void requireProgressEnabled() {
        if ((!properties.getQuest().isEnabled() && !properties.getRealm().isEnabled())
            || !properties.isConfigured()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }

    private void requireRealmEnabled() {
        if (!properties.getRealm().isEnabled() || !properties.isConfigured()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }

    private void requireQuestEnabled() {
        if (!properties.getEvent().isEnabled() || !properties.getQuest().isEnabled()
            || !properties.isConfigured()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }
}
