package com.java2nb.common.utils;

import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * Tiện ích danh sách cho phép cho trường và thứ tự sắp xếp.
 */
@UtilityClass
public class SortWhitelistUtil {

    // Trường trong danh sách cho phép
    private static final Set<String> ALLOWED_COLUMNS = Set.of("id", "name", "order_num","index_num");

    // Kiểu sắp xếp trong danh sách cho phép
    private static final Set<String> ALLOWED_ORDERS = Set.of("asc", "desc");

    /**
     * Lọc theo danh sách cho phép và chuẩn hóa trường sắp xếp.
     *
     * @param column tên trường gốc
     * @return tên trường an toàn; trả null nếu không hợp lệ
     */
    public static String sanitizeColumn(String column) {
        if (column == null) return null;
        String lower = column.trim().toLowerCase();
        return ALLOWED_COLUMNS.contains(lower) ? lower : null;
    }

    /**
     * Lọc theo danh sách cho phép và chuẩn hóa kiểu sắp xếp.
     *
     * @param order kiểu sắp xếp gốc
     * @return kiểu sắp xếp an toàn ("asc" hoặc "desc"); trả null nếu không hợp lệ
     */
    public static String sanitizeOrder(String order) {
        if (order == null) return null;
        String lower = order.trim().toLowerCase();
        return ALLOWED_ORDERS.contains(lower) ? lower : null;
    }

}
