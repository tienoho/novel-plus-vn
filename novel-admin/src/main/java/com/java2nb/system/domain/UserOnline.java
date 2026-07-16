package com.java2nb.system.domain;

import java.util.Date;

/**
 *
 *
 */
public class UserOnline {

    /**
     */
    private String id;

    private String userId;

    private String username;

    /**
     * Địa chỉ máy người dùng
     */
    private String host;

    /**
     * IP hệ thống khi người dùng đăng nhập
     */
    private String systemHost;

    /**
     * Loại trình duyệt người dùng
     */
    private String userAgent;

    /**
     * Trạng thái trực tuyến
     */
    private String status = "on_line";

    /**
     * Thời gian tạo session
     */
    private Date startTimestamp;
    /**
     * Thời gian truy cập session gần nhất
     */
    private Date lastAccessTime;

    /**
     * Thời gian hết hạn
     */
    private Long timeout;

    /**
     * Bản sao phiên người dùng hiện tại
     */
    private String onlineSession;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Date startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Date getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOnlineSession() {
        return onlineSession;
    }

    public void setOnlineSession(String onlineSession) {
        this.onlineSession = onlineSession;
    }


    public String getSystemHost() {
        return systemHost;
    }

    public void setSystemHost(String systemHost) {
        this.systemHost = systemHost;
    }



}
