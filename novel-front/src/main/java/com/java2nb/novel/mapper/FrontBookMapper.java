package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.Book;
import com.java2nb.novel.vo.BookSpVO;
import com.java2nb.novel.vo.BookVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Administrator
 */
public interface FrontBookMapper extends BookMapper {


    List<BookVO> searchByPage(BookSpVO params);

    List<BookVO> searchFuzzyCandidates(@Param("params") BookSpVO params, @Param("limit") int limit);

    void addVisitCount(@Param("bookId") Long bookId, @Param("visitCount") Integer visitCount);

    List<Book> listRecBookByCatId(@Param("catId") Integer catId);

    List<Book> listRecommendations(@Param("userId") Long userId,
        @Param("preferredCatId") Integer preferredCatId,
        @Param("maxAgeRating") Byte maxAgeRating,
        @Param("excludedBookId") Long excludedBookId,
        @Param("excludeKnown") boolean excludeKnown,
        @Param("limit") int limit);

    void addCommentCount(@Param("bookId") Long bookId);

    List<Book> queryNetworkPicBooks(@Param("localPicPrefix") String localPicPrefix, @Param("limit") Integer limit);

    /**
     * Truy vấn ngẫu nhiên tập tác phẩm theo điểm
     * @param limit số bản ghi cần truy vấn
     * @return tập tác phẩm
     * */
    List<Book> selectIdsByScoreAndRandom(@Param("limit") int limit);
}
