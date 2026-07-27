package com.java2nb.novel.mapper;

import static com.java2nb.novel.mapper.BookOwnershipProofDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.java2nb.novel.entity.BookOwnershipProof;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

@Mapper
public interface BookOwnershipProofMapper {
    BasicColumn[] selectList = BasicColumn.columnList(id, bookId, authorId, proofType, fileUrl, fileHash, note, verificationStatus, verifierId, verifiedAt, createTime);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BookOwnershipProof> insertStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("BookOwnershipProofResult")
    Optional<BookOwnershipProof> selectOne(SelectStatementProvider selectStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="BookOwnershipProofResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="book_id", property="bookId", jdbcType=JdbcType.BIGINT),
        @Result(column="author_id", property="authorId", jdbcType=JdbcType.BIGINT),
        @Result(column="proof_type", property="proofType", jdbcType=JdbcType.TINYINT),
        @Result(column="file_url", property="fileUrl", jdbcType=JdbcType.VARCHAR),
        @Result(column="file_hash", property="fileHash", jdbcType=JdbcType.CHAR),
        @Result(column="note", property="note", jdbcType=JdbcType.VARCHAR),
        @Result(column="verification_status", property="verificationStatus", jdbcType=JdbcType.TINYINT),
        @Result(column="verifier_id", property="verifierId", jdbcType=JdbcType.BIGINT),
        @Result(column="verified_at", property="verifiedAt", jdbcType=JdbcType.TIMESTAMP),
        @Result(column="create_time", property="createTime", jdbcType=JdbcType.TIMESTAMP)
    })
    List<BookOwnershipProof> selectMany(SelectStatementProvider selectStatement);

    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, bookOwnershipProof, completer);
    }

    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, bookOwnershipProof, completer);
    }

    default int insert(BookOwnershipProof record) {
        return MyBatis3Utils.insert(this::insert, record, bookOwnershipProof, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(bookId).toPropertyWhenPresent("bookId", record::getBookId)
            .map(authorId).toPropertyWhenPresent("authorId", record::getAuthorId)
            .map(proofType).toPropertyWhenPresent("proofType", record::getProofType)
            .map(fileUrl).toPropertyWhenPresent("fileUrl", record::getFileUrl)
            .map(fileHash).toPropertyWhenPresent("fileHash", record::getFileHash)
            .map(note).toPropertyWhenPresent("note", record::getNote)
            .map(verificationStatus).toPropertyWhenPresent("verificationStatus", record::getVerificationStatus)
            .map(verifierId).toPropertyWhenPresent("verifierId", record::getVerifierId)
            .map(verifiedAt).toPropertyWhenPresent("verifiedAt", record::getVerifiedAt)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
        );
    }

    default Optional<BookOwnershipProof> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, bookOwnershipProof, completer);
    }

    default List<BookOwnershipProof> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, bookOwnershipProof, completer);
    }

    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, bookOwnershipProof, completer);
    }

    default int updateByPrimaryKeySelective(BookOwnershipProof record) {
        return update(c ->
            c.set(bookId).equalToWhenPresent(record::getBookId)
            .set(authorId).equalToWhenPresent(record::getAuthorId)
            .set(proofType).equalToWhenPresent(record::getProofType)
            .set(fileUrl).equalToWhenPresent(record::getFileUrl)
            .set(fileHash).equalToWhenPresent(record::getFileHash)
            .set(note).equalToWhenPresent(record::getNote)
            .set(verificationStatus).equalToWhenPresent(record::getVerificationStatus)
            .set(verifierId).equalToWhenPresent(record::getVerifierId)
            .set(verifiedAt).equalToWhenPresent(record::getVerifiedAt)
            .where(id, isEqualTo(record::getId))
        );
    }

    default Optional<BookOwnershipProof> selectByPrimaryKey(Long id_) {
        return selectOne(c -> c.where(id, isEqualTo(id_)));
    }

    default int insertSelective(BookOwnershipProof record) {
        return insert(record);
    }
}
