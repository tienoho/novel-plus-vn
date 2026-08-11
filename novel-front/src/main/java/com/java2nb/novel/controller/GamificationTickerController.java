package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.TickerEntryResponse;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bảng chạy công khai: những lượt thắp đuốc gần nhất của người dùng đã bật hiển thị. Chỉ đọc,
 * không yêu cầu đăng nhập vì đây là nội dung công khai trên trang chủ.
 *
 * <p>Không bao giờ trả {@code userId} hay bất kỳ định danh nào ngoài nickname người dùng đã tự
 * đặt; nickname còn bị lọc lại bằng {@code SensitiveWordFilter} ở tầng service trước khi tới đây.
 */
@Validated
@RestController
@RequestMapping("gamification")
@RequiredArgsConstructor
public class GamificationTickerController {

    private static final int DEFAULT_LIMIT = 20;

    private final MonthlyTicketService monthlyTicketService;
    private final GamificationConfigProvider configProvider;

    @GetMapping("ticker")
    @RateLimit(key = "gamification-ticker", count = 60, timeWindowSeconds = 60,
        limitType = LimitType.IP)
    public RestResult<List<TickerEntryResponse>> getTicker(
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        if (!configProvider.current().isVoteEnabled()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_DISABLED);
        }
        return RestResult.ok(monthlyTicketService.listTicker(limit == 0 ? DEFAULT_LIMIT : limit)
            .stream().map(TickerEntryResponse::from).toList());
    }
}
