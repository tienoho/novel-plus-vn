package com.java2nb.novel.mapper;

import java.sql.JDBCType;
import java.util.Date;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BookOwnershipProofDynamicSqlSupport {
    public static final BookOwnershipProof bookOwnershipProof = new BookOwnershipProof();

    public static final SqlColumn<Long> id = bookOwnershipProof.id;
    public static final SqlColumn<Long> bookId = bookOwnershipProof.bookId;
    public static final SqlColumn<Long> authorId = bookOwnershipProof.authorId;
    public static final SqlColumn<Byte> proofType = bookOwnershipProof.proofType;
    public static final SqlColumn<String> fileUrl = bookOwnershipProof.fileUrl;
    public static final SqlColumn<String> fileHash = bookOwnershipProof.fileHash;
    public static final SqlColumn<String> note = bookOwnershipProof.note;
    public static final SqlColumn<Byte> verificationStatus = bookOwnershipProof.verificationStatus;
    public static final SqlColumn<Long> verifierId = bookOwnershipProof.verifierId;
    public static final SqlColumn<Date> verifiedAt = bookOwnershipProof.verifiedAt;
    public static final SqlColumn<Date> createTime = bookOwnershipProof.createTime;

    public static final class BookOwnershipProof extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);
        public final SqlColumn<Long> bookId = column("book_id", JDBCType.BIGINT);
        public final SqlColumn<Long> authorId = column("author_id", JDBCType.BIGINT);
        public final SqlColumn<Byte> proofType = column("proof_type", JDBCType.TINYINT);
        public final SqlColumn<String> fileUrl = column("file_url", JDBCType.VARCHAR);
        public final SqlColumn<String> fileHash = column("file_hash", JDBCType.CHAR);
        public final SqlColumn<String> note = column("note", JDBCType.VARCHAR);
        public final SqlColumn<Byte> verificationStatus = column("verification_status", JDBCType.TINYINT);
        public final SqlColumn<Long> verifierId = column("verifier_id", JDBCType.BIGINT);
        public final SqlColumn<Date> verifiedAt = column("verified_at", JDBCType.TIMESTAMP);
        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public BookOwnershipProof() {
            super("book_ownership_proof");
        }
    }
}
