package com.java2nb.novel.service;


import com.java2nb.novel.entity.FriendLink;

import java.util.List;

/**
 * @author 11797
 */
public interface FriendLinkService {

    /**
     * Truy vấn liên kết bạn bè trang chủ
     * @return tập dữ liệu
     * */
    List<FriendLink> listIndexLink();
}
