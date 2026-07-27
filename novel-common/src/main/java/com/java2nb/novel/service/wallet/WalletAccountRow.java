package com.java2nb.novel.service.wallet;

import lombok.Data;

@Data
public class WalletAccountRow {

    private Long id;
    private String ownerType;
    private Long ownerId;
    private String accountType;
    private Long availableBalance;
    private Long version;
}
