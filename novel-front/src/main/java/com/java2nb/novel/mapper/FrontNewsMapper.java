package com.java2nb.novel.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * @author Administrator
 */
public interface FrontNewsMapper extends NewsMapper {

    /**
     * Tăng lượt đọc tin tức
     * @param newsId ID tin tức
     * */
    void addReadCount(@Param("newsId") Integer newsId);

}
