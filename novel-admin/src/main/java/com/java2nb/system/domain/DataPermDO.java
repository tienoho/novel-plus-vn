package com.java2nb.system.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;


/**
 * Quản lý quyền dữ liệu
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-11-25 11:40:03
 */
public class DataPermDO implements Serializable {

    private static final long serialVersionUID = 1L;


    //
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // Tên quyền
    private String name;
    // Tên bảng dữ liệu
    private String tableName;
    // Mô-đun sở hữu
    private String moduleName;
    // Tên thuộc tính kiểm soát quyền người dùng
    private String crlAttrName;
    // Tên cột kiểm soát quyền trong bảng dữ liệu
    private String crlColumnName;
    // Mã quyền; tiền tố all_ xem mọi dữ liệu, sup_ xem dữ liệu cấp dưới, own_ xem dữ liệu cùng cấp
    private String permCode;
    // Thứ tự
    private Integer orderNum;
    // Thời gian tạo
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date gmtCreate;
    // Thời gian sửa
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date gmtModified;

    /**
     * Đặt:
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy:
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: tên quyền
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Lấy: tên quyền
     */
    public String getName() {
        return name;
    }

    /**
     * Đặt: tên bảng dữ liệu
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Lấy: tên bảng dữ liệu
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Đặt: mô-đun sở hữu
     */
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Lấy: mô-đun sở hữu
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Đặt: tên thuộc tính kiểm soát quyền người dùng
     */
    public void setCrlAttrName(String crlAttrName) {
        this.crlAttrName = crlAttrName;
    }

    /**
     * Lấy: tên thuộc tính kiểm soát quyền người dùng
     */
    public String getCrlAttrName() {
        return crlAttrName;
    }

    /**
     * Đặt: tên cột kiểm soát quyền trong bảng dữ liệu
     */
    public void setCrlColumnName(String crlColumnName) {
        this.crlColumnName = crlColumnName;
    }

    /**
     * Lấy: tên cột kiểm soát quyền trong bảng dữ liệu
     */
    public String getCrlColumnName() {
        return crlColumnName;
    }

    /**
     * Đặt: mã quyền; tiền tố all_ xem mọi dữ liệu, sup_ xem dữ liệu cấp dưới, own_ xem dữ liệu cùng cấp
     */
    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }

    /**
     * Lấy: mã quyền; tiền tố all_ xem mọi dữ liệu, sup_ xem dữ liệu cấp dưới, own_ xem dữ liệu cùng cấp
     */
    public String getPermCode() {
        return permCode;
    }

    /**
     * Đặt: thứ tự
     */
    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    /**
     * Lấy: thứ tự
     */
    public Integer getOrderNum() {
        return orderNum;
    }

    /**
     * Đặt: thời gian tạo
     */
    public void setGmtCreate(Date gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    /**
     * Lấy: thời gian tạo
     */
    public Date getGmtCreate() {
        return gmtCreate;
    }

    /**
     * Đặt: thời gian sửa
     */
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    /**
     * Lấy: thời gian sửa
     */
    public Date getGmtModified() {
        return gmtModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataPermDO that = (DataPermDO) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name)
            && Objects.equals(tableName, that.tableName) && Objects.equals(moduleName, that.moduleName)
            && Objects.equals(crlAttrName, that.crlAttrName) && Objects.equals(crlColumnName,
            that.crlColumnName) && Objects.equals(permCode, that.permCode) && Objects.equals(orderNum,
            that.orderNum) && Objects.equals(gmtCreate, that.gmtCreate) && Objects.equals(gmtModified,
            that.gmtModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, tableName, moduleName, crlAttrName, crlColumnName, permCode, orderNum, gmtCreate,
            gmtModified);
    }
}
