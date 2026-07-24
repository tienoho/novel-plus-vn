package com.java2nb.novel.service.finance;

import java.util.Date;

public record AuthorKycStatus(String status, String identityType, String identityNumberMasked,
                              String bankCode, String bankAccountMasked, int submissionVersion,
                              Date submittedAt, String rejectionReason) {
}
