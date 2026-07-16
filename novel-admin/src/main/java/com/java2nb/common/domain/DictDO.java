package com.java2nb.common.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;


/**
 * Bảng từ điển
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-29 18:28:07
 */
public class DictDO implements Serializable {

    private static final long serialVersionUID = 1L;

    // Mã số
    private Long id;
    // Tên nhãn
    private String name;
    // Giá trị dữ liệu
    private String value;
    // Loại
    private String type;
    // Mô tả
    private String description;
    // Sắp xếp tăng dần
    private BigDecimal sort;
    // Mã cấp cha
    private Long parentId;
    //Người tạo
    private Integer createBy;
    // Thời gian tạo
    private Date createDate;
    // Người cập nhật
    private Long updateBy;
    // Thời gian cập nhật
    private Date updateDate;
    // Ghi chú
    private String remarks;
    //Cờ xóa
    private String delFlag;

    /**
     * Đặt: mã số
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy: mã số
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: tên nhãn
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Lấy: tên nhãn
     */
    public String getName() {
        return name;
    }

    /**
     * Đặt: giá trị dữ liệu
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Lấy: giá trị dữ liệu
     */
    public String getValue() {
        return value;
    }

    /**
     * Đặt: loại
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Lấy: loại
     */
    public String getType() {
        return type;
    }

    /**
     * Đặt: mô tả
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Lấy: mô tả
     */
    public String getDescription() {
        return description;
    }

    /**
     * Đặt: thứ tự (tăng dần)
     */
    public void setSort(BigDecimal sort) {
        this.sort = sort;
    }

    /**
     * Lấy: thứ tự (tăng dần)
     */
    public BigDecimal getSort() {
        return sort;
    }

    /**
     * Đặt: mã cấp cha
     */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /**
     * Lấy: mã cấp cha
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * Đặt: người tạo
     */
    public void setCreateBy(Integer createBy) {
        this.createBy = createBy;
    }

    /**
     * Lấy: người tạo
     */
    public Integer getCreateBy() {
        return createBy;
    }

    /**
     * Đặt: thời gian tạo
     */
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    /**
     * Lấy: thời gian tạo
     */
    public Date getCreateDate() {
        return createDate;
    }

    /**
     * Đặt: người cập nhật
     */
    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    /**
     * Lấy: người cập nhật
     */
    public Long getUpdateBy() {
        return updateBy;
    }

    /**
     * Đặt: thời gian cập nhật
     */
    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    /**
     * Lấy: thời gian cập nhật
     */
    public Date getUpdateDate() {
        return updateDate;
    }

    /**
     * Đặt: ghi chú
     */
    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    /**
     * Lấy: ghi chú
     */
    public String getRemarks() {
        return remarks;
    }

    /**
     * Đặt: cờ xóa
     */
    public void setDelFlag(String delFlag) {
        this.delFlag = delFlag;
    }

    /**
     * Lấy: cờ xóa
     */
    public String getDelFlag() {
        return delFlag;
    }

    @Override
    public String toString() {
        return "DictDO{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", value='" + value + '\'' +
            ", type='" + type + '\'' +
            ", description='" + description + '\'' +
            ", sort=" + sort +
            ", parentId=" + parentId +
            ", createBy=" + createBy +
            ", createDate=" + createDate +
            ", updateBy=" + updateBy +
            ", updateDate=" + updateDate +
            ", remarks='" + remarks + '\'' +
            ", delFlag='" + delFlag + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DictDO dictDO = (DictDO) o;
        return Objects.equals(id, dictDO.id) && Objects.equals(name, dictDO.name)
            && Objects.equals(value, dictDO.value) && Objects.equals(type, dictDO.type)
            && Objects.equals(description, dictDO.description) && Objects.equals(sort, dictDO.sort)
            && Objects.equals(parentId, dictDO.parentId) && Objects.equals(createBy, dictDO.createBy)
            && Objects.equals(createDate, dictDO.createDate) && Objects.equals(updateBy,
            dictDO.updateBy) && Objects.equals(updateDate, dictDO.updateDate) && Objects.equals(remarks,
            dictDO.remarks) && Objects.equals(delFlag, dictDO.delFlag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, value, type, description, sort, parentId, createBy, createDate, updateBy,
            updateDate,
            remarks, delFlag);
    }
}
