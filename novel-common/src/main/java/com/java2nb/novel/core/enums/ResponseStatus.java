package com.java2nb.novel.core.enums;

import io.github.xxyopen.model.resp.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.java2nb.novel.core.i18n.Messages;

/**
 * @author 11797
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum ResponseStatus implements IResultCode {


    /**
     * Lỗi liên quan đến người dùng
     * */
   NO_LOGIN(1001, "auth.login.required"),
    VEL_CODE_ERROR(1002, "auth.captcha.invalid"),
    USERNAME_EXIST(1003,"auth.phone.registered"),
    USERNAME_PASS_ERROR(1004,"auth.phonePassword.invalid"),
    TWO_PASSWORD_DIFF(1005, "user.password.mismatch"),
    OLD_PASSWORD_ERROR(1006, "user.password.oldInvalid"),
    USER_NO_BALANCE(1007, "payment.balance.insufficient"),

    /**
     * Lỗi liên quan đến bình luận
     * */
    HAS_COMMENTS(3001, "book.comment.duplicate"),

    /**
     * Lỗi liên quan đến tác giả
     * */
    INVITE_CODE_INVALID(4001, "author.invite.invalid"),
    AUTHOR_STATUS_FORBIDDEN(4002, "author.status.forbidden")
    , BOOKNAME_EXISTS(4003,"author.book.nameExists"),

    /**
     * Lỗi liên quan đến tác phẩm
     */
    BOOK_EXISTS(5001,"book.exists")

            ,
    /**
     * Lỗi liên quan đến công cụ tìm kiếm
     * */
    ES_SEARCH_FAIL(9001,"error.search"),

    /**
     * Lỗi liên quan đến tệp
     * */
    FILE_DIR_MAKE_FAIL(10001,"error.file.directory"),
    FILE_NOT_IMAGE(10002,"error.file.notImage"),
    FILE_SIZE_LIMIT(10003,"error.file.size"),

    /**
     * Lỗi dùng chung khác
     * */
    PASSWORD_ERROR(88001,"auth.password.invalid");

    private int code;
    private String msg;

    @Override
    public String getMsg() {
        return Messages.getDefault(msg);
    }


}
