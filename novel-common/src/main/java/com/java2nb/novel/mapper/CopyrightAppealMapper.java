package com.java2nb.novel.mapper;

import static com.java2nb.novel.mapper.CopyrightAppealDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.java2nb.novel.entity.CopyrightAppeal;
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
public interface CopyrightAppealMapper {
    BasicColumn[] selectList = BasicColumn.columnList(id, reportId, bookId, authorId, appealReason, proofUrls, status, reviewRemark, reviewerId, reviewedAt, createTime);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<CopyrightAppeal> insertStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("CopyrightAppealResult")
    Optional<CopyrightAppeal> selectOne(SelectStatementProvider selectStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="CopyrightAppealResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="report_id", property="reportId", jdbcType=JdbcType.BIGINT),
        @Result(column="book_id", property="bookId", jdbcType=JdbcType.BIGINT),
        @Result(column="author_id", property="authorId", jdbcType=JdbcType.BIGINT),
        @Result(column="appeal_reason", property="appealReason", jdbcType=JdbcType.LONGVARCHAR),
        @Result(column="proof_urls", property="proofUrls", jdbcType=JdbcType.VARCHAR),
        @Result(column="status", property="status", jdbcType=JdbcType.TINYINT),
        @Result(column="review_remark", property="reviewRemark", jdbcType=JdbcType.VARCHAR),
        @Result(column="reviewer_id", property="reviewerId", jdbcType=JdbcType.BIGINT),
        @Result(column="reviewed_at", property="reviewedAt", jdbcType=JdbcType.TIMESTAMP),
        @Result(column="create_time", property="createTime", jdbcType=JdbcType.TIMESTAMP)
    })
    List<CopyrightAppeal> selectMany(SelectStatementProvider selectStatement);

    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, copyrightAppeal, completer);
    }

    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, copyrightAppeal, completer);
    }

    default int insert(CopyrightAppeal record) {
        return MyBatis3Utils.insert(this::insert, record, copyrightAppeal, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(reportId).toPropertyWhenPresent("reportId", record::getReportId)
            .map(bookId).toPropertyWhenPresent("bookId", record::getBookId)
            .map(authorId).toPropertyWhenPresent("authorId", record::getAuthorId)
            .map(appealReason).toPropertyWhenPresent("appealReason", record::getAppealReason)
            .map(proofUrls).toPropertyWhenPresent("proofUrls", record::getProofUrls)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(reviewRemark).toPropertyWhenPresent("reviewRemark", record::getReviewRemark)
            .map(reviewerId).toPropertyWhenPresent("reviewerId", record::getReviewerId)
            .map(reviewedAt).toPropertyWhenPresent("reviewedAt", record::getReviewedAt)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
        );
    }

    default Optional<CopyrightAppeal> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, copyrightAppeal, completer);
    }

    default List<CopyrightAppeal> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, copyrightAppeal, completer);
    }

    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, copyrightAppeal, completer);
    }

    default int updateByPrimaryKeySelective(CopyrightAppeal record) {
        return update(c ->
            c.set(reportId).equalToWhenPresent(record::getReportId)
            .set(bookId).equalToWhenPresent(record::getBookId)
            .set(authorId).equalToWhenPresent(record::getAuthorId)
            .set(appealReason).equalToWhenPresent(record::getAppealReason)
            .set(proofUrls).equalToWhenPresent(record::getProofUrls)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(reviewRemark).equalToWhenPresent(record::getReviewRemark)
            .set(reviewerId).equalToWhenPresent(record::getReviewerId)
            .set(reviewedAt).equalToWhenPresent(record::getReviewedAt)
            .where(id, isEqualTo(record::getId))
        );
    }

    default Optional<CopyrightAppeal> selectByPrimaryKey(Long id_) {
        return selectOne(c -> c.where(id, isEqualTo(id_)));
    }

    default int insertSelective(CopyrightAppeal record) {
        return insert(record);
    }
}
