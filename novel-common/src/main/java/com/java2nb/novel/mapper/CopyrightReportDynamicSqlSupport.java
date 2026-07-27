package com.java2nb.novel.mapper;

import java.sql.JDBCType;
import java.util.Date;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class CopyrightReportDynamicSqlSupport {
    public static final CopyrightReport copyrightReport = new CopyrightReport();

    public static final SqlColumn<Long> id = copyrightReport.id;
    public static final SqlColumn<String> reportNo = copyrightReport.reportNo;
    public static final SqlColumn<Long> reporterId = copyrightReport.reporterId;
    public static final SqlColumn<String> reporterName = copyrightReport.reporterName;
    public static final SqlColumn<String> reporterEmail = copyrightReport.reporterEmail;
    public static final SqlColumn<String> reporterPhone = copyrightReport.reporterPhone;
    public static final SqlColumn<Byte> reporterType = copyrightReport.reporterType;
    public static final SqlColumn<Byte> targetType = copyrightReport.targetType;
    public static final SqlColumn<Long> targetId = copyrightReport.targetId;
    public static final SqlColumn<String> targetName = copyrightReport.targetName;
    public static final SqlColumn<String> originalWorkName = copyrightReport.originalWorkName;
    public static final SqlColumn<String> originalWorkUrl = copyrightReport.originalWorkUrl;
    public static final SqlColumn<Byte> violationType = copyrightReport.violationType;
    public static final SqlColumn<String> description = copyrightReport.description;
    public static final SqlColumn<String> evidenceUrls = copyrightReport.evidenceUrls;
    public static final SqlColumn<Byte> status = copyrightReport.status;
    public static final SqlColumn<String> reviewResult = copyrightReport.reviewResult;
    public static final SqlColumn<Long> reviewerId = copyrightReport.reviewerId;
    public static final SqlColumn<Date> reviewedAt = copyrightReport.reviewedAt;
    public static final SqlColumn<Date> createTime = copyrightReport.createTime;
    public static final SqlColumn<Date> updateTime = copyrightReport.updateTime;

    public static final class CopyrightReport extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);
        public final SqlColumn<String> reportNo = column("report_no", JDBCType.VARCHAR);
        public final SqlColumn<Long> reporterId = column("reporter_id", JDBCType.BIGINT);
        public final SqlColumn<String> reporterName = column("reporter_name", JDBCType.VARCHAR);
        public final SqlColumn<String> reporterEmail = column("reporter_email", JDBCType.VARCHAR);
        public final SqlColumn<String> reporterPhone = column("reporter_phone", JDBCType.VARCHAR);
        public final SqlColumn<Byte> reporterType = column("reporter_type", JDBCType.TINYINT);
        public final SqlColumn<Byte> targetType = column("target_type", JDBCType.TINYINT);
        public final SqlColumn<Long> targetId = column("target_id", JDBCType.BIGINT);
        public final SqlColumn<String> targetName = column("target_name", JDBCType.VARCHAR);
        public final SqlColumn<String> originalWorkName = column("original_work_name", JDBCType.VARCHAR);
        public final SqlColumn<String> originalWorkUrl = column("original_work_url", JDBCType.VARCHAR);
        public final SqlColumn<Byte> violationType = column("violation_type", JDBCType.TINYINT);
        public final SqlColumn<String> description = column("description", JDBCType.LONGVARCHAR);
        public final SqlColumn<String> evidenceUrls = column("evidence_urls", JDBCType.VARCHAR);
        public final SqlColumn<Byte> status = column("status", JDBCType.TINYINT);
        public final SqlColumn<String> reviewResult = column("review_result", JDBCType.VARCHAR);
        public final SqlColumn<Long> reviewerId = column("reviewer_id", JDBCType.BIGINT);
        public final SqlColumn<Date> reviewedAt = column("reviewed_at", JDBCType.TIMESTAMP);
        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);
        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public CopyrightReport() {
            super("copyright_report");
        }
    }
}
