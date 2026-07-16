package com.java2nb.novel.service;

/**
 * Dịch vụ định vị địa chỉ IP
 *
 * <p>Dịch vụ truy vấn vị trí địa lý theo địa chỉ IP,
 * bao gồm quốc gia, tỉnh và thành phố.</p>
 *
 * <p>Lớp là Service Bean do Spring quản lý và có thể inject vào Controller hoặc Service khác.</p>
 *
 * @author xiongxiaoyang
 * @date 2025/6/30
 */
public interface IpLocationService {

    /**
     * Truy vấn vị trí địa lý theo địa chỉ IP
     *
     * @param ip địa chỉ IP cần truy vấn (IPv4)
     * @return tỉnh nếu là IP Trung Quốc; nếu không thì quốc gia
     */
    String getLocation(String ip);

}
