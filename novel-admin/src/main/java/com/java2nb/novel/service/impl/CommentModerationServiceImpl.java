package com.java2nb.novel.service.impl;

import com.java2nb.novel.config.GamificationAdminSettings;
import com.java2nb.novel.entity.BookComment;
import com.java2nb.novel.mapper.BookCommentDynamicSqlSupport;
import com.java2nb.novel.mapper.BookCommentMapper;
import com.java2nb.novel.service.CommentModerationService;
import com.java2nb.novel.service.gamification.GamificationEventInputFactory;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.LinkedHashSet;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

@Service
public class CommentModerationServiceImpl implements CommentModerationService {

    private final BookCommentMapper mapper;
    private final GamificationEventRecorder eventRecorder;
    private final GamificationAdminSettings settings;
    private final Clock clock;

    @Autowired
    public CommentModerationServiceImpl(BookCommentMapper mapper, GamificationEventRecorder eventRecorder,
                                        GamificationAdminSettings settings) {
        this(mapper, eventRecorder, settings, Clock.systemUTC());
    }

    CommentModerationServiceImpl(BookCommentMapper mapper, GamificationEventRecorder eventRecorder,
                                 GamificationAdminSettings settings, Clock clock) {
        this.mapper = mapper;
        this.eventRecorder = eventRecorder;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAudit(Long[] ids, byte auditStatus) {
        if (auditStatus < 0 || auditStatus > 2) {
            throw new IllegalArgumentException("Trạng thái kiểm duyệt bình luận không hợp lệ");
        }
        if (ids == null || ids.length == 0) {
            return 0;
        }
        int affected = 0;
        for (Long id : new LinkedHashSet<>(java.util.Arrays.asList(ids))) {
            if (id == null) {
                continue;
            }
            BookComment comment = mapper.selectByPrimaryKey(id).orElse(null);
            if (comment == null) {
                continue;
            }
            int changed = mapper.update(c -> c.set(BookCommentDynamicSqlSupport.auditStatus)
                .equalTo(auditStatus)
                .where(BookCommentDynamicSqlSupport.id, isEqualTo(id))
                .and(BookCommentDynamicSqlSupport.auditStatus, isNotEqualTo(auditStatus)));
            affected += changed;
            if (changed == 1 && auditStatus == 1 && settings.getEvent().isEnabled()) {
                if (!settings.isConfigured()) {
                    throw new IllegalStateException("Cấu hình gamification admin không hợp lệ khi ghi sự kiện");
                }
                Date occurredAt = Date.from(clock.instant());
                eventRecorder.ingest(GamificationEventInputFactory.create("COMMENT_APPROVED",
                    "GAMIFY:COMMENT_APPROVED:" + id, comment.getCreateUserId(), comment.getBookId(),
                    occurredAt, null, settings.resolveZoneId(), settings.getPolicyVersion()));
            }
        }
        return affected;
    }
}
