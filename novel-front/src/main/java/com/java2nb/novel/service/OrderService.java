package com.java2nb.novel.service;


/**
 * @author 11797
 */
public interface OrderService {


    /**
     * Tạo đơn nạp Xu
     *
     * @param payChannel kênh thanh toán
     * @param payAmount số tiền thanh toán
     * @param userId ID người dùng
     * @return mã đơn thương nhân
     */
    Long createPayOrder(Byte payChannel, Integer payAmount, Long userId);


    /**
     * Cập nhật trạng thái đơn hàng
     *
     * @param outTradeNo mã đơn thương nhân
     * @param tradeNo mã đơn Alipay/WeChat
     * @param payStatus trạng thái thanh toán
     */
    void updatePayOrder(Long outTradeNo, String tradeNo, int payStatus);
}
