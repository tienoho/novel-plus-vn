var prefix = "/common/generator"
$(function() {
	load();
});

function load() {
	$('#exampleTable')
			.bootstrapTable(
					{
						method : 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
						url : prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
						showRefresh : false,
						showToggle : false,
						showColumns : true,
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
						search : false, // Có hiển thị ô tìm kiếm hay không
						showColumns : false, // Có hiển thị menu chọn cột hay không
						sidePagination : "client", // Chọn phân trang ở client hoặc server
						// "server"
						// queryParams : queryParams,
						// //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
						// queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
						// limit, offset, search, sort, order nếu không cần chứa:
						// pageSize, pageNumber, searchText, sortName,
						// sortOrder.
						// Trả về false để hủy yêu cầu
						queryParams : function(params) {
							return {
								// Tham số gửi tới backend gồm offset, limit, sort, order và các cặp key/value của cột
								limit : params.limit,
								offset : params.offset,
								tableName : $('#tableName').val(),
							};
						},
						columns : [
								{
									checkbox : true
								},
								{
									field : 'tableName', // Tên field của cột
									title : adminMessage('generatorTableName', 'Tên bảng') // Tiêu đề cột
								},
								{
									field : 'engine',
									title : 'engine'
								},
								{
									field : 'tableComment',
									title : adminMessage('generatorTableDescription', 'Mô tả bảng')
								},
								{
									field : 'createTime',
									title : adminMessage('createdAt', 'Thời gian tạo')
								},
								{
									title : adminMessage('actions', 'Thao tác'),
									field : 'id',
									align : 'center',
									formatter : function(value, row, index) {
										/*var d = '<a class="btn btn-primary btn-sm" href="#" mce_href="#" title="' + adminMessage('generatorDownloadOnline', 'Tải mã trực tuyến') + '" onclick="downloadCode(\''
												+ row.tableName
												+ '\')"><i class="fa fa-cloud-download"></i></a> ';*/
										var g = '<a class="btn btn-primary btn-sm" href="#" mce_href="#" title="' + adminMessage('generatorGenerateLocal', 'Sinh mã cục bộ') + '" onclick="columnEdit(\''
											+ row.tableName
											+ '\')"><i class="fa fa-bug"></i></a> ';

										return g;
									}
								} ]
					});
}
function reLoad() {
	$('#exampleTable').bootstrapTable('refresh');
}
function downloadCode(tableName) {
	location.href = prefix + "/downLoadCode/" + tableName;
}
function genCode(tableName) {
	layer.confirm(adminMessage('generatorConfirmLocal', 'Bạn có chắc muốn sinh mã cho bản ghi đã chọn trong thư mục gốc dự án cục bộ?'), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		$.ajax({
			url : prefix + "/genCode",
			type : "post",
			data : {
				'tableName' : tableName
			},
			success : function(r) {
				if (r.code == 0) {
					layer.msg(r.msg);
				} else {
					layer.msg(r.msg);
				}
			}
		});
	})
}
function batchDownload() {
	var rows = $('#exampleTable').bootstrapTable('getSelections'); // Trả về các dòng đã chọn; trả mảng rỗng nếu chưa chọn
	if (rows.length == 0) {
		layer.msg(adminMessage('generatorSelectTable', 'Vui lòng chọn bảng cần sinh mã'));
		return;
	}
	var tables = new Array();
	// Duyệt các dòng đã chọn và lấy ID tương ứng
	$.each(rows, function(i, row) {
		tables[i] = row['tableName'];
	});
	location.href = prefix + "/batchDownload?tables=" + JSON.stringify(tables).replace('[','%5B').replace(']','%5D');
}

function batchCode() {
	var rows = $('#exampleTable').bootstrapTable('getSelections'); // Trả về các dòng đã chọn; trả mảng rỗng nếu chưa chọn
	if (rows.length == 0) {
		layer.msg(adminMessage('generatorSelectTable', 'Vui lòng chọn bảng cần sinh mã'));
		return;
	}
	var tables = new Array();
	// Duyệt các dòng đã chọn và lấy ID tương ứng
	$.each(rows, function(i, row) {
		tables[i] = row['tableName'];
	});
	layer.confirm(adminMessage('generatorConfirmBatch', 'Bạn có chắc muốn sinh mã hàng loạt cho các bản ghi đã chọn?'), {
		btn : [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
	}, function() {
		$.ajax({
			url : prefix + "/batchCode",
			type : "post",
			data : {
				'tables' : JSON.stringify(tables)
			},
			success : function(r) {
				if (r.code == 0) {
					layer.msg(r.msg);
				} else {
					layer.msg(r.msg);
				}
			}
		});
	})

}

function edit(){
	console.log('Mở trang cấu hình');
	layer.open({
		type : 2,
		title : adminMessage('generatorConfig', 'Cấu hình sinh mã'),
		maxmin : true,
		shadeClose : false, 
		area : [ '800px', '520px' ],
		content : prefix + '/edit'
	});
}

function columnEdit(tableName){
	layer.open({
		type : 2,
		title : adminMessage('generatorColumnConfig', 'Cấu hình cột'),
		maxmin : true,
		shadeClose : false,
		area : [ '800px', '520px' ],
		content : prefix + '/genColumns?tableName='+tableName
	});
}
