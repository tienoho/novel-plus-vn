package com.java2nb.novel.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class CopyrightReport implements Serializable {
    private Long id;
    private String reportNo;
    private Long reporterId;
    private String reporterName;
    private String reporterEmail;
    private String reporterPhone;
    private Byte reporterType; // 1: Author, 2: Owner, 3: Representative
    private Byte targetType;   // 1: Novel, 2: Chapter, 3: Cover
    private Long targetId;
    private String targetName;
    private String originalWorkName;
    private String originalWorkUrl;
    private Byte violationType; // 1: Unauthorized copy, 2: Plagiarism, 3: Trademark
    private String description;
    private String evidenceUrls; // JSON string
    private Byte status; // 0: Pending, 1: Under Review, 2: Takedown, 3: Rejected, 4: Appealed, 5: Resolved
    private String reviewResult;
    private Long reviewerId;
    private Date reviewedAt;
    private Date createTime;
    private Date updateTime;

    public Long getBookId() { return targetId; }
    public void setBookId(Long bookId) { this.targetId = bookId; }
    public void setReporterUserId(Long reporterUserId) { this.reporterId = reporterUserId; }
}
