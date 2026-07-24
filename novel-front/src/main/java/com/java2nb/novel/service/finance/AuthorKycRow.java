package com.java2nb.novel.service.finance;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class AuthorKycRow {

    private Long id;
    private Long authorId;
    private Long userId;
    private String legalNameCiphertext;
    private String dateOfBirthCiphertext;
    private String identityType;
    private String identityNumberCiphertext;
    private String identityNumberHash;
    private String identityNumberLast4;
    private String taxCodeCiphertext;
    private String bankCode;
    private String bankAccountCiphertext;
    private String bankAccountHash;
    private String bankAccountLast4;
    private String bankAccountNameCiphertext;
    private String consentVersion;
    private Date consentedAt;
    private String status;
    private Integer submissionVersion;
    private Date submittedAt;
    private String rejectionReason;
}
