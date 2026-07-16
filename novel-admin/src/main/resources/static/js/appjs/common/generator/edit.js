// Ví dụ từ tài liệu chính thức
$().ready(function() {
	validateRule();
});

$.validator.setDefaults({
	submitHandler : function() {
		console.log('Gửi thay đổi');
		update();
	}
});
function update() {
	$.ajax({
		cache : true,
		type : "POST",
		url : "/common/generator/update",
		data : $('#signupForm').serialize(),// ID biểu mẫu
		async : false,
		error : function(request) {
			parent.layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
		},
		success : function(data) {
			if (data.code == 0) {
				parent.layer.msg(data.msg);

			} else {
				parent.layer.msg(data.msg);
			}

		}
	});

}
function validateRule() {
	var icon = "<i class='fa fa-times-circle'></i> ";
	$("#signupForm").validate({
		rules : {
			author : {
				required : true
			},
			email : {
				required : true,
			},
			package : {
				required : true,
			},
			
		},
		messages : {

			author : {
				required : icon + adminMessage('generatorAuthorRequired', 'Vui lòng nhập tên tác giả')
			},
			email : {
				required : icon + adminMessage('generatorEmailRequired', 'Vui lòng nhập email'),
			},
			package : {
				required : icon + adminMessage('generatorPackageRequired', 'Vui lòng nhập tên package'),
			},
		}
	})
}
