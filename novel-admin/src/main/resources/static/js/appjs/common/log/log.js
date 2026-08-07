var prefix = "/common/log"
$(function () {
    load();

});
$('#exampleTable').on('load-success.bs.table', function (e, data) {
    if (data.total && !data.rows.length) {
        $('#exampleTable').bootstrapTable('selectPage').bootstrapTable('refresh');
    }
});

function load() {
    $('#exampleTable')
        .bootstrapTable(
            {
                method: 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
                url: prefix + "/list", // Địa chỉ tải dữ liệu từ máy chủ
                // showRefresh : true,
                // showToggle : true,
                // showColumns : true,
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
                // search : true, // Có hiển thị ô tìm kiếm hay không
                // showColumns : true, // Có hiển thị menu chọn cột hay không
                sidePagination: "server", // Chọn phân trang ở client hoặc server
                // "server"
                queryParams: function (params) {
                    return {
                        limit: params.limit,
                        offset: params.offset,
                        name: $('#searchName').val(),
                        sort: 'gmt_create',
                        order: 'desc',
                        operation: $("#searchOperation").val(),
                        username: $("#searchUsername").val()
                    };
                },
                // //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
                // queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
                // limit, offset, search, sort, order nếu không cần chứa:
                // pageSize, pageNumber, searchText, sortName,
                // sortOrder.
                // Trả về false để hủy yêu cầu
                columns: [
                    {
                        checkbox: true
                    },
                    {
                        field: 'id', // Tên field của cột
                        title: adminMessage('sequence', 'STT') // Tiêu đề cột
                    },
                    {
                        field: 'userId',
                        title: adminMessage('logUserId', 'ID người dùng')
                    },
                    {
                        field: 'username',
                        title: adminMessage('logUsername', 'Tên đăng nhập')
                    },
                    {
                        field: 'operation',
                        title: adminMessage('actions', 'Thao tác')
                    },
                    {
                        field: 'time',
                        title: adminMessage('logElapsed', 'Thời gian xử lý')
                    },
                    {
                        field: 'method',
                        title: adminMessage('logMethod', 'Phương thức')
                    },
                    {
                        field: 'params',
                        title: adminMessage('logParameters', 'Tham số')
                    },
                    {
                        field: 'ip',
                        title: adminMessage('logIp', 'Địa chỉ IP')
                    },
                    {
                        field: 'gmtCreate',
                        title: adminMessage('createdAt', 'Thời gian tạo')
                    },
                    {
                        title: adminMessage('actions', 'Thao tác'),
                        field: 'id',
                        align: 'center',
                        formatter: function (value, row, index) {
                            var d = adminRowAction({action: 'remove', args: [row.id], variant: 'warning',
                                title: adminMessage('deleteLabel', 'Xóa'), icon: 'remove'});
                            return d;
                        }
                    }]
            });
}

function reLoad() {
    $('#exampleTable').bootstrapTable('refresh');
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
            beforeSend: function (request) {
                index = layer.load();
            },
            success: function (r) {
                if (r.code == 0) {
                    layer.close(index);
                    layer.msg(r.msg);
                    reLoad();
                } else {
                    layer.msg(r.msg);
                }
            }
        });
    })
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
