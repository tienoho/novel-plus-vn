package com.java2nb.novel.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * Đơn nạp tiền
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 03:49:57
 */
public class PayDO implements Serializable {

    private static final long serialVersionUID = 1L;


    // Khóa chính
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;
    // Dự phòng
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long outTradeNo;
    // Mã đơn hàng
    private String tradeNo;
    // Dự phòng
    private Integer payChannel;
    //Xu giao dịch
    private Integer totalAmount;
    // ID người dùng thanh toán
    // Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
    // Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long userId;
    // Trạng thái thanh toán: 0 thất bại, 1 thành công, 2 đang chờ
    private Integer payStatus;
    // Thời gian tạo
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    // Thời gian cập nhật
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String userName;

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
     * Đặt: dự phòng
     */
    public void setOutTradeNo(Long outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    /**
     * Lấy: dự phòng
     */
    public Long getOutTradeNo() {
        return outTradeNo;
    }

    /**
     * Đặt: mã đơn hàng
     */
    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    /**
     * Lấy: mã đơn hàng
     */
    public String getTradeNo() {
        return tradeNo;
    }

    /**
     * Đặt: dự phòng
     */
    public void setPayChannel(Integer payChannel) {
        this.payChannel = payChannel;
    }

    /**
     * Lấy: dự phòng
     */
    public Integer getPayChannel() {
        return payChannel;
    }

    /**
     * Đặt: Xu giao dịch
     */
    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * Lấy: Xu giao dịch
     */
    public Integer getTotalAmount() {
        return totalAmount;
    }

    /**
     * Đặt: ID người dùng thanh toán
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Lấy: ID người dùng thanh toán
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Đặt: trạng thái thanh toán: 0 thất bại, 1 thành công, 2 đang chờ
     */
    public void setPayStatus(Integer payStatus) {
        this.payStatus = payStatus;
    }

    /**
     * Lấy: trạng thái thanh toán: 0 thất bại, 1 thành công, 2 đang chờ
     */
    public Integer getPayStatus() {
        return payStatus;
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
