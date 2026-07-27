package com.java2nb.novel.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class CopyrightAppeal implements Serializable {
    private Long id;
    private Long reportId;
    private Long bookId;
    private Long authorId;
    private String appealReason;
    private String proofUrls; // JSON string
    private Byte status; // 0: Pending, 1: Accepted, 2: Rejected
    private String reviewRemark;
    private Long reviewerId;
    private Date reviewedAt;
    private Date createTime;

    public void setUpdateTime(Date updateTime) { }
}
