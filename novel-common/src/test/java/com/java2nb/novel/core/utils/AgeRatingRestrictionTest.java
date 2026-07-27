package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class AgeRatingRestrictionTest {

    @Test
    public void testAllAgesAllowed() {
        assertTrue(AgeRatingUtil.isAgeAllowed(null, (byte) 0));
        assertTrue(AgeRatingUtil.isAgeAllowed(new Date(), (byte) 0));
    }

    @Test
    public void testNullUserDobFailsRestrictedContent() {
        assertFalse(AgeRatingUtil.isAgeAllowed(null, (byte) 13));
        assertFalse(AgeRatingUtil.isAgeAllowed(null, (byte) 18));
    }

    @Test
    public void testUnderagedUserDenied() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -15);
        Date dob = cal.getTime();

        assertTrue(AgeRatingUtil.isAgeAllowed(dob, (byte) 13));
        assertFalse(AgeRatingUtil.isAgeAllowed(dob, (byte) 16));
        assertFalse(AgeRatingUtil.isAgeAllowed(dob, (byte) 18));
    }

    @Test
    public void testAdultUserAllowedAll() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -20);
        Date dob = cal.getTime();

        assertTrue(AgeRatingUtil.isAgeAllowed(dob, (byte) 13));
        assertTrue(AgeRatingUtil.isAgeAllowed(dob, (byte) 16));
        assertTrue(AgeRatingUtil.isAgeAllowed(dob, (byte) 18));
    }

    @Test
    public void testRestrictedContentRequiresVerifiedAge() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -20);
        Date dob = cal.getTime();

        assertFalse(AgeRatingUtil.isAgeAllowed(dob, null, (byte) 18));
        assertFalse(AgeRatingUtil.isAgeAllowed(dob, (byte) 0, (byte) 18));
        assertTrue(AgeRatingUtil.isAgeAllowed(dob, (byte) 1, (byte) 18));
        assertTrue(AgeRatingUtil.isAgeAllowed(null, (byte) 0, (byte) 0));
    }

    @Test
    public void testPublicBookPolicyFailsClosedForModerationAndAge() {
        Book approved = approvedBook();
        assertNull(AgeRatingUtil.publicBookDenialReason(approved, null));

        approved.setAuditStatus((byte) 0);
        assertEquals(ResponseStatus.BOOK_NOT_AVAILABLE,
            AgeRatingUtil.publicBookDenialReason(approved, null));

        approved.setAuditStatus((byte) 3);
        assertEquals(ResponseStatus.COPYRIGHT_TAKEDOWN,
            AgeRatingUtil.publicBookDenialReason(approved, null));

        approved = approvedBook();
        approved.setCoverAuditStatus((byte) 0);
        assertEquals(ResponseStatus.BOOK_NOT_AVAILABLE,
            AgeRatingUtil.publicBookDenialReason(approved, null));

        approved = approvedBook();
        approved.setAgeRating((byte) 18);
        assertEquals(ResponseStatus.AGE_RESTRICTED,
            AgeRatingUtil.publicBookDenialReason(approved, null));

        User adult = new User();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -20);
        adult.setDateOfBirth(calendar.getTime());
        adult.setIsAgeVerified((byte) 1);
        assertNull(AgeRatingUtil.publicBookDenialReason(approved, adult));
    }

    @Test
    public void testPublicChapterPolicyRequiresApprovalAndMatchingBook() {
        BookIndex chapter = new BookIndex();
        chapter.setBookId(10L);
        chapter.setAuditStatus((byte) 1);
        assertNull(AgeRatingUtil.publicChapterDenialReason(chapter, 10L));
        assertEquals(ResponseStatus.BOOK_NOT_AVAILABLE,
            AgeRatingUtil.publicChapterDenialReason(chapter, 11L));

        chapter.setAuditStatus((byte) 0);
        assertEquals(ResponseStatus.BOOK_NOT_AVAILABLE,
            AgeRatingUtil.publicChapterDenialReason(chapter, 10L));
        chapter.setAuditStatus((byte) 3);
        assertEquals(ResponseStatus.COPYRIGHT_TAKEDOWN,
            AgeRatingUtil.publicChapterDenialReason(chapter, 10L));
    }

    private Book approvedBook() {
        Book book = new Book();
        book.setStatus((byte) 1);
        book.setAuditStatus((byte) 1);
        book.setCoverAuditStatus((byte) 1);
        book.setAgeRating((byte) 0);
        return book;
    }
}
