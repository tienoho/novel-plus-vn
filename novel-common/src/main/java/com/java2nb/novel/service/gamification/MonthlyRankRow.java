package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

/** Một dòng xếp hạng lấy từ nguồn vote hoặc từ snapshot đã chốt. */
@Data
public class MonthlyRankRow {
    private Long snapshotId;
    private Integer rankNo;
    private Long bookId;
    private Long authorId;
    private String bookName;
    private String authorName;
    private String picUrl;
    private Long totalTickets;
    private Long distinctVoterCount;
    private Date lastVoteAt;
}
