package com.java2nb.novel.mapper;

import java.sql.JDBCType;
import java.util.Date;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class CopyrightAppealDynamicSqlSupport {
    public static final CopyrightAppeal copyrightAppeal = new CopyrightAppeal();

    public static final SqlColumn<Long> id = copyrightAppeal.id;
    public static final SqlColumn<Long> reportId = copyrightAppeal.reportId;
    public static final SqlColumn<Long> bookId = copyrightAppeal.bookId;
    public static final SqlColumn<Long> authorId = copyrightAppeal.authorId;
    public static final SqlColumn<String> appealReason = copyrightAppeal.appealReason;
    public static final SqlColumn<String> proofUrls = copyrightAppeal.proofUrls;
    public static final SqlColumn<Byte> status = copyrightAppeal.status;
    public static final SqlColumn<String> reviewRemark = copyrightAppeal.reviewRemark;
    public static final SqlColumn<Long> reviewerId = copyrightAppeal.reviewerId;
    public static final SqlColumn<Date> reviewedAt = copyrightAppeal.reviewedAt;
    public static final SqlColumn<Date> createTime = copyrightAppeal.createTime;

    public static final class CopyrightAppeal extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);
        public final SqlColumn<Long> reportId = column("report_id", JDBCType.BIGINT);
        public final SqlColumn<Long> bookId = column("book_id", JDBCType.BIGINT);
        public final SqlColumn<Long> authorId = column("author_id", JDBCType.BIGINT);
        public final SqlColumn<String> appealReason = column("appeal_reason", JDBCType.LONGVARCHAR);
        public final SqlColumn<String> proofUrls = column("proof_urls", JDBCType.VARCHAR);
        public final SqlColumn<Byte> status = column("status", JDBCType.TINYINT);
        public final SqlColumn<String> reviewRemark = column("review_remark", JDBCType.VARCHAR);
        public final SqlColumn<Long> reviewerId = column("reviewer_id", JDBCType.BIGINT);
        public final SqlColumn<Date> reviewedAt = column("reviewed_at", JDBCType.TIMESTAMP);
        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public CopyrightAppeal() {
            super("copyright_appeal");
        }
    }
}
