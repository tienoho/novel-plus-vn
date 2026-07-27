package com.java2nb.novel.service.analytics;

import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.dto.analytics.ReaderAnalyticsEventInput;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.mapper.ReaderAnalyticsMapper;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthorAnalyticsServiceImpl implements AuthorAnalyticsService {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9:_-]{8,96}");
    private static final Pattern VISITOR_ID = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Set<String> EVENT_TYPES = Set.of("START", "PROGRESS", "COMPLETE");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReaderAnalyticsMapper analyticsMapper;
    private final BookIndexMapper bookIndexMapper;
    private final AuthorBookCollaborationService collaborationService;

    @Override
    public void recordReadEvent(ReaderAnalyticsEventInput input) {
        if (input == null || input.getBookId() == null || input.getIndexId() == null) {
            throw new IllegalArgumentException("Thiếu thông tin truyện hoặc chương");
        }
        if (input.getClientEventId() == null || !CLIENT_ID.matcher(input.getClientEventId()).matches()) {
            throw new IllegalArgumentException("Mã sự kiện đọc không hợp lệ");
        }
        if (input.getVisitorId() == null || !VISITOR_ID.matcher(input.getVisitorId()).matches()) {
            throw new IllegalArgumentException("Mã độc giả ẩn danh không hợp lệ");
        }

        String eventType = input.getEventType() == null
            ? ""
            : input.getEventType().trim().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Loại sự kiện đọc không hợp lệ");
        }

        BookIndex index = bookIndexMapper.selectByPrimaryKey(input.getIndexId()).orElse(null);
        if (index == null || !input.getBookId().equals(index.getBookId())) {
            throw new IllegalArgumentException("Chương không thuộc truyện đã khai báo");
        }

        int progress = input.getProgressPercent() == null ? 0 : input.getProgressPercent();
        int duration = input.getDurationSeconds() == null ? 0 : input.getDurationSeconds();
        if (progress < 0 || progress > 100 || duration < 0 || duration > 86400) {
            throw new IllegalArgumentException("Tiến độ hoặc thời lượng đọc không hợp lệ");
        }
        if ("START".equals(eventType)) {
            progress = 0;
        } else if ("COMPLETE".equals(eventType)) {
            progress = 100;
        }

        ReaderAnalyticsEventRow row = new ReaderAnalyticsEventRow();
        row.setClientEventId(input.getClientEventId());
        row.setReaderKeyHash(ContentHashUtil.sha256Hex("ANALYTICS_V1:" + input.getVisitorId()));
        row.setBookId(input.getBookId());
        row.setIndexId(input.getIndexId());
        row.setEventType(eventType);
        row.setProgressPercent(progress);
        row.setDurationSeconds(duration);
        analyticsMapper.insertEvent(row);
    }

    @Override
    public AuthorAnalyticsSummary getSummary(long authorId, long bookId, LocalDate startDate, LocalDate endDate) {
        DateRange range = validateAccessAndRange(authorId, bookId, startDate, endDate);
        return analyticsMapper.selectSummary(
            bookId, range.ownerAuthorId(), range.startTime(), range.endTime());
    }

    @Override
    public AuthorAnalyticsPage getChapterAnalytics(long authorId, long bookId, LocalDate startDate,
                                                    LocalDate endDate, int page, int limit) {
        DateRange range = validateAccessAndRange(authorId, bookId, startDate, endDate);
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Phân trang analytics không hợp lệ");
        }
        long total = analyticsMapper.countChapters(bookId);
        int offset = Math.multiplyExact(page - 1, limit);
        return new AuthorAnalyticsPage(
            analyticsMapper.selectChapterAnalytics(bookId, range.ownerAuthorId(), range.startTime(), range.endTime(),
                offset, limit),
            total,
            page,
            limit
        );
    }

    private DateRange validateAccessAndRange(long authorId, long bookId, LocalDate startDate, LocalDate endDate) {
        AuthorBookAccess access = collaborationService.requirePermission(
            authorId, bookId, BookPermission.VIEW_ANALYTICS);

        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate normalizedEnd = endDate == null ? today : endDate;
        LocalDate normalizedStart = startDate == null ? normalizedEnd.minusDays(29) : startDate;
        long days = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd);
        if (days < 0 || days > 365) {
            throw new IllegalArgumentException("Khoảng thời gian analytics phải từ 1 đến 366 ngày");
        }

        Date start = Date.from(normalizedStart.atStartOfDay(VIETNAM_ZONE).toInstant());
        Date endExclusive = Date.from(normalizedEnd.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant());
        return new DateRange(access.getOwnerAuthorId(), start, endExclusive);
    }

    private record DateRange(long ownerAuthorId, Date startTime, Date endTime) {
    }
}
