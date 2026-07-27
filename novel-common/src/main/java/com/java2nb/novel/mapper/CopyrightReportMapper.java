package com.java2nb.novel.mapper;

import static com.java2nb.novel.mapper.CopyrightReportDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.java2nb.novel.entity.CopyrightReport;
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
public interface CopyrightReportMapper {
    BasicColumn[] selectList = BasicColumn.columnList(id, reportNo, reporterId, reporterName, reporterEmail, reporterPhone, reporterType, targetType, targetId, targetName, originalWorkName, originalWorkUrl, violationType, description, evidenceUrls, status, reviewResult, reviewerId, reviewedAt, createTime, updateTime);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<CopyrightReport> insertStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("CopyrightReportResult")
    Optional<CopyrightReport> selectOne(SelectStatementProvider selectStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="CopyrightReportResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="report_no", property="reportNo", jdbcType=JdbcType.VARCHAR),
        @Result(column="reporter_id", property="reporterId", jdbcType=JdbcType.BIGINT),
        @Result(column="reporter_name", property="reporterName", jdbcType=JdbcType.VARCHAR),
        @Result(column="reporter_email", property="reporterEmail", jdbcType=JdbcType.VARCHAR),
        @Result(column="reporter_phone", property="reporterPhone", jdbcType=JdbcType.VARCHAR),
        @Result(column="reporter_type", property="reporterType", jdbcType=JdbcType.TINYINT),
        @Result(column="target_type", property="targetType", jdbcType=JdbcType.TINYINT),
        @Result(column="target_id", property="targetId", jdbcType=JdbcType.BIGINT),
        @Result(column="target_name", property="targetName", jdbcType=JdbcType.VARCHAR),
        @Result(column="original_work_name", property="originalWorkName", jdbcType=JdbcType.VARCHAR),
        @Result(column="original_work_url", property="originalWorkUrl", jdbcType=JdbcType.VARCHAR),
        @Result(column="violation_type", property="violationType", jdbcType=JdbcType.TINYINT),
        @Result(column="description", property="description", jdbcType=JdbcType.LONGVARCHAR),
        @Result(column="evidence_urls", property="evidenceUrls", jdbcType=JdbcType.VARCHAR),
        @Result(column="status", property="status", jdbcType=JdbcType.TINYINT),
        @Result(column="review_result", property="reviewResult", jdbcType=JdbcType.VARCHAR),
        @Result(column="reviewer_id", property="reviewerId", jdbcType=JdbcType.BIGINT),
        @Result(column="reviewed_at", property="reviewedAt", jdbcType=JdbcType.TIMESTAMP),
        @Result(column="create_time", property="createTime", jdbcType=JdbcType.TIMESTAMP),
        @Result(column="update_time", property="updateTime", jdbcType=JdbcType.TIMESTAMP)
    })
    List<CopyrightReport> selectMany(SelectStatementProvider selectStatement);

    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, copyrightReport, completer);
    }

    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, copyrightReport, completer);
    }

    default int insert(CopyrightReport record) {
        return MyBatis3Utils.insert(this::insert, record, copyrightReport, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(reportNo).toPropertyWhenPresent("reportNo", record::getReportNo)
            .map(reporterId).toPropertyWhenPresent("reporterId", record::getReporterId)
            .map(reporterName).toPropertyWhenPresent("reporterName", record::getReporterName)
            .map(reporterEmail).toPropertyWhenPresent("reporterEmail", record::getReporterEmail)
            .map(reporterPhone).toPropertyWhenPresent("reporterPhone", record::getReporterPhone)
            .map(reporterType).toPropertyWhenPresent("reporterType", record::getReporterType)
            .map(targetType).toPropertyWhenPresent("targetType", record::getTargetType)
            .map(targetId).toPropertyWhenPresent("targetId", record::getTargetId)
            .map(targetName).toPropertyWhenPresent("targetName", record::getTargetName)
            .map(originalWorkName).toPropertyWhenPresent("originalWorkName", record::getOriginalWorkName)
            .map(originalWorkUrl).toPropertyWhenPresent("originalWorkUrl", record::getOriginalWorkUrl)
            .map(violationType).toPropertyWhenPresent("violationType", record::getViolationType)
            .map(description).toPropertyWhenPresent("description", record::getDescription)
            .map(evidenceUrls).toPropertyWhenPresent("evidenceUrls", record::getEvidenceUrls)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(reviewResult).toPropertyWhenPresent("reviewResult", record::getReviewResult)
            .map(reviewerId).toPropertyWhenPresent("reviewerId", record::getReviewerId)
            .map(reviewedAt).toPropertyWhenPresent("reviewedAt", record::getReviewedAt)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
        );
    }

    default Optional<CopyrightReport> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, copyrightReport, completer);
    }

    default List<CopyrightReport> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, copyrightReport, completer);
    }

    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, copyrightReport, completer);
    }

    default int updateByPrimaryKeySelective(CopyrightReport record) {
        return update(c ->
            c.set(reportNo).equalToWhenPresent(record::getReportNo)
            .set(reporterId).equalToWhenPresent(record::getReporterId)
            .set(reporterName).equalToWhenPresent(record::getReporterName)
            .set(reporterEmail).equalToWhenPresent(record::getReporterEmail)
            .set(reporterPhone).equalToWhenPresent(record::getReporterPhone)
            .set(reporterType).equalToWhenPresent(record::getReporterType)
            .set(targetType).equalToWhenPresent(record::getTargetType)
            .set(targetId).equalToWhenPresent(record::getTargetId)
            .set(targetName).equalToWhenPresent(record::getTargetName)
            .set(originalWorkName).equalToWhenPresent(record::getOriginalWorkName)
            .set(originalWorkUrl).equalToWhenPresent(record::getOriginalWorkUrl)
            .set(violationType).equalToWhenPresent(record::getViolationType)
            .set(description).equalToWhenPresent(record::getDescription)
            .set(evidenceUrls).equalToWhenPresent(record::getEvidenceUrls)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(reviewResult).equalToWhenPresent(record::getReviewResult)
            .set(reviewerId).equalToWhenPresent(record::getReviewerId)
            .set(reviewedAt).equalToWhenPresent(record::getReviewedAt)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .where(id, isEqualTo(record::getId))
        );
    }

    default Optional<CopyrightReport> selectByPrimaryKey(Long id_) {
        return selectOne(c -> c.where(id, isEqualTo(id_)));
    }

    default int insertSelective(CopyrightReport record) {
        return insert(record);
    }
}
