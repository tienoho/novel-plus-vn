package com.java2nb.novel.service.wallet;

import lombok.Data;

@Data
public class WalletEntryRow {

    private Long walletAccountId;
    private String ownerType;
    private Long ownerId;
    private String accountType;
    private Long amount;
}
