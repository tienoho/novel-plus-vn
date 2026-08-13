package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class RealmCatalogRow {
    private String realmCode;
    private String nameKey;
    private Integer minLevel;
    private Boolean active;
}
