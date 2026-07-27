package com.java2nb.novel.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class BookOwnershipProof implements Serializable {
    private Long id;
    private Long bookId;
    private Long authorId;
    private Byte proofType; // 1: Certificate, 2: Contract, 3: Original manuscript, 4: Other
    private String fileUrl;
    private String fileHash;
    private String note;
    private Byte verificationStatus; // 0: Pending, 1: Valid, 2: Invalid
    private Long verifierId;
    private Date verifiedAt;
    private Date createTime;

    public String getProofContent() { return note; }
    public void setProofContent(String proofContent) { this.note = proofContent; }
    public String getProofUrl() { return fileUrl; }
    public void setProofUrl(String proofUrl) { this.fileUrl = proofUrl; }
    public String getProofHash() { return fileHash; }
    public void setProofHash(String proofHash) { this.fileHash = proofHash; }
    public void setUpdateTime(Date updateTime) { }
}
