package com.java2nb.novel.service.gamification;

import java.util.Date;

public record CheckInOutcome(CheckInResult checkIn, QuestClaimResult reward, Date nextCheckIn) {
}
