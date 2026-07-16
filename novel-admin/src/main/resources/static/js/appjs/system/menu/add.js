var prefix = "/sys/menu"
$(function() {
	validateRule();
	//Mở danh sách biểu tượng
    $("#ico-btn").click(function(){
        layer.open({
            type: 2,
			title: adminMessage('iconList', 'Danh sách biểu tượng'),
            content: '/fonts/FontIcoList.html',
            area: ['480px', '90%'],
            success: function(layero, index){
                //var body = layer.getChildFrame('.ico-list', index);
                //console.log(layero, index);
            }
        });
    });
});
$.validator.setDefaults({
	submitHandler : function() {
		submit01();
	}
});
function submit01() {
	$.ajax({
		cache : true,
		type : "POST",
		url : prefix + "/save",
		data : $('#signupForm').serialize(),
		async : false,
		error : function(request) {
			layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
		},
		success : function(data) {
			if (data.code == 0) {
				parent.layer.msg(adminMessage('saveSuccess', 'Lưu thành công'));
				parent.reLoad();
				var index = parent.layer.getFrameIndex(window.name); // Lấy chỉ mục cửa sổ
				parent.layer.close(index);

			} else {
				layer.alert(data.msg)
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
			type : {
				required : true
			}
		},
		messages : {
			name : {
				required : icon + adminMessage('menuNameRequired', 'Vui lòng nhập tên menu')
			},
			type : {
				required : icon + adminMessage('menuTypeRequired', 'Vui lòng chọn loại menu')
			}
		}
	})
}
