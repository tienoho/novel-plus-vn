package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.hibernate.validator.constraints.URL;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 15:12:25
 */
public class FriendLinkDO implements Serializable {

    private static final long serialVersionUID = 1L;


    // Khóa chính
    private Integer id;
    // Tên liên kết
    private String linkName;
    // URL liên kết
    @URL
    private String linkUrl;
    // Số thứ tự
    private Integer sort;
    // Bật: 0 tắt, 1 bật
    private Integer isOpen;
    //ID người tạo
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long createUserId;
    // Thời gian tạo
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    // ID người cập nhật
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long updateUserId;
    // Thời gian cập nhật
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Đặt: khóa chính
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Lấy: khóa chính
     */
    public Integer getId() {
        return id;
    }

    /**
     * Đặt: tên liên kết
     */
    public void setLinkName(String linkName) {
        this.linkName = linkName;
    }

    /**
     * Lấy: tên liên kết
     */
    public String getLinkName() {
        return linkName;
    }

    /**
     * Đặt: URL liên kết
     */
    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    /**
     * Lấy: URL liên kết
     */
    public String getLinkUrl() {
        return linkUrl;
    }

    /**
     * Đặt: số thứ tự
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * Lấy: số thứ tự
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Đặt: bật: 0 tắt, 1 bật
     */
    public void setIsOpen(Integer isOpen) {
        this.isOpen = isOpen;
    }

    /**
     * Lấy: bật: 0 tắt, 1 bật
     */
    public Integer getIsOpen() {
        return isOpen;
    }

    /**
     * Đặt: ID người tạo
     */
    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    /**
     * Lấy: ID người tạo
     */
    public Long getCreateUserId() {
        return createUserId;
    }

    /**
     * Đặt: thời gian tạo
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * Lấy: thời gian tạo
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * Đặt: ID người cập nhật
     */
    public void setUpdateUserId(Long updateUserId) {
        this.updateUserId = updateUserId;
    }

    /**
     * Lấy: ID người cập nhật
     */
    public Long getUpdateUserId() {
        return updateUserId;
    }

    /**
     * Đặt: thời gian cập nhật
     */
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * Lấy: thời gian cập nhật
     */
    public Date getUpdateTime() {
        return updateTime;
    }
}
