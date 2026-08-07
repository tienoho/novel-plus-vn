$().ready(function() {
	validateRule();
});

$.validator.setDefaults({
	submitHandler : function() {
		update();
	}
});
function update() {
	$.ajax({
		cache : true,
		type : "POST",
		url : "/common/dict/update",
		data : $('#signupForm').serialize(),// ID biểu mẫu
		async : false,
		error : function(request) {
			parent.layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
		},
		success : function(data) {
			if (data.code == 0) {
				parent.layer.msg(adminMessage('operationSuccess', 'Thao tác thành công'));
				parent.reLoad();
				var index = parent.layer.getFrameIndex(window.name); // Lấy chỉ mục cửa sổ
				parent.layer.close(index);

			} else {
				parent.layer.alert(HtmlUtil.htmlEncode(data.msg || ""))
			}

		}
	});

}
function validateRule() {
	var icon = "<i class='fa fa-times-circle'></i> ";
	$("#signupForm").validate({
		rules : {
			name : {
				required : true
			}
		},
		messages : {
			name : {
				required : icon + adminMessage('validationRequired', 'Trường này là bắt buộc.')
			}
		}
	})
}
