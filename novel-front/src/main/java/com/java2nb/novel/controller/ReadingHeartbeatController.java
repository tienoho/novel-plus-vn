package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.ReadingHeartbeatRequest;
import com.java2nb.novel.dto.gamification.ReadingHeartbeatResponse;
import com.java2nb.novel.service.gamification.GamificationReadingHeartbeatService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("user/gamification")
public class ReadingHeartbeatController extends BaseController {

    private final GamificationReadingHeartbeatService heartbeatService;
    private final GamificationConfigProvider configProvider;

    @PostMapping("reading-heartbeat")
    @RateLimit(key = "gamification-reading-heartbeat", count = 30, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<ReadingHeartbeatResponse> record(
        @Valid @RequestBody ReadingHeartbeatRequest input, HttpServletRequest request) {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        requireEnabled(config);
        long userId = requireUser(request).getId();
        return RestResult.ok(ReadingHeartbeatResponse.from(
            heartbeatService.record(userId, input.toInput(), config)));
    }

    private UserDetails requireUser(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        return user;
    }

    private void requireEnabled(GamificationConfigSnapshot config) {
        if (!config.isEventEnabled() || !config.isQuestEnabled()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
    }
}
