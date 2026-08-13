package com.java2nb.novel.mapper;

import com.java2nb.novel.service.chapter.ChapterCommercialPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterCommercialPolicyMapper {
    ChapterCommercialPolicy selectByBookIndexId(@Param("bookIndexId") long bookIndexId);

    int upsert(ChapterCommercialPolicy policy);

    int deleteByBookIndexId(@Param("bookIndexId") long bookIndexId);
}
