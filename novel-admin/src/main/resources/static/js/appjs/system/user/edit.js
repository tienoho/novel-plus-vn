// Ví dụ từ tài liệu chính thức
$().ready(function() {
	validateRule();
	// $("#signupForm").validate();
});

$.validator.setDefaults({
	submitHandler : function() {
		update();
	}
});
function update() {
	$("#roleIds").val(getCheckedRoles());
	$.ajax({
		cache : true,
		type : "POST",
		url : "/sys/user/update",
		data : $('#signupForm').serialize(),// ID biểu mẫu
		async : false,
		error : function(request) {
			alert(adminMessage('connectionError', 'Lỗi kết nối'));
		},
		success : function(data) {
			if (data.code == 0) {
				parent.layer.msg(HtmlUtil.htmlEncode(data.msg || ""));
				parent.reLoad();
				var index = parent.layer.getFrameIndex(window.name); // Lấy chỉ mục cửa sổ
				parent.layer.close(index);

			} else {
				parent.layer.msg(HtmlUtil.htmlEncode(data.msg || ""));
			}

		}
	});

}
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
function setCheckedRoles() {
	var roleIds = $("#roleIds").val();
	alert(roleIds);
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
function validateRule() {
	var icon = "<i class='fa fa-times-circle'></i> ";
	$("#signupForm").validate({
		rules : {
			name : {
				required : true
			},
			username : {
				required : true,
				minlength : 2
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
				minlength : icon + adminMessage('usernameMinlength', 'Tên đăng nhập phải có ít nhất 2 ký tự')
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
