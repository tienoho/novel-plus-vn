package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class RealmCatalogRow {
    private String policyVersion;
    private String realmCode;
    private String nameKey;
    private Integer minLevel;
    private Integer sortNo;
    private Boolean active;
}
