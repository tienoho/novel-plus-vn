package com.java2nb.novel.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class AuthorKycReviewDO {

    private Long id;
    private Long authorId;
    private Long userId;
    private String identityType;
    private String identityNumberLast4;
    private String bankCode;
    private String bankAccountLast4;
    private String status;
    private Integer submissionVersion;
    private Date submittedAt;
    private Date reviewedAt;
    private Long reviewedBy;
    private String rejectionReason;
    @JsonIgnore private String legalNameCiphertext;
    @JsonIgnore private String dateOfBirthCiphertext;
    @JsonIgnore private String identityNumberCiphertext;
    @JsonIgnore private String taxCodeCiphertext;
    @JsonIgnore private String bankAccountCiphertext;
    @JsonIgnore private String bankAccountNameCiphertext;
    private String legalName;
    private String dateOfBirth;
    private String identityNumber;
    private String taxCode;
    private String bankAccount;
    private String bankAccountName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String identityType) { this.identityType = identityType; }
    public String getIdentityNumberLast4() { return identityNumberLast4; }
    public void setIdentityNumberLast4(String value) { this.identityNumberLast4 = value; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getBankAccountLast4() { return bankAccountLast4; }
    public void setBankAccountLast4(String value) { this.bankAccountLast4 = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSubmissionVersion() { return submissionVersion; }
    public void setSubmissionVersion(Integer value) { this.submissionVersion = value; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { this.rejectionReason = value; }
    public String getLegalNameCiphertext() { return legalNameCiphertext; }
    public void setLegalNameCiphertext(String value) { this.legalNameCiphertext = value; }
    public String getDateOfBirthCiphertext() { return dateOfBirthCiphertext; }
    public void setDateOfBirthCiphertext(String value) { this.dateOfBirthCiphertext = value; }
    public String getIdentityNumberCiphertext() { return identityNumberCiphertext; }
    public void setIdentityNumberCiphertext(String value) { this.identityNumberCiphertext = value; }
    public String getTaxCodeCiphertext() { return taxCodeCiphertext; }
    public void setTaxCodeCiphertext(String value) { this.taxCodeCiphertext = value; }
    public String getBankAccountCiphertext() { return bankAccountCiphertext; }
    public void setBankAccountCiphertext(String value) { this.bankAccountCiphertext = value; }
    public String getBankAccountNameCiphertext() { return bankAccountNameCiphertext; }
    public void setBankAccountNameCiphertext(String value) { this.bankAccountNameCiphertext = value; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getIdentityNumber() { return identityNumber; }
    public void setIdentityNumber(String value) { this.identityNumber = value; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String value) { this.bankAccountName = value; }
}
