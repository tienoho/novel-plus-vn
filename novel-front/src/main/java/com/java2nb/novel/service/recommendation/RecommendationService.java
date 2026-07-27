package com.java2nb.novel.service.recommendation;

import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.vo.BookSettingVO;

import java.util.List;

/**
 * Dịch vụ đề xuất tác phẩm theo hành vi, thể loại và giới hạn độ tuổi.
 */
public interface RecommendationService {

    List<Book> recommendBooks(Long userId, User userProfile, Integer preferredCatId, Long excludedBookId, int limit);

    List<BookSettingVO> recommendHomeBooks(Long userId, User userProfile, int limit);
}
