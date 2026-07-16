package com.java2nb.system.vo;

import com.java2nb.system.domain.UserDO;

/**
 * @author xiongxy
 * @date 2019-09-25 15:09:21
 */
public class UserVO {
    /**
     * Đối tượng người dùng cần cập nhật
     */
    private UserDO userDO = new UserDO();
    /**
     * Mật khẩu cũ
     */
    private String pwdOld;
    /**
     * Mật khẩu mới
     */
    private String pwdNew;

    public UserDO getUserDO() {
        return userDO;
    }

    public void setUserDO(UserDO userDO) {
        this.userDO = userDO;
    }

    public String getPwdOld() {
        return pwdOld;
    }

    public void setPwdOld(String pwdOld) {
        this.pwdOld = pwdOld;
    }

    public String getPwdNew() {
        return pwdNew;
    }

    public void setPwdNew(String pwdNew) {
        this.pwdNew = pwdNew;
    }
}
