package com.java2nb.common.utils;

import java.util.Map;
import java.util.Set;

/**
 * Khóa tham số sắp xếp do trình duyệt gửi về thành các SQL identifier đã biết.
 */
public final class SafeSort {

    private static final Set<String> ALLOWED_COLUMNS = Set.of("gmt_create", "order_num");

    private SafeSort() {
    }

    public static void sanitize(Map<String, Object> params) {
        Object requestedColumn = params.get("sort");
        if (requestedColumn == null || !ALLOWED_COLUMNS.contains(requestedColumn.toString())) {
            params.remove("sort");
            params.remove("order");
            return;
        }

        String direction = String.valueOf(params.getOrDefault("order", "desc"));
        params.put("order", "asc".equalsIgnoreCase(direction) ? "asc" : "desc");
    }
}
