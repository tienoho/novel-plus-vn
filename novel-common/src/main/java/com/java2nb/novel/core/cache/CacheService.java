package com.java2nb.novel.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

/**
 * @author 11797
 */
public interface CacheService {

    /**
     * Lấy dữ liệu String trong bộ nhớ đệm theo khóa
     */
    String get(String key);

    /**
     * Đặt bộ nhớ đệm kiểu String
     */
    void set(String key, String value);

    /**
     * Đặt bộ nhớ đệm String có thời hạn, đơn vị giây
     */
    void set(String key, String value, long timeout);

    /**
     * Lấy dữ liệu Object trong bộ nhớ đệm theo khóa
     */
    <T> T getObject(String key, Class<T> clazz);

    <T> List<T> getList(String key, Class<T> clazz);

    /**
     * Đặt bộ nhớ đệm kiểu Object
     */
    void setObject(String key, Object value);

    /**
     * Đặt bộ nhớ đệm Object có thời hạn, đơn vị giây
     */
    void setObject(String key, Object value, long timeout);

    /**
     * Xóa dữ liệu bộ nhớ đệm theo khóa
     */
    void del(String key);

    /**
     * Xóa khóa chỉ khi giá trị hiện tại khớp với giá trị mong đợi.
     *
     * <p>Thao tác phải được implementation thực hiện nguyên tử.</p>
     */
    boolean compareAndDelete(String key, String expectedValue);


    /**
     * Kiểm tra khóa có tồn tại hay không
     */
    boolean contains(String key);

    /**
     * Đặt thời gian hết hạn cho khóa
     */
    void expire(String key, long timeout);


}
