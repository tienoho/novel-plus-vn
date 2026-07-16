var prefix = "/sys/menu"
$(document).ready(function () {
    load();
});
var load = function () {
    $('#exampleTable')
        .bootstrapTreeTable(
            {
                id: 'menuId',
                code: 'menuId',
                parentCode: 'parentId',
                type: "GET", // Loại yêu cầu AJAX
                url: prefix + '/list', // URL yêu cầu AJAX
                ajaxParams: {sort:'order_num'}, // Thuộc tính data của yêu cầu AJAX
                expandColumn: '1',// Cột hiển thị nút mở rộng
                striped: true, // Có tô màu xen kẽ các dòng hay không
                bordered: true, // Có hiển thị đường viền hay không
                expandAll: false, // Có mở rộng toàn bộ hay không
                // toolbar : '#exampleToolbar',
                columns: [
                    {
                        title: adminMessage('menuId', 'Mã'),
                        field: 'menuId',
                        visible: false,
                        align: 'center',
                        valign: 'center',
                        width: '5%'
                    },
                    {
                        title: adminMessage('menuName', 'Tên menu'),
                        valign: 'center',
                        field: 'name',
                        width: '20%'
                    },

                    {
                        title: adminMessage('menuIcon', 'Biểu tượng'),
                        field: 'icon',
                        align: 'center',
                        valign: 'center',
                        width : '5%',
                        formatter: function (item, index) {
                            return item.icon == null ? ''
                                : '<i class="' + item.icon
                                + ' fa-lg"></i>';
                        }
                    },
                    {
                        title: adminMessage('menuType', 'Loại'),
                        field: 'type',
                        align: 'center',
                        valign: 'center',
                        width : '10%',
                        formatter: function (item, index) {
                            if (item.type === 0) {
                                return '<span class="label label-primary">' + adminMessage('menuDirectory', 'Thư mục') + '</span>';
                            }
                            if (item.type === 1) {
                                return '<span class="label label-success">' + adminMessage('menuItem', 'Menu') + '</span>';
                            }
                            if (item.type === 2) {
                                return '<span class="label label-warning">' + adminMessage('menuButton', 'Nút') + '</span>';
                            }
                        }
                    },
                    {
                        title: adminMessage('menuUrl', 'Địa chỉ'),
                        valign: 'center',
                        width : '20%',
                        field: 'url'
                    },
                    {
                        title: adminMessage('menuPermission', 'Mã quyền'),
                        valign: 'center',
                        width : '20%',
                        field: 'perms'
                    },
                    {
                        title: adminMessage('actions', 'Thao tác'),
                        field: 'id',
                        align: 'center',
                        valign: 'center',
                        formatter: function (item, index) {
                            var e = '<a class="btn btn-primary btn-sm '
                                + s_edit_h
                                + '" href="#" mce_href="#" title="' + adminMessage('edit', 'Sửa') + '" onclick="edit(\''
                                + item.menuId
                                + '\')"><i class="fa fa-edit"></i></a> ';
                            var p = '<a class="btn btn-primary btn-sm '
                                + s_add_h
                                + '" href="#" mce_href="#" title="' + adminMessage('menuAddChild', 'Thêm menu con') + '" onclick="add(\''
                                + item.menuId
                                + '\')"><i class="fa fa-plus"></i></a> ';
                            var d = '<a class="btn btn-warning btn-sm '
                                + s_remove_h
                                + '" href="#" title="' + adminMessage('deleteLabel', 'Xóa') + '"  mce_href="#" onclick="remove(\''
                                + item.menuId
                                + '\')"><i class="fa fa-remove"></i></a> ';
                            return e + d + p;
                        }
                    }]
            });
}

function reLoad() {
    load();
}

function add(pId) {
    layer.open({
        type: 2,
        title: adminMessage('addMenu', 'Thêm menu'),
        maxmin: true,
        shadeClose: false, // Nhấp lớp phủ để đóng hộp thoại
        area: ['800px', '520px'],
        content: prefix + '/add/' + pId // URL iframe
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
            success: function (data) {
                if (data.code == 0) {
                    layer.msg(adminMessage('deleteSuccess', 'Xóa thành công'));
                    reLoad();
                } else {
                    layer.msg(data.msg);
                }
            }
        });
    })
}

function edit(id) {
    layer.open({
        type: 2,
        title: adminMessage('editMenu', 'Sửa menu'),
        maxmin: true,
        shadeClose: false, // Nhấp lớp phủ để đóng hộp thoại
        area: ['800px', '520px'],
        content: prefix + '/edit/' + id // URL iframe
    });
}

function batchRemove() {
    // var rows = $('#exampleTable').bootstrapTable('getSelections');

}
