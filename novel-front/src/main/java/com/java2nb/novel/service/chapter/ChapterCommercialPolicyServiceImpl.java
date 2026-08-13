package com.java2nb.novel.service.chapter;

import com.java2nb.novel.core.config.BookPriceProperties;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.mapper.ChapterCommercialPolicyMapper;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import lombok.RequiredArgsConstructor;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.update;

@Service
@RequiredArgsConstructor
public class ChapterCommercialPolicyServiceImpl implements ChapterCommercialPolicyService {
    private static final int MAX_CUSTOM_PRICE = 1_000_000;

    private final ChapterCommercialPolicyMapper policyMapper;
    private final BookIndexMapper bookIndexMapper;
    private final AuthorBookCollaborationService collaborationService;
    private final BookPriceProperties bookPriceProperties;

    @Override
    @Transactional(readOnly = true)
    public ChapterAccessDecision evaluate(BookIndex chapter, boolean purchased, Date now) {
        if (chapter == null || chapter.getId() == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        Date effectiveNow = now == null ? new Date() : now;
        ChapterCommercialPolicy policy = policyMapper.selectByBookIndexId(chapter.getId());
        boolean unlocked = policy != null && policy.getUnlockAt() != null
            && !effectiveNow.before(policy.getUnlockAt());
        boolean permanentlyFree = chapter.getIsVip() == null || chapter.getIsVip() != 1 || unlocked;
        boolean temporaryFree = !permanentlyFree && policy != null
            && policy.getFreeFrom() != null && policy.getFreeUntil() != null
            && !effectiveNow.before(policy.getFreeFrom()) && effectiveNow.before(policy.getFreeUntil());
        return new ChapterAccessDecision(!purchased && !permanentlyFree && !temporaryFree,
            permanentlyFree, temporaryFree, permanentlyFree);
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterCommercialPolicyView getForAuthor(long authorId, long bookIndexId) {
        BookIndex chapter = bookIndexMapper.selectByPrimaryKey(bookIndexId).orElse(null);
        if (chapter == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        collaborationService.requirePermission(authorId, chapter.getBookId(), BookPermission.MANAGE_CHAPTERS);
        ChapterCommercialPolicy policy = policyMapper.selectByBookIndexId(bookIndexId);
        return new ChapterCommercialPolicyView(bookIndexId,
            chapter.getIsVip() == null ? 0 : chapter.getIsVip(),
            chapter.getBookPrice() == null ? 0 : chapter.getBookPrice(),
            policy == null ? null : policy.getCustomPrice(),
            policy == null ? null : policy.getUnlockAt(),
            policy == null ? null : policy.getFreeFrom(),
            policy == null ? null : policy.getFreeUntil());
    }

    @Override
    public int calculateAutomaticPrice(int wordCount) {
        if (bookPriceProperties.getWordCount() == null || bookPriceProperties.getWordCount().signum() <= 0
            || bookPriceProperties.getValue() == null || bookPriceProperties.getValue().signum() <= 0) {
            throw new IllegalStateException("Cấu hình giá chương không hợp lệ");
        }
        int calculated = BigDecimal.valueOf(Math.max(wordCount, 0)).multiply(bookPriceProperties.getValue())
            .divide(bookPriceProperties.getWordCount(), 0, RoundingMode.DOWN).intValueExact();
        return Math.max(1, calculated);
    }

    @Override
    public int resolveEffectivePrice(byte isVip, Integer customPrice, int automaticPrice) {
        validate(isVip, customPrice, null, null, null);
        if (isVip == 0) {
            return 0;
        }
        return customPrice == null ? Math.max(1, automaticPrice) : customPrice;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findCustomPrice(long bookIndexId) {
        ChapterCommercialPolicy policy = policyMapper.selectByBookIndexId(bookIndexId);
        return policy == null ? null : policy.getCustomPrice();
    }

    @Override
    public void validate(byte isVip, Integer customPrice, Date unlockAt, Date freeFrom, Date freeUntil) {
        if (isVip != 0 && isVip != 1) {
            throw new IllegalArgumentException("Trạng thái chương thu phí không hợp lệ");
        }
        if (customPrice != null && (customPrice <= 0 || customPrice > MAX_CUSTOM_PRICE)) {
            throw new IllegalArgumentException("Giá tùy chỉnh phải từ 1 đến 1.000.000 Xu");
        }
        if ((freeFrom == null) != (freeUntil == null)) {
            throw new IllegalArgumentException("Cửa sổ miễn phí phải có đủ thời gian bắt đầu và kết thúc");
        }
        if (freeFrom != null && !freeFrom.before(freeUntil)) {
            throw new IllegalArgumentException("Thời gian kết thúc miễn phí phải sau thời gian bắt đầu");
        }
        if (isVip == 0 && (customPrice != null || unlockAt != null || freeFrom != null)) {
            throw new IllegalArgumentException("Chính sách thương mại chỉ áp dụng cho chương thu phí");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyPublishedPolicy(long authorId, long bookIndexId, byte isVip, Integer customPrice,
                                     Date unlockAt, Date freeFrom, Date freeUntil, int automaticPrice) {
        validate(isVip, customPrice, unlockAt, freeFrom, freeUntil);
        BookIndex locked = bookIndexMapper.lockById(bookIndexId);
        if (locked == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        collaborationService.requirePermission(authorId, locked.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        int effectivePrice = resolveEffectivePrice(isVip, customPrice, automaticPrice);
        int updated = bookIndexMapper.update(update(BookIndexDynamicSqlSupport.bookIndex)
            .set(BookIndexDynamicSqlSupport.isVip).equalTo(isVip)
            .set(BookIndexDynamicSqlSupport.bookPrice).equalTo(effectivePrice)
            .set(BookIndexDynamicSqlSupport.updateTime).equalTo(new Date())
            .where(BookIndexDynamicSqlSupport.id, isEqualTo(bookIndexId))
            .build().render(RenderingStrategies.MYBATIS3));
        if (updated != 1) {
            throw new IllegalStateException("Không thể cập nhật chính sách thương mại của chương");
        }

        if (isVip == 0 || customPrice == null && unlockAt == null && freeFrom == null) {
            policyMapper.deleteByBookIndexId(bookIndexId);
            return;
        }
        ChapterCommercialPolicy policy = new ChapterCommercialPolicy();
        policy.setBookIndexId(bookIndexId);
        policy.setBookId(locked.getBookId());
        policy.setCustomPrice(customPrice);
        policy.setUnlockAt(unlockAt);
        policy.setFreeFrom(freeFrom);
        policy.setFreeUntil(freeUntil);
        if (policyMapper.upsert(policy) != 1 && policyMapper.selectByBookIndexId(bookIndexId) == null) {
            throw new IllegalStateException("Không thể lưu chính sách thương mại của chương");
        }
    }
}
