package com.java2nb.novel.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class SensitiveWord implements Serializable {
    private Long id;
    private String word;
    private String category; // POLITICS, PORN, VIOLENCE, ADVERTISING, GENERAL
    private String replacement;
    private Byte status; // 1: Active, 0: Disabled
    private Date createTime;
}
