
var prefix = "/common/dict"
$(function() {
	
	//	var config = {
	//		'.chosen-select' : {},
	//		'.chosen-select-deselect' : {
	//			allow_single_deselect : true
	//		},
	//		'.chosen-select-no-single' : {
	//			disable_search_threshold : 10
	//		},
	//		'.chosen-select-no-results' : {
	//			no_results_text : 'Không có dữ liệu'
	//		},
	//		'.chosen-select-width' : {
	//			width : "95%"
	//		}
	//	}
	//	for (var selector in config) {
	//		$(selector).chosen(config[selector]);
	//	}
	load();
});
function selectLoad() {
	var html = "";
	$.ajax({
		url : '/common/dict/type',
		success : function(data) {
			//Tải dữ liệu
			for (var i = 0; i < data.length; i++) {
				html += '<option value="' + data[i].type + '">' + data[i].description + '</option>'
			}
			$(".chosen-select").append(html);
			$(".chosen-select").chosen({
				maxHeight : 200
			});
			//Sự kiện nhấp
			$('.chosen-select').on('change', function(e, params) {
				console.log(params.selected);
				var opt = {
					query : {
						type : params.selected,
					}
				}
				$('#exampleTable').bootstrapTable('refresh', opt);
			});
		}
	});
}
function load() {
	selectLoad();
	$('#exampleTable')
		.bootstrapTable(
			{
				method : 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
				url : prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
				//	showRefresh : true,
				//	showToggle : true,
				//	showColumns : true,
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
				//search : true, // Có hiển thị ô tìm kiếm hay không
				showColumns : false, // Có hiển thị menu chọn cột hay không
				sidePagination : "server", // Chọn phân trang ở client hoặc server
				queryParams : function(params) {
					return {
						//Tham số gửi tới backend gồm offset, limit, sort, order và các cặp key/value của cột
						limit : params.limit,
						offset : params.offset,
						// name:$('#searchName').val(),
						type : $('#searchName').val(),
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
						field : 'id',
						title : adminMessage('dictId', 'Mã')
					},
					{
						field : 'name',
						title : adminMessage('dictLabel', 'Nhãn')
					},
					{
						field : 'value',
						title : adminMessage('dictValue', 'Giá trị dữ liệu'),
						width : '100px'
					},
					{
						field : 'type',
						title : adminMessage('dictType', 'Loại')
					},
					{
						field : 'description',
						title : adminMessage('dictDescription', 'Mô tả')
					},
					{
						visible : false,
						field : 'sort',
						title : adminMessage('dictSort', 'Thứ tự')
					},
					{
						visible : false,
						field : 'parentId',
						title : adminMessage('dictParentId', 'Mã cấp trên')
					},
					{
						visible : false,
						field : 'createBy',
						title : adminMessage('dictCreatedBy', 'Người tạo')
					},
					{
						visible : false,
						field : 'createDate',
						title : adminMessage('createdAt', 'Thời gian tạo')
					},
					{
						visible : false,
						field : 'updateBy',
						title : adminMessage('dictUpdatedBy', 'Người cập nhật')
					},
					{
						visible : false,
						field : 'updateDate',
						title : adminMessage('modifiedAt', 'Thời gian cập nhật')
					},
					{
						visible : false,
						field : 'remarks',
						title : adminMessage('dictRemark', 'Ghi chú')
					},
					{
						visible : false,
						field : 'delFlag',
						title : adminMessage('dictDeleted', 'Đánh dấu xóa')
					},
					{
						title : adminMessage('actions', 'Thao tác'),
						field : 'id',
						align : 'center',
						formatter : function(value, row, index) {
							var e = '<a class="btn btn-primary btn-sm ' + s_edit_h + '" href="#" mce_href="#" title="' + adminMessage('edit', 'Sửa') + '" onclick="edit(\''
								+ row.id
								+ '\')"><i class="fa fa-edit"></i></a> ';
							var d = '<a class="btn btn-warning btn-sm ' + s_remove_h + '" href="#" title="' + adminMessage('deleteLabel', 'Xóa') + '"  mce_href="#" onclick="remove(\''
								+ row.id
								+ '\')"><i class="fa fa-remove"></i></a> ';
							var f = '<a class="btn btn-success btn-sm ' + s_add_h + '" href="#" title="' + adminMessage('add', 'Thêm') + '"  mce_href="#" onclick="addD(\''
								+ row.type +'\',\''+row.description
								+ '\')"><i class="fa fa-plus"></i></a> ';
							return e + d +f;
						}
					} ]
			});
}
function reLoad() {
	var opt = {
		query : {
			type : $('.chosen-select').val(),
		}
	}
	$('#exampleTable').bootstrapTable('refresh', opt);
}
function add() {
	layer.open({
		type : 2,
		title : adminMessage('add', 'Thêm'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/add' // URL iframe
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

function addD(type,description) {
	layer.open({
		type : 2,
		title : adminMessage('add', 'Thêm'),
		maxmin : true,
		shadeClose : false, // Nhấp lớp phủ để đóng hộp thoại
		area : [ '800px', '520px' ],
		content : prefix + '/add/'+type+'/'+description // URL iframe
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
			ids[i] = row['id'];
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
