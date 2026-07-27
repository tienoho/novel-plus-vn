package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Challenge 5: Age Rating DOB Check Boundaries & Leap Year Calculation Flaw")
class AgeRatingUtilChallengeTest {

    @Test
    @DisplayName("Rating 0 (All ages) allows any reader regardless of DOB or null DOB")
    void testRatingZeroAllowsAll() {
        assertThat(AgeRatingUtil.isAgeAllowed(null, (byte) 0)).isTrue();
        assertThat(AgeRatingUtil.isAgeAllowed(new Date(), (byte) 0)).isTrue();
    }

    @Test
    @DisplayName("Missing DOB (null) rejects access when age rating > 0")
    void testNullDobRejectsRestrictedContent() {
        assertThat(AgeRatingUtil.isAgeAllowed(null, (byte) 13)).isFalse();
        assertThat(AgeRatingUtil.isAgeAllowed(null, (byte) 18)).isFalse();
    }

    @Test
    @DisplayName("Demonstrate flaw: Leap year birthday comparison using DAY_OF_YEAR calculates wrong age on birthday")
    void testLeapYearBirthDateDayOfYearBug() {
        // User born on Dec 31, 2004 (2004 is a Leap Year, 366 days)
        Calendar birth = Calendar.getInstance();
        birth.set(2004, Calendar.DECEMBER, 31, 0, 0, 0);
        birth.set(Calendar.MILLISECOND, 0);
        Date userDob = birth.getTime();

        // On Dec 31, 2022 (2022 is Non-Leap Year, 365 days), the user is EXACTLY 18 years old.
        // However, Calendar.DAY_OF_YEAR for Dec 31 in 2022 is 365.
        // Calendar.DAY_OF_YEAR for Dec 31 in 2004 was 366.
        // AgeRatingUtil compares: now.get(DAY_OF_YEAR) < birth.get(DAY_OF_YEAR) => 365 < 366 => TRUE!
        // It decrements age (18 -> 17), failing the 18+ check on their 18th birthday!

        boolean allowed = AgeRatingUtil.isAgeAllowed(userDob, (byte) 18);
        System.out.println("User DOB: 2004-12-31 (Leap year)");
        System.out.println("Evaluation Date: 2022-12-31 (18th birthday in non-leap year)");
        System.out.println("isAgeAllowed(18) result: " + allowed);

        // Verify AgeRatingUtil correctly returns true for a reader who is 18 years old today
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Standard adult reader (e.g. born 20 years ago) is allowed 18+ content")
    void testAdultAllowed() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -20);
        Date dob = cal.getTime();

        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 13)).isTrue();
        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 16)).isTrue();
        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 18)).isTrue();
    }

    @Test
    @DisplayName("Underage reader (e.g. 10 years old) is blocked from 13+, 16+, and 18+ content")
    void testUnderageBlocked() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -10);
        Date dob = cal.getTime();

        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 13)).isFalse();
        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 16)).isFalse();
        assertThat(AgeRatingUtil.isAgeAllowed(dob, (byte) 18)).isFalse();
    }
}
