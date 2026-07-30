package com.java2nb.novel.service.gamification;

public record QuestRewardCommand(String questCode, long expAmount, long ticketAmount) {
    public QuestRewardCommand {
        questCode = questCode == null ? "" : questCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!questCode.matches("[A-Z0-9_]{3,48}")) {
            throw new IllegalArgumentException("Mã nhiệm vụ không hợp lệ");
        }
        if (expAmount < 0 || ticketAmount < 0 || (expAmount == 0 && ticketAmount == 0)) {
            throw new IllegalArgumentException("Phần thưởng nhiệm vụ không hợp lệ");
        }
    }
}
