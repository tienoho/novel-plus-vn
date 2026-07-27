package com.java2nb.novel.mapper;

import java.sql.JDBCType;
import java.util.Date;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class SensitiveWordDynamicSqlSupport {
    public static final SensitiveWord sensitiveWord = new SensitiveWord();

    public static final SqlColumn<Long> id = sensitiveWord.id;
    public static final SqlColumn<String> word = sensitiveWord.word;
    public static final SqlColumn<String> category = sensitiveWord.category;
    public static final SqlColumn<String> replacement = sensitiveWord.replacement;
    public static final SqlColumn<Byte> status = sensitiveWord.status;
    public static final SqlColumn<Date> createTime = sensitiveWord.createTime;

    public static final class SensitiveWord extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);
        public final SqlColumn<String> word = column("word", JDBCType.VARCHAR);
        public final SqlColumn<String> category = column("category", JDBCType.VARCHAR);
        public final SqlColumn<String> replacement = column("replacement", JDBCType.VARCHAR);
        public final SqlColumn<Byte> status = column("status", JDBCType.TINYINT);
        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public SensitiveWord() {
            super("sensitive_word");
        }
    }
}
