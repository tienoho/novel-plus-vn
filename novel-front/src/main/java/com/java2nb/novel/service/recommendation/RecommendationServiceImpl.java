package com.java2nb.novel.service.recommendation;

import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.mapper.FrontBookMapper;
import com.java2nb.novel.vo.BookSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final int MAX_LIMIT = 20;

    private final FrontBookMapper bookMapper;

    @Override
    public List<Book> recommendBooks(Long userId, User userProfile, Integer preferredCatId, Long excludedBookId,
        int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        byte maxAgeRating = resolveMaximumAgeRating(userProfile);
        return bookMapper.listRecommendations(userId, preferredCatId, maxAgeRating, excludedBookId,
            userId != null, safeLimit);
    }

    @Override
    public List<BookSettingVO> recommendHomeBooks(Long userId, User userProfile, int limit) {
        return recommendBooks(userId, userProfile, null, null, limit).stream().map(book -> {
            BookSettingVO item = new BookSettingVO();
            BeanUtils.copyProperties(book, item);
            item.setBookId(book.getId());
            item.setType((byte) 4);
            return item;
        }).toList();
    }

    static byte resolveMaximumAgeRating(User userProfile) {
        if (userProfile == null || userProfile.getIsAgeVerified() == null
            || userProfile.getIsAgeVerified() != 1) {
            return 0;
        }
        if (AgeRatingUtil.isAgeAllowed(userProfile.getDateOfBirth(), userProfile.getIsAgeVerified(), (byte) 18)) {
            return 18;
        }
        if (AgeRatingUtil.isAgeAllowed(userProfile.getDateOfBirth(), userProfile.getIsAgeVerified(), (byte) 16)) {
            return 16;
        }
        if (AgeRatingUtil.isAgeAllowed(userProfile.getDateOfBirth(), userProfile.getIsAgeVerified(), (byte) 13)) {
            return 13;
        }
        return 0;
    }
}
