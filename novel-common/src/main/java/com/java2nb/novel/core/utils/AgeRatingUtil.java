package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.User;

import java.util.Calendar;
import java.util.Date;

/**
 * Tiện ích kiểm tra giới hạn độ tuổi người đọc
 */
public class AgeRatingUtil {

    private AgeRatingUtil() {
    }

    /**
     * Kiểm tra người dùng có đủ tuổi để đọc tác phẩm hay không
     * @param userDob Ngày sinh người dùng
     * @param ageRating Giới hạn độ tuổi (0: Tất cả, 13: 13+, 16: 16+, 18: 18+)
     * @return true nếu hợp lệ, false nếu chưa đủ tuổi hoặc thiếu thông tin ngày sinh
     */
    public static boolean isAgeAllowed(Date userDob, Byte ageRating) {
        if (ageRating == null || ageRating <= 0) {
            return true;
        }
        if (userDob == null) {
            return false;
        }
        Calendar birth = Calendar.getInstance();
        birth.setTime(userDob);
        Calendar now = Calendar.getInstance();
        int age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        int nowMonth = now.get(Calendar.MONTH);
        int birthMonth = birth.get(Calendar.MONTH);
        if (nowMonth < birthMonth || (nowMonth == birthMonth && now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))) {
            age--;
        }
        return age >= ageRating;
    }

    /**
     * Kiểm tra quyền truy cập nội dung giới hạn tuổi bằng hồ sơ đã xác minh.
     * Nội dung dành cho mọi lứa tuổi không yêu cầu xác minh.
     *
     * @param userDob ngày sinh người dùng
     * @param isAgeVerified trạng thái xác minh tuổi, 1 là đã xác minh
     * @param ageRating giới hạn độ tuổi của tác phẩm
     * @return true nếu người dùng được phép truy cập
     */
    public static boolean isAgeAllowed(Date userDob, Byte isAgeVerified, Byte ageRating) {
        if (ageRating == null || ageRating <= 0) {
            return true;
        }
        return isAgeVerified != null && isAgeVerified == 1 && isAgeAllowed(userDob, ageRating);
    }

    /**
     * Trả về lý do từ chối truy cập công khai hoặc {@code null} khi được phép.
     */
    public static ResponseStatus publicBookDenialReason(Book book, User user) {
        if (book == null) {
            return ResponseStatus.BOOK_NOT_AVAILABLE;
        }
        if (book.getAuditStatus() != null && book.getAuditStatus() == 3) {
            return ResponseStatus.COPYRIGHT_TAKEDOWN;
        }
        if (book.getStatus() == null || book.getStatus() != 1
            || book.getAuditStatus() == null || book.getAuditStatus() != 1
            || book.getCoverAuditStatus() == null || book.getCoverAuditStatus() != 1) {
            return ResponseStatus.BOOK_NOT_AVAILABLE;
        }
        if (!isAgeAllowed(user == null ? null : user.getDateOfBirth(),
            user == null ? null : user.getIsAgeVerified(), book.getAgeRating())) {
            return ResponseStatus.AGE_RESTRICTED;
        }
        return null;
    }

    /**
     * Trả về lý do từ chối chương công khai, đồng thời chống dùng ID chương của tác phẩm khác.
     */
    public static ResponseStatus publicChapterDenialReason(BookIndex chapter, Long expectedBookId) {
        if (chapter == null || expectedBookId == null || !expectedBookId.equals(chapter.getBookId())) {
            return ResponseStatus.BOOK_NOT_AVAILABLE;
        }
        if (chapter.getAuditStatus() != null && chapter.getAuditStatus() == 3) {
            return ResponseStatus.COPYRIGHT_TAKEDOWN;
        }
        if (chapter.getAuditStatus() == null || chapter.getAuditStatus() != 1) {
            return ResponseStatus.BOOK_NOT_AVAILABLE;
        }
        return null;
    }
}
