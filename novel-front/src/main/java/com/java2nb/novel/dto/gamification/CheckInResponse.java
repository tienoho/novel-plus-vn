package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.CheckInOutcome;

import java.util.Date;

public record CheckInResponse(int streak, int longestStreak, long expGained, long ticketGained,
                              long totalExp, int level, long ticketBalance, Date nextCheckIn,
                              boolean alreadyCheckedIn) {
    public static CheckInResponse from(CheckInOutcome outcome) {
        return new CheckInResponse(outcome.checkIn().profile().getCheckinStreak(),
            outcome.checkIn().profile().getLongestStreak(), outcome.reward().claim().getExpAmount(),
            outcome.reward().claim().getTicketAmount(),
            outcome.reward().profile().profile().getTotalExp(),
            outcome.reward().profile().profile().getLevel(), outcome.reward().ticketBalance(),
            outcome.nextCheckIn(), outcome.checkIn().alreadyCheckedIn());
    }
}
