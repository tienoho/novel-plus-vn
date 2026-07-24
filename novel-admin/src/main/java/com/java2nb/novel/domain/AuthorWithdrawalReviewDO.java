package com.java2nb.novel.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class AuthorWithdrawalReviewDO {

    private Long id;
    private String withdrawalNo;
    private Long authorId;
    private Long userId;
    private Long requestedXu;
    private Long vndPerXu;
    private Long grossAmountVnd;
    private Long withheldTaxVnd;
    private Long netAmountVnd;
    private String bankCode;
    private String bankAccountLast4;
    @JsonIgnore private String bankAccountCiphertext;
    @JsonIgnore private String bankAccountNameCiphertext;
    private String bankAccount;
    private String bankAccountName;
    private String status;
    private String payoutProvider;
    private String providerReference;
    private Date requestedAt;
    private Date reviewedAt;
    private Date paidAt;
    private String rejectionReason;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWithdrawalNo() { return withdrawalNo; }
    public void setWithdrawalNo(String value) { this.withdrawalNo = value; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long value) { this.authorId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getRequestedXu() { return requestedXu; }
    public void setRequestedXu(Long value) { this.requestedXu = value; }
    public Long getVndPerXu() { return vndPerXu; }
    public void setVndPerXu(Long value) { this.vndPerXu = value; }
    public Long getGrossAmountVnd() { return grossAmountVnd; }
    public void setGrossAmountVnd(Long value) { this.grossAmountVnd = value; }
    public Long getWithheldTaxVnd() { return withheldTaxVnd; }
    public void setWithheldTaxVnd(Long value) { this.withheldTaxVnd = value; }
    public Long getNetAmountVnd() { return netAmountVnd; }
    public void setNetAmountVnd(Long value) { this.netAmountVnd = value; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String value) { this.bankCode = value; }
    public String getBankAccountLast4() { return bankAccountLast4; }
    public void setBankAccountLast4(String value) { this.bankAccountLast4 = value; }
    public String getBankAccountCiphertext() { return bankAccountCiphertext; }
    public void setBankAccountCiphertext(String value) { this.bankAccountCiphertext = value; }
    public String getBankAccountNameCiphertext() { return bankAccountNameCiphertext; }
    public void setBankAccountNameCiphertext(String value) { this.bankAccountNameCiphertext = value; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String value) { this.bankAccount = value; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String value) { this.bankAccountName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getPayoutProvider() { return payoutProvider; }
    public void setPayoutProvider(String value) { this.payoutProvider = value; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String value) { this.providerReference = value; }
    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date value) { this.requestedAt = value; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date value) { this.reviewedAt = value; }
    public Date getPaidAt() { return paidAt; }
    public void setPaidAt(Date value) { this.paidAt = value; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { this.rejectionReason = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
}
