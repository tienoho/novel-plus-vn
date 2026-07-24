package com.java2nb.novel.service.finance;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class KycSubmissionRequest {

    private String legalName;
    private LocalDate dateOfBirth;
    private String identityType;
    private String identityNumber;
    private String taxCode;
    private String bankCode;
    private String bankAccount;
    private String bankAccountName;
    private String consentVersion;
}
