package com.java2nb.common.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * Tải tệp lên
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-19 16:02:20
 */
public class FileDO implements Serializable {
    private static final long serialVersionUID = 1L;

    //
    private Long id;
    // Loại tệp
    private Integer type;
    // Địa chỉ URL
    private String url;
    // Thời gian tạo
    private Date createDate;


    public FileDO() {
        super();
    }


    public FileDO(Integer type, String url, Date createDate) {
        super();
        this.type = type;
        this.url = url;
        this.createDate = createDate;
    }


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
     * Đặt: loại tệp
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * Lấy: loại tệp
     */
    public Integer getType() {
        return type;
    }

    /**
     * Đặt: địa chỉ URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Lấy: địa chỉ URL
     */
    public String getUrl() {
        return url;
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

    @Override
    public String toString() {
        return "FileDO{" +
                "id=" + id +
                ", type=" + type +
                ", url='" + url + '\'' +
                ", createDate=" + createDate +
                '}';
    }
}
