package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-18 11:08:54
 */
public class UserFeedbackDO implements Serializable {

    private static final long serialVersionUID = 1L;


    //ID khóa chính
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // ID người dùng
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long userId;
    // Nội dung phản hồi
    private String content;
    // Thời gian phản hồi
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String userName;


    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Đặt: ID khóa chính
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy: ID khóa chính
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: ID người dùng
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Lấy: ID người dùng
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Đặt: nội dung phản hồi
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Lấy: nội dung phản hồi
     */
    public String getContent() {
        return content;
    }

    /**
     * Đặt: thời gian phản hồi
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * Lấy: thời gian phản hồi
     */
    public Date getCreateTime() {
        return createTime;
    }
}
