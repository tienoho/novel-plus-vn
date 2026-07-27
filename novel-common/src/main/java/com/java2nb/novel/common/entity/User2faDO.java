package com.java2nb.novel.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Domain entity for table user_2fa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User2faDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String secretKeyCiphertext;
    private Boolean isEnabled;
    private String backupCodesJson;
    private Date enabledAt;
    private Date lastVerifiedAt;
    private Date createTime;
    private Date updateTime;
}
