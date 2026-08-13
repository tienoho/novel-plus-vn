package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.BookComment;
import com.java2nb.novel.mapper.BookCommentDynamicSqlSupport;
import com.java2nb.novel.mapper.BookCommentMapper;
import com.java2nb.novel.service.CommentModerationService;
import com.java2nb.novel.service.gamification.GamificationEventInputFactory;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
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
    private final GamificationConfigProvider configProvider;
    private final Clock clock;

    @Autowired
    public CommentModerationServiceImpl(BookCommentMapper mapper, GamificationEventRecorder eventRecorder,
                                        GamificationConfigProvider configProvider) {
        this(mapper, eventRecorder, configProvider, Clock.systemUTC());
    }

    CommentModerationServiceImpl(BookCommentMapper mapper, GamificationEventRecorder eventRecorder,
                                 GamificationConfigProvider configProvider, Clock clock) {
        this.mapper = mapper;
        this.eventRecorder = eventRecorder;
        this.configProvider = configProvider;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAudit(Long[] ids, byte auditStatus) {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
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
            if (changed == 1 && auditStatus == 1 && config.isEventEnabled()) {
                Date occurredAt = Date.from(clock.instant());
                eventRecorder.ingest(GamificationEventInputFactory.create("COMMENT_APPROVED",
                    "GAMIFY:COMMENT_APPROVED:" + id, comment.getCreateUserId(), comment.getBookId(),
                    occurredAt, null, java.time.ZoneId.of(config.getZoneId()),
                    config.getPolicyVersion(), config.getRuntimeRevision()));
            }
        }
        return affected;
    }
}
