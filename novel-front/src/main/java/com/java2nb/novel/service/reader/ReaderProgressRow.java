package com.java2nb.novel.service.reader;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ReaderProgressRow {
    private Long userId;
    private Long bookId;
    private Long bookIndexId;
    private Integer paragraphIndex;
    private Integer characterOffset;
    private BigDecimal progressPercent;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
