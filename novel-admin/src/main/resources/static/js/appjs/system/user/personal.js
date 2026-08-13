var prefix = "/sys/user"
$(function () {
    laydate({
        elem : '#birth'
    });
});
/**
 * Gửi thông tin cơ bản
 */
$("#base_save").click(function () {
    var hobbyStr = getHobbyStr();
    $("#hobby").val(hobbyStr);
    if($("#basicInfoForm").valid()){
            $.ajax({
                cache : true,
                type : "POST",
                url :"/sys/user/updatePeronal",
                data : $('#basicInfoForm').serialize(),
                async : false,
                error : function(request) {
                    layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
                },
                success : function(data) {
                    if (data.code == 0) {
                        parent.layer.msg(adminMessage('updateSuccess', 'Cập nhật thành công'));
                    } else {
                        parent.layer.alert(HtmlUtil.htmlEncode(data.msg || ""))
                    }
                }
            });
        }

});
$("#pwd_save").click(function () {
    if($("#modifyPwd").valid()){
        $.ajax({
            cache : true,
            type : "POST",
            url :"/sys/user/resetPwd",
            data : $('#modifyPwd').serialize(),
            async : false,
            error : function(request) {
                parent.layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
            },
            success : function(data) {
                if (data.code == 0) {
                    parent.layer.alert(adminMessage('updateSuccess', 'Cập nhật thành công'));
                    $("#photo_info").click();
                } else {
                    parent.layer.alert(HtmlUtil.htmlEncode(data.msg || ""))
                }
            }
        });
    }
});
function getHobbyStr(){
    var hobbyStr ="";
    $(".hobby").each(function () {
        if($(this).is(":checked")){
            hobbyStr+=$(this).val()+";";
        }
    });
   return hobbyStr;
}
