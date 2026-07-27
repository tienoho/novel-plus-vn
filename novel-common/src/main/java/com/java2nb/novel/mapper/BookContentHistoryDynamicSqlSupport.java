package com.java2nb.novel.mapper;

import java.sql.JDBCType;
import java.util.Date;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BookContentHistoryDynamicSqlSupport {
    public static final BookContentHistory bookContentHistory = new BookContentHistory();

    public static final SqlColumn<Long> id = bookContentHistory.id;
    public static final SqlColumn<Long> bookId = bookContentHistory.bookId;
    public static final SqlColumn<Long> indexId = bookContentHistory.indexId;
    public static final SqlColumn<Integer> versionNum = bookContentHistory.versionNum;
    public static final SqlColumn<String> indexName = bookContentHistory.indexName;
    public static final SqlColumn<String> content = bookContentHistory.content;
    public static final SqlColumn<Integer> wordCount = bookContentHistory.wordCount;
    public static final SqlColumn<String> contentHash = bookContentHistory.contentHash;
    public static final SqlColumn<Long> modifiedBy = bookContentHistory.modifiedBy;
    public static final SqlColumn<Byte> modifiedType = bookContentHistory.modifiedType;
    public static final SqlColumn<String> changeReason = bookContentHistory.changeReason;
    public static final SqlColumn<Date> createTime = bookContentHistory.createTime;
    public static final SqlColumn<Long> simHash = bookContentHistory.simHash;

    public static final class BookContentHistory extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);
        public final SqlColumn<Long> bookId = column("book_id", JDBCType.BIGINT);
        public final SqlColumn<Long> indexId = column("index_id", JDBCType.BIGINT);
        public final SqlColumn<Integer> versionNum = column("version_num", JDBCType.INTEGER);
        public final SqlColumn<String> indexName = column("index_name", JDBCType.VARCHAR);
        public final SqlColumn<String> content = column("content", JDBCType.LONGVARCHAR);
        public final SqlColumn<Integer> wordCount = column("word_count", JDBCType.INTEGER);
        public final SqlColumn<String> contentHash = column("content_hash", JDBCType.CHAR);
        public final SqlColumn<Long> modifiedBy = column("modified_by", JDBCType.BIGINT);
        public final SqlColumn<Byte> modifiedType = column("modified_type", JDBCType.TINYINT);
        public final SqlColumn<String> changeReason = column("change_reason", JDBCType.VARCHAR);
        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);
        public final SqlColumn<Long> simHash = column("sim_hash", JDBCType.BIGINT);

        public BookContentHistory() {
            super("book_content_history");
        }
    }
}
