package com.java2nb.common.domain;

import java.io.Serializable;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import lombok.Data;


/**
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-11-22 18:03:46
 */
public class GenColumnsDO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Khóa chính
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // Tên bảng
    private String tableName;
    // Tên cột
    private String columnName;
    //Kiểu cột
    private String columnType;
    // Ánh xạ kiểu Java
    private String javaType;
    //Chú thích cột
    private String columnComment;
    // Thứ tự cột (tăng dần)
    private Integer columnSort;
    // Nhãn cột
    private String columnLabel;
    // Kiểu hiển thị: 1 ô văn bản, 2 danh sách thả xuống, 3 số, 4 ngày, 5 vùng văn bản, 6 văn bản phong phú, 7 tải một ảnh, 8 tải nhiều ảnh, 9 tải một tệp, 10 tải nhiều tệp, 11 trường ẩn, 12 không hiển thị
    private Integer pageType;
    // Bắt buộc
    private Integer isRequired;
    // Dùng khi trang hiển thị dạng danh sách thả xuống; loại từ điển được lấy từ bảng từ điển
    private String dictType;

    // Tên thuộc tính (chữ cái đầu viết hoa), ví dụ: user_name => UserName
    private String attrName;
    // Tên thuộc tính (chữ cái đầu viết thường), ví dụ: user_name => userName
    private String attrname;

    private String extra;

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnType() {
        return columnType;
    }

    public void setColumnType(String columnType) {
        this.columnType = columnType;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public Integer getColumnSort() {
        return columnSort;
    }

    public void setColumnSort(Integer columnSort) {
        this.columnSort = columnSort;
    }

    public String getColumnLabel() {
        return columnLabel;
    }

    public void setColumnLabel(String columnLabel) {
        this.columnLabel = columnLabel;
    }

    public Integer getPageType() {
        return pageType;
    }

    public void setPageType(Integer pageType) {
        this.pageType = pageType;
    }

    public Integer getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Integer isRequired) {
        this.isRequired = isRequired;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getAttrName() {
        return attrName;
    }

    public void setAttrName(String attrName) {
        this.attrName = attrName;
    }

    public String getAttrname() {
        return attrname;
    }

    public void setAttrname(String attrname) {
        this.attrname = attrname;
    }
}
