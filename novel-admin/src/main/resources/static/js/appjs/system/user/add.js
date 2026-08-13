$().ready(function() {
	validateRule();
});

$.validator.setDefaults({
	submitHandler : function() {
		save();
	}
});
function getCheckedRoles() {
	var adIds = "";
	$("input:checkbox[name=role]:checked").each(function(i) {
		if (0 == i) {
			adIds = $(this).val();
		} else {
			adIds += ("," + $(this).val());
		}
	});
	return adIds;
}
function save() {
	$("#roleIds").val(getCheckedRoles());
	$.ajax({
		cache : true,
		type : "POST",
		url : "/sys/user/save",
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
			},
			username : {
				required : true,
				minlength : 2,
				remote : {
					url : "/sys/user/exit", // Chương trình xử lý backend
					type : "post", // Phương thức gửi dữ liệu
					dataType : "json", // Định dạng dữ liệu nhận
					data : { // Dữ liệu cần truyền
						username : function() {
							return $("#username").val();
						}
					}
				}
			},
			password : {
				required : true,
				minlength : 6
			},
			confirm_password : {
				required : true,
				minlength : 6,
				equalTo : "#password"
			},
			email : {
				required : true,
				email : true
			},
			topic : {
				required : "#newsletter:checked",
				minlength : 2
			},
			agree : "required"
		},
		messages : {

			name : {
				required : icon + adminMessage('userNameRequired', 'Vui lòng nhập họ tên')
			},
			username : {
				required : icon + adminMessage('usernameRequired', 'Vui lòng nhập tên đăng nhập'),
				minlength : icon + adminMessage('usernameMinlength', 'Tên đăng nhập phải có ít nhất 2 ký tự'),
				remote : icon + adminMessage('usernameExists', 'Tên đăng nhập đã tồn tại')
			},
			password : {
				required : icon + adminMessage('passwordRequired', 'Vui lòng nhập mật khẩu'),
				minlength : icon + adminMessage('passwordMinlength', 'Mật khẩu phải có ít nhất 6 ký tự')
			},
			confirm_password : {
				required : icon + adminMessage('passwordConfirmRequired', 'Vui lòng nhập lại mật khẩu'),
				minlength : icon + adminMessage('passwordMinlength', 'Mật khẩu phải có ít nhất 6 ký tự'),
				equalTo : icon + adminMessage('passwordMismatch', 'Mật khẩu nhập lại không khớp')
			},
			email : icon + adminMessage('emailRequired', 'Vui lòng nhập email'),
		}
	})
}

var openDept = function(){
	layer.open({
		type:2,
		title: adminMessage('departmentName', 'Phòng ban'),
		area : [ '300px', '450px' ],
		content:"/system/sysDept/treeView"
	})
}
function loadDept( deptId,deptName){
	$("#deptId").val(deptId);
	$("#deptName").val(deptName);
}
