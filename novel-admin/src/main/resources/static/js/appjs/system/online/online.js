var prefix = "/sys/online"
$(function() {
	load();
});

function load() {
	$('#exampleTable')
			.bootstrapTable(
					{
						method : 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
						url : prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
						// showRefresh : true,
						// showToggle : true,
						// showColumns : true,
						iconSize : 'outline',
						toolbar : '#exampleToolbar',
						striped : true, // Đặt true để tô màu xen kẽ các dòng
						dataType : "json", // Kiểu dữ liệu máy chủ trả về
						pagination : true, // Đặt true để hiển thị thanh phân trang ở cuối
						// queryParamsType : "limit",
						// //Đặt limit để gửi tham số theo định dạng RESTful
						singleSelect : false, // Đặt true để tắt chọn nhiều dòng
						// contentType : "application/x-www-form-urlencoded",
						// //Kiểu mã hóa dữ liệu gửi tới máy chủ
						pageSize : 10, // Số bản ghi mỗi trang khi bật phân trang
						pageNumber : 1, // Số trang đầu tiên khi bật phân trang
						// search : true, // Có hiển thị ô tìm kiếm hay không
						showColumns : false, // Có hiển thị menu chọn cột hay không
						sidePagination : "client", // Chọn phân trang ở client hoặc server
						// "server"
						queryParams : function(params) {
							return {
								// Tham số gửi tới backend gồm offset, limit, sort, order và các cặp key/value của cột
								limit : params.limit,
								offset : params.offset,
								name : $('#searchName').val()
							};
						},
						// //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
						// queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
						// limit, offset, search, sort, order nếu không cần chứa:
						// pageSize, pageNumber, searchText, sortName,
						// sortOrder.
						// Trả về false để hủy yêu cầu
						columns : [
								{
									checkbox : true
								},
								{
									field : 'id', // Tên field của cột
									title : adminMessage('sequence', 'STT') // Tiêu đề cột
								},
								{
									field : 'username',
									title : adminMessage('username', 'Tên đăng nhập')
								},
								{
									field : 'host',
									title : adminMessage('onlineHost', 'Máy khách')
								},
								{
									field : 'startTimestamp',
									title : adminMessage('loginTime', 'Thời gian đăng nhập')
								},
								{
									field : 'lastAccessTime',
									title : adminMessage('lastAccess', 'Truy cập gần nhất')
								},
								{
									field : 'timeout',
									title : adminMessage('expireTime', 'Thời gian hết hạn')
								},
								{
									field : 'status',
									title : adminMessage('status', 'Trạng thái'),
									align : 'center',
									formatter : function(value, row, index) {
										if (value == 'on_line') {
											return '<span class="label label-success">' + adminMessage('online', 'Trực tuyến') + '</span>';
										} else if (value == 'off_line') {
											return '<span class="label label-primary">' + adminMessage('offline', 'Ngoại tuyến') + '</span>';
										}
									}
								},
								{
									title : adminMessage('actions', 'Thao tác'),
									field : 'id',
									align : 'center',
									formatter : function(value, row, index) {
										var d = adminRowAction({action: 'force-logout', args: [row.id], variant: 'warning', title: adminMessage('deleteLabel', 'Xóa'), icon: 'remove'});
										return d;
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
		title : adminMessage('addUser', 'Thêm người dùng'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/add'
	});
}
function forceLogout(id) {
	layer.confirm(adminMessage('forceLogoutConfirm', 'Bạn có chắc muốn buộc người dùng đã chọn đăng xuất?'), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		$.ajax({
			url : prefix+"/forceLogout/" + id,
			type : "post",
			data : {
				'id' : id
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
function edit(id) {
	layer.open({
		type : 2,
		title : adminMessage('editUser', 'Sửa người dùng'),
		maxmin : true,
		shadeClose : true, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/edit/' + id // URL iframe
	});
}
function resetPwd(id) {
	layer.open({
		type : 2,
		title : adminMessage('resetPassword', 'Đặt lại mật khẩu'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '400px', '260px' ],
		content : prefix + '/resetPwd/' + id // URL iframe
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
	// Nút
	}, function() {
		var ids = new Array();
		// Duyệt các dòng đã chọn và lấy ID tương ứng
		$.each(rows, function(i, row) {
			ids[i] = row['userId'];
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
	}, function() {
	});
}
function getTreeData() {
	$.ajax({
		type : "GET",
		url : "/system/sysDept/tree",
		success : function(tree) {
			loadTree(tree);
		}
	});
}
function loadTree(tree) {
	$('#jstree').jstree({
		'core' : {
			'data' : tree
		},
		"plugins" : [ "search" ]
	});
	$('#jstree').jstree().open_all();
}
$('#jstree').on("changed.jstree", function(e, data) {
	if (data.selected == -1) {
		var opt = {
			query : {
				deptId : '',
			}
		}
		$('#exampleTable').bootstrapTable('refresh', opt);
	} else {
		var opt = {
			query : {
				deptId : data.selected[0],
			}
		}
		$('#exampleTable').bootstrapTable('refresh', opt);
	}

});
