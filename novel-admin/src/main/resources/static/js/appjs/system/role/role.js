var prefix = "/sys/role";
$(function() {
	load();
});

function load() {
	$('#exampleTable')
			.bootstrapTable(
					{
						method : 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
						url : prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
						striped : true, // Đặt true để tô màu xen kẽ các dòng
						dataType : "json", // Kiểu dữ liệu máy chủ trả về
						pagination : true, // Đặt true để hiển thị thanh phân trang ở cuối
						// queryParamsType : "limit",
						// //Đặt limit để gửi tham số theo định dạng RESTful
						singleSelect : false, // Đặt true để tắt chọn nhiều dòng
						iconSize : 'outline',
						toolbar : '#exampleToolbar',
						// contentType : "application/x-www-form-urlencoded",
						// //Kiểu mã hóa dữ liệu gửi tới máy chủ
						pageSize : 10, // Số bản ghi mỗi trang khi bật phân trang
						pageNumber : 1, // Số trang đầu tiên khi bật phân trang
						search : true, // Có hiển thị ô tìm kiếm hay không
						showColumns : true, // Có hiển thị menu chọn cột hay không
						sidePagination : "client", // Chọn phân trang ở client hoặc server
						// "server"
						// queryParams : queryParams,
						// //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
						// queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
						// limit, offset, search, sort, order nếu không cần chứa:
						// pageSize, pageNumber, searchText, sortName,
						// sortOrder.
						// Trả về false để hủy yêu cầu
						columns : [
								{ // Cấu hình cột
									// Kiểu dữ liệu; xem tài liệu Bootstrap Table để biết cấu hình chi tiết
									checkbox : true
								// Hiển thị ô chọn trong danh sách
								},
								{
									field : 'roleId', // Tên field của cột
									title : adminMessage('sequence', 'STT') // Tiêu đề cột
								},
								{
									field : 'roleName',
									title : adminMessage('roleName', 'Tên vai trò')
								},
								{
									field : 'remark',
									title : adminMessage('roleRemark', 'Ghi chú')
								},
								{
									field : '',
									title : adminMessage('rolePermission', 'Quyền')
								},
								{
									title : adminMessage('actions', 'Thao tác'),
									field : 'roleId',
									align : 'center',
									formatter : function(value, row, index) {
										var e = '<a class="btn btn-primary btn-sm '+s_edit_h+'" href="#" mce_href="#" title="' + adminMessage('edit', 'Sửa') + '" onclick="edit(\''
												+ row.roleId
												+ '\')"><i class="fa fa-edit"></i></a> ';
										var d = '<a class="btn btn-warning btn-sm '+s_remove_h+'" href="#" title="' + adminMessage('deleteLabel', 'Xóa') + '"  mce_href="#" onclick="remove(\''
												+ row.roleId
												+ '\')"><i class="fa fa-remove"></i></a> ';
										return e + d;
									}
								} ]
					});
}
function reLoad() {
	$('#exampleTable').bootstrapTable('refresh');
}
function add() {
	// Hộp thoại iframe
	layer.open({
		type : 2,
		title : adminMessage('addRole', 'Thêm vai trò'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/add' // URL iframe
	});
}
function remove(id) {
	layer.confirm(adminMessage('deleteConfirm', 'Bạn có chắc muốn xóa bản ghi đã chọn?'), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		$.ajax({
			url : prefix + "/remove",
			type : "post",
			data : {
				'id' : id
			},
			success : function(r) {
				if (r.code === 0) {
					layer.msg(adminMessage('deleteSuccess', 'Xóa thành công'));
					reLoad();
				} else {
					layer.msg(r.msg);
				}
			}
		});
	})

}
function edit(id) {
	layer.open({
		type : 2,
		title : adminMessage('editRole', 'Sửa vai trò'),
		maxmin : true,
		shadeClose : true, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/edit/' + id // URL iframe
	});
}
function batchRemove() {
	
	var rows = $('#exampleTable').bootstrapTable('getSelections'); // Trả về các dòng đã chọn; trả mảng rỗng nếu chưa chọn
	if (rows.length == 0) {
		layer.msg(adminMessage('batchDeleteEmpty', 'Vui lòng chọn dữ liệu cần xóa'));
		return;
	}
	layer.confirm(adminFormat('batchDeleteConfirm', 'Bạn có chắc muốn xóa {0} bản ghi đã chọn?', rows.length), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		var ids = new Array();
		$.each(rows, function(i, row) {
			ids[i] = row['roleId'];
		});
		console.log(ids);
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
