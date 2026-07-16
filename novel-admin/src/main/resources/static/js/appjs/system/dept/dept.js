
var prefix = "/system/sysDept"
$(function() {
	load();
});

function load() {
	$('#exampleTable')
		.bootstrapTreeTable(
			{
				id : 'deptId',
				code : 'deptId',
                parentCode : 'parentId',
				type : "GET", // Loại yêu cầu AJAX
				url : prefix + '/list', // URL yêu cầu AJAX
				ajaxParams : {}, // Thuộc tính data của yêu cầu AJAX
				expandColumn : '1', // Cột hiển thị nút mở rộng
				striped : true, // Có tô màu xen kẽ các dòng hay không
				bordered : true, // Có hiển thị đường viền hay không
				expandAll : false, // Có mở rộng toàn bộ hay không
				// toolbar : '#exampleToolbar',
				columns : [
					{
						title : adminMessage('menuId', 'Mã'),
						field : 'deptId',
						visible : false,
						align : 'center',
						valign : 'center',
						width : '50px',
						checkbox : true
					},
					{
						field : 'name',
						title : adminMessage('departmentName', 'Tên phòng ban'),
                        valign : 'center',
						witth :20
					},
					{
						field : 'orderNum',
						title : adminMessage('departmentSort', 'Thứ tự'),
                        align : 'center',
                        valign : 'center',
					},
					{
						field : 'delFlag',
						title : adminMessage('status', 'Trạng thái'),
						align : 'center',
                        valign : 'center',
						formatter : function(item, index) {
							if (item.delFlag == '0') {
								return '<span class="label label-danger">' + adminMessage('disabled', 'Đã khóa') + '</span>';
							} else if (item.delFlag == '1') {
								return '<span class="label label-primary">' + adminMessage('normal', 'Hoạt động') + '</span>';
							}
						}
					},
					{
						title : adminMessage('actions', 'Thao tác'),
						field : 'id',
						align : 'center',
                        valign : 'center',
						formatter : function(item, index) {
							var e = '<a class="btn btn-primary btn-sm ' + s_edit_h + '" href="#" mce_href="#" title="' + adminMessage('edit', 'Sửa') + '" onclick="edit(\''
								+ item.deptId
								+ '\')"><i class="fa fa-edit"></i></a> ';
							var a = '<a class="btn btn-primary btn-sm ' + s_add_h + '" href="#" title="' + adminMessage('departmentAddChild', 'Thêm phòng ban con') + '"  mce_href="#" onclick="add(\''
								+ item.deptId
								+ '\')"><i class="fa fa-plus"></i></a> ';
							var d = '<a class="btn btn-warning btn-sm ' + s_remove_h + '" href="#" title="' + adminMessage('deleteLabel', 'Xóa') + '"  mce_href="#" onclick="removeone(\''
								+ item.deptId
								+ '\')"><i class="fa fa-remove"></i></a> ';
							var f = '<a class="btn btn-success btn-sm＂ href="#" title="' + adminMessage('backup', 'Dự phòng') + '"  mce_href="#" onclick="resetPwd(\''
								+ item.deptId
								+ '\')"><i class="fa fa-key"></i></a> ';
							return e + a + d;
						}
					} ]
			});
}
function reLoad() {
	load();
}
function add(pId) {
	layer.open({
		type : 2,
		title : adminMessage('add', 'Thêm'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/add/' + pId
	});
}
function edit(id) {
	layer.open({
		type : 2,
		title : adminMessage('edit', 'Sửa'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/edit/' + id // URL iframe
	});
}
function removeone(id) {
	layer.confirm(adminMessage('deleteConfirm', 'Bạn có chắc muốn xóa bản ghi đã chọn?'), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		$.ajax({
			url : prefix + "/remove",
			type : "post",
			data : {
				'deptId' : id
			},
			success : function(r) {
				if (r.code == 0) {
					layer.msg(r.msg);
					reLoad();
				} else {
					layer.msg(r.msg);
				}
			}
		});
	})
}

function resetPwd(id) {
}
function batchRemove() {
	var rows = $('#exampleTable').bootstrapTable('getSelections'); // Trả về các dòng đã chọn; trả mảng rỗng nếu chưa chọn
	if (rows.length == 0) {
		layer.msg(adminMessage('batchDeleteEmpty', 'Vui lòng chọn dữ liệu cần xóa'));
		return;
	}
	layer.confirm(adminFormat('batchDeleteConfirm', 'Bạn có chắc muốn xóa {0} bản ghi đã chọn?', rows.length), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	// Nút
	}, function() {
		var ids = new Array();
		// Duyệt các dòng đã chọn và lấy ID tương ứng
		$.each(rows, function(i, row) {
			ids[i] = row['deptId'];
		});
		$.ajax({
			type : 'POST',
			data : {
				"ids" : ids
			},
			url : prefix + '/batchRemove',
			success : function(r) {
				if (r.code == 0) {
					layer.msg(r.msg);
					reLoad();
				} else {
					layer.msg(r.msg);
				}
			}
		});
	}, function() {});
}

