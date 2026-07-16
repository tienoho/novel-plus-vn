package com.java2nb.novel.service;


/**
 * @author 11797
 */
public interface FileService {

    /**
     * Chuyển ảnh mạng đã thu thập sang phương tiện lưu trữ của hệ thống (cục bộ, OSS, FastDFS)
     * @param picSrc đường dẫn ảnh mạng đã thu thập
     * @param picSavePath đường dẫn lưu
     * @return địa chỉ ảnh mới
     * */
    String transFile(String picSrc, String picSavePath);

}
