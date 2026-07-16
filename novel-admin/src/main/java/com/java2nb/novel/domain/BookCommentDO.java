package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * Bảng bình luận tác phẩm
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 21:59:28
 */
public class BookCommentDO implements Serializable {

    private static final long serialVersionUID = 1L;


    // Khóa chính
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // ID tác phẩm
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long bookId;
    // Nội dung đánh giá
    private String commentContent;
    // Số phản hồi
    private Integer replyCount;
    // Trạng thái duyệt: 0 chờ duyệt, 1 đã duyệt, 2 từ chối
    private Integer auditStatus;
    // Thời gian đánh giá
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    // Người đánh giá
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long createUserId;

    private String bookName;

    private String userName;

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Đặt: khóa chính
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Lấy: khóa chính
     */
    public Long getId() {
        return id;
    }

    /**
     * Đặt: ID tác phẩm
     */
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    /**
     * Lấy: ID tác phẩm
     */
    public Long getBookId() {
        return bookId;
    }

    /**
     * Đặt: nội dung đánh giá
     */
    public void setCommentContent(String commentContent) {
        this.commentContent = commentContent;
    }

    /**
     * Lấy: nội dung đánh giá
     */
    public String getCommentContent() {
        return commentContent;
    }

    /**
     * Đặt: số phản hồi
     */
    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    /**
     * Lấy: số phản hồi
     */
    public Integer getReplyCount() {
        return replyCount;
    }

    /**
     * Đặt: trạng thái duyệt: 0 chờ duyệt, 1 đã duyệt, 2 từ chối
     */
    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    /**
     * Lấy: trạng thái duyệt: 0 chờ duyệt, 1 đã duyệt, 2 từ chối
     */
    public Integer getAuditStatus() {
        return auditStatus;
    }

    /**
     * Đặt: thời gian đánh giá
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * Lấy: thời gian đánh giá
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * Đặt: người đánh giá
     */
    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    /**
     * Lấy: người đánh giá
     */
    public Long getCreateUserId() {
        return createUserId;
    }
}
