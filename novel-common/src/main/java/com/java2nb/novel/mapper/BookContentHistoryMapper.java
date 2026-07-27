package com.java2nb.novel.mapper;

import static com.java2nb.novel.mapper.BookContentHistoryDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.java2nb.novel.entity.BookContentHistory;
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
public interface BookContentHistoryMapper {
    BasicColumn[] selectList = BasicColumn.columnList(id, bookId, indexId, versionNum, indexName, content, wordCount, contentHash, modifiedBy, modifiedType, changeReason, createTime);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BookContentHistory> insertStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("BookContentHistoryResult")
    Optional<BookContentHistory> selectOne(SelectStatementProvider selectStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="BookContentHistoryResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="book_id", property="bookId", jdbcType=JdbcType.BIGINT),
        @Result(column="index_id", property="indexId", jdbcType=JdbcType.BIGINT),
        @Result(column="version_num", property="versionNum", jdbcType=JdbcType.INTEGER),
        @Result(column="index_name", property="indexName", jdbcType=JdbcType.VARCHAR),
        @Result(column="content", property="content", jdbcType=JdbcType.LONGVARCHAR),
        @Result(column="word_count", property="wordCount", jdbcType=JdbcType.INTEGER),
        @Result(column="content_hash", property="contentHash", jdbcType=JdbcType.CHAR),
        @Result(column="modified_by", property="modifiedBy", jdbcType=JdbcType.BIGINT),
        @Result(column="modified_type", property="modifiedType", jdbcType=JdbcType.TINYINT),
        @Result(column="change_reason", property="changeReason", jdbcType=JdbcType.VARCHAR),
        @Result(column="create_time", property="createTime", jdbcType=JdbcType.TIMESTAMP)
    })
    List<BookContentHistory> selectMany(SelectStatementProvider selectStatement);

    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, bookContentHistory, completer);
    }

    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, bookContentHistory, completer);
    }

    default int insert(BookContentHistory record) {
        return MyBatis3Utils.insert(this::insert, record, bookContentHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(bookId).toPropertyWhenPresent("bookId", record::getBookId)
            .map(indexId).toPropertyWhenPresent("indexId", record::getIndexId)
            .map(versionNum).toPropertyWhenPresent("versionNum", record::getVersionNum)
            .map(indexName).toPropertyWhenPresent("indexName", record::getIndexName)
            .map(content).toPropertyWhenPresent("content", record::getContent)
            .map(wordCount).toPropertyWhenPresent("wordCount", record::getWordCount)
            .map(contentHash).toPropertyWhenPresent("contentHash", record::getContentHash)
            .map(modifiedBy).toPropertyWhenPresent("modifiedBy", record::getModifiedBy)
            .map(modifiedType).toPropertyWhenPresent("modifiedType", record::getModifiedType)
            .map(changeReason).toPropertyWhenPresent("changeReason", record::getChangeReason)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
        );
    }

    default Optional<BookContentHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, bookContentHistory, completer);
    }

    default List<BookContentHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, bookContentHistory, completer);
    }

    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, bookContentHistory, completer);
    }

    default Optional<BookContentHistory> selectByPrimaryKey(Long id_) {
        return selectOne(c -> c.where(id, isEqualTo(id_)));
    }

    default int insertSelective(BookContentHistory record) {
        return insert(record);
    }
}
