package com.java2nb.novel.mapper;

import static com.java2nb.novel.mapper.SensitiveWordDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.java2nb.novel.entity.SensitiveWord;
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
public interface SensitiveWordMapper {
    BasicColumn[] selectList = BasicColumn.columnList(id, word, category, replacement, status, createTime);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<SensitiveWord> insertStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ResultMap("SensitiveWordResult")
    Optional<SensitiveWord> selectOne(SelectStatementProvider selectStatement);

    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @Results(id="SensitiveWordResult", value = {
        @Result(column="id", property="id", jdbcType=JdbcType.BIGINT, id=true),
        @Result(column="word", property="word", jdbcType=JdbcType.VARCHAR),
        @Result(column="category", property="category", jdbcType=JdbcType.VARCHAR),
        @Result(column="replacement", property="replacement", jdbcType=JdbcType.VARCHAR),
        @Result(column="status", property="status", jdbcType=JdbcType.TINYINT),
        @Result(column="create_time", property="createTime", jdbcType=JdbcType.TIMESTAMP)
    })
    List<SensitiveWord> selectMany(SelectStatementProvider selectStatement);

    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, sensitiveWord, completer);
    }

    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, sensitiveWord, completer);
    }

    default int insert(SensitiveWord record) {
        return MyBatis3Utils.insert(this::insert, record, sensitiveWord, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(word).toPropertyWhenPresent("word", record::getWord)
            .map(category).toPropertyWhenPresent("category", record::getCategory)
            .map(replacement).toPropertyWhenPresent("replacement", record::getReplacement)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
        );
    }

    default Optional<SensitiveWord> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, sensitiveWord, completer);
    }

    default List<SensitiveWord> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, sensitiveWord, completer);
    }

    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, sensitiveWord, completer);
    }

    default int updateByPrimaryKeySelective(SensitiveWord record) {
        return update(c ->
            c.set(word).equalToWhenPresent(record::getWord)
            .set(category).equalToWhenPresent(record::getCategory)
            .set(replacement).equalToWhenPresent(record::getReplacement)
            .set(status).equalToWhenPresent(record::getStatus)
            .where(id, isEqualTo(record::getId))
        );
    }
}
