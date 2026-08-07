var prefix = "/novel/bookContent"
$(function () {
    load();
});

function load() {
    $('#exampleTable')
        .bootstrapTable(
            {
                method: 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
                url: prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
                //	showRefresh : true,
                //	showToggle : true,
                //	showColumns : true,
                iconSize: 'outline',
                toolbar: '#exampleToolbar',
                striped: true, // Đặt true để tô màu xen kẽ các dòng
                dataType: "json", // Kiểu dữ liệu máy chủ trả về
                pagination: true, // Đặt true để hiển thị thanh phân trang ở cuối
                // queryParamsType : "limit",
                // //Đặt limit để gửi tham số theo định dạng RESTful
                singleSelect: false, // Đặt true để tắt chọn nhiều dòng
                // contentType : "application/x-www-form-urlencoded",
                // //Kiểu mã hóa dữ liệu gửi tới máy chủ
                pageSize: 10, // Số bản ghi mỗi trang khi bật phân trang
                pageNumber: 1, // Số trang đầu tiên khi bật phân trang
                //search : true, // Có hiển thị ô tìm kiếm hay không
                showColumns: false, // Có hiển thị menu chọn cột hay không
                sidePagination: "server", // Chọn phân trang ở client hoặc server
                queryParams: function (params) {
                    //Tham số gửi tới backend gồm offset, limit, sort, order và các cặp key/value của cột
                    var queryParams = getFormJson("searchForm");
                    queryParams.limit = params.limit;
                    queryParams.offset = params.offset;
                    return queryParams;
                },
                // //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
                // queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
                // limit, offset, search, sort, order nếu không cần chứa:
                // pageSize, pageNumber, searchText, sortName,
                // sortOrder.
                // Trả về false để hủy yêu cầu
                responseHandler: function (rs) {

                    if (rs.code == 0) {
                        return rs.data;
                    } else {
                        parent.layer.alert(rs.msg)
                        return {total: 0, rows: []};
                    }
                },
                columns: [
                    {
                        checkbox: true
                    },
                    {
                        title: adminMessage('sequence', 'STT'),
                        formatter: function () {
                            return arguments[2] + 1;
                        }
                    },
                                                                        {
                                field: 'id',
                                title: adminMessage('primaryKey', 'Khóa chính')
                            },

                        
                                                                        {
                                field: 'indexId',
                                title: adminMessage('bookContentIndexId', 'ID chương')
                            },

                        
                                                                        {
                                field: 'content',
                                title: adminMessage('bookContent', 'Nội dung chương')
                            },

                        
                                        {
                        title: adminMessage('actions', 'Thao tác'),
                        field: 'id',
                        align: 'center',
                        formatter: function (value, row, index) {
                            var d = adminRowAction({action: 'detail', args: [row.id], variant: 'primary', visibility: s_detail_h, title: adminMessage('detail', 'Chi tiết'), icon: 'detail'});
                            var e = adminRowAction({action: 'edit', args: [row.id], variant: 'primary', visibility: s_edit_h, title: adminMessage('edit', 'Sửa'), icon: 'edit'});
                            var r = adminRowAction({action: 'remove', args: [row.id], variant: 'warning', visibility: s_remove_h, title: adminMessage('deleteLabel', 'Xóa'), icon: 'remove'});
                            return d + e + r;
                        }
                    }]
            });
}
function reLoad() {
    $('#exampleTable').bootstrapTable('refresh');
}
function add() {
    layer.open({
        type: 2,
        title: adminMessage('add', 'Thêm'),
        maxmin: true,
        shadeClose: false, // Nhấp lớp phủ để đóng hộp thoại
        area: ['800px', '520px'],
        content: prefix + '/add' // URL iframe
    });
}
function detail(id) {
    layer.open({
        type: 2,
        title: adminMessage('detail', 'Chi tiết'),
        maxmin: true,
        shadeClose: false, // Nhấp lớp phủ để đóng hộp thoại
        area: ['800px', '520px'],
        content: prefix + '/detail/' + id // URL iframe
    });
}
function edit(id) {
    layer.open({
        type: 2,
        title: adminMessage('edit', 'Sửa'),
        maxmin: true,
        shadeClose: false, // Nhấp lớp phủ để đóng hộp thoại
        area: ['800px', '520px'],
        content: prefix + '/edit/' + id // URL iframe
    });
}
function remove(id) {
    layer.confirm(adminMessage('deleteConfirm', 'Bạn có chắc muốn xóa bản ghi đã chọn?'), {
        btn: [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
    }, function () {
        $.ajax({
            url: prefix + "/remove",
            type: "post",
            data: {
                'id': id
            },
            success: function (r) {
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
        btn: [adminMessage('confirm', 'Đồng ý'), adminMessage('cancel', 'Hủy')]
        // Nút
    }, function () {
        var ids = new Array();
        // Duyệt các dòng đã chọn và lấy ID tương ứng
        $.each(rows, function (i, row) {
            ids[i] = row['id'];
        });
        $.ajax({
            type: 'POST',
            data: {
                "ids": ids
            },
            url: prefix + '/batchRemove',
            success: function (r) {
                if (r.code == 0) {
                    layer.msg(r.msg);
                    reLoad();
                } else {
                    layer.msg(r.msg);
                }
            }
        });
    }, function () {

    });
}
