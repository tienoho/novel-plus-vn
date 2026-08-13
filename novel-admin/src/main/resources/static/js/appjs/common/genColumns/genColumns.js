var prefix = "/common/generator/genColumns";
var columnsData = [];
var tableName = "";
$(function () {
    load();
});

function load() {
    $('#exampleTable')
        .bootstrapTable(
            {
                method: 'get', // Phương thức yêu cầu dữ liệu máy chủ: GET hoặc POST
                url: prefix + "/list?tableName=" + $("#tableName").val(), // Địa chỉ tải dữ liệu từ máy chủ
                //	showRefresh : true,
                //	showToggle : true,
                //	showColumns : true,
                iconSize: 'outline',
                toolbar: '#exampleToolbar',
                striped: true, // Đặt true để tô màu xen kẽ các dòng
                dataType: "json", // Kiểu dữ liệu máy chủ trả về
                pagination: false, // Đặt true để hiển thị thanh phân trang ở cuối
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
                // //Có thể ghi đè tham số để bổ sung dữ liệu khi yêu cầu máy chủ
                // queryParamsType = 'limit' ,Dữ liệu trả về phải chứa
                // limit, offset, search, sort, order nếu không cần chứa:
                // pageSize, pageNumber, searchText, sortName,
                // sortOrder.
                // Trả về false để hủy yêu cầu
                responseHandler: function (rs) {

                    if (rs.code == 0) {
                        columnsData[0]=rs.data.rows[0];
                        tableName=columnsData[0].tableName;
                        rs.data.rows.splice(0,1);
                        return rs.data;
                    } else {
                        parent.layer.alert(rs.msg)
                        return {total: 0, rows: []};
                    }
                },
                onPostBody: function() {
                    loadDict();

                    $.ajax({
                        url : '/common/dict/type',
                        success : function(data) {
                            $("select[name=dictType]").each(function (index, domEle) {
                                var html = "";
                                //Tải dữ liệu
                                for (var i = 0; i < data.length; i++) {
                                    html += '<option value="' + data[i].type + '">' + data[i].description + '</option>'
                                }
                                $(domEle).append(html);
                                $(domEle).chosen({
                                    maxHeight: 200
                                });
                                $(domEle).val($(domEle).attr("select-value"));
                                $(domEle).trigger("chosen:updated");

                            });


                        }
                    });
                },
                columns: [
                    {
                        title: adminMessage('sequence', 'STT'),
                        formatter: function () {
                            return arguments[2] + 1;
                        }
                    },
                    {
                        field: 'columnName',
                        title: adminMessage('columnName', 'Tên cột')
                    },
                    {
                        field: 'columnType',
                        title: adminMessage('columnType', 'Kiểu cột')
                    },
                    {
                        field: 'javaType',
                        title: adminMessage('columnJavaType', 'Kiểu Java ánh xạ'),
                        formatter: function (value, row, index) {

                            return "<select style='width: 100px' class=\"form-control chosen-select\" tabindex=\"2\" dict-value='"+value+"' dict-type=\"java_type\" >\n" +
                                "                        </select>";
                        }
                    },
                    {
                        field: 'columnComment',
                        title: adminMessage('columnComment', 'Chú thích cột')
                    },
                    {
                        field: 'columnLabel',
                        title: adminMessage('columnLabel', 'Nhãn cột'),
                        formatter: function (value, row, index) {

                            return "<input style='width: 100px' class=\"form-control\" type='text' value='"+value+"'/>";
                        }
                    },
                    { /*<select data-placeholder="--Chọn loại--" name="catid" id="catid"
                    class="form-control chosen-select" tabindex="2" dict-type="novel_category" >
                        </select>*/
                        field: 'pageType',
                        title: adminMessage('columnDisplayType', 'Kiểu hiển thị trên trang'),
                        formatter: function (value, row, index) {

                            return "<select style='width: 100px' class=\"form-control chosen-select\" tabindex=\"2\" dict-value='"+value+"' dict-type=\"page_type\" >\n" +
                                "                        </select>";
                        }
                    },
                    {
                        field: 'dictType',
                        title: adminMessage('columnDictType', 'Loại từ điển'),
                        formatter: function (value, row, index) {
                            return "<select name='dictType' style='width: 150px' class=\"form-control chosen-select\" tabindex=\"2\" select-value='"+value+"' >\n" +
                                "                        </select>";

                        }
                    },
                    {
                        field: 'isRequired',
                        title: adminMessage('columnRequired', 'Bắt buộc'),
                        formatter: function (value, row, index) {
                            return "<input class=\"form-control\" type='checkbox' "+(value==1?'checked':'')+"/>";
                        }
                    },
                    {
                        field: 'columnSort',
                        title: adminMessage('columnSort', 'Thứ tự cột'),
                        formatter: function (value, row, index) {
                            return "<input style='width: 100px' class=\"form-control\" type='text' value='"+value+"'/>";
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


function save() {

    $('#exampleTable').find("tbody").find("tr").each(function (index, trEle){
        var columnData = {};
        columnData.tableName = tableName;
        columnsData[index+1]=columnData;

        $(trEle).find("td").each(function (index, tdEle){
            switch (index) {
                case 1:{
                    columnData.columnName = $(tdEle).text();
                    break;
                }
                case 2:{
                    columnData.columnType = $(tdEle).text();
                    break;
                }
                case 3:{
                    columnData.javaType = $(tdEle).find("select").eq(0).val();
                    break;
                }
                case 4:{
                    columnData.columnComment = $(tdEle).text();
                    break;
                }
                case 5:{
                    columnData.columnLabel = $(tdEle).find("input").eq(0).val();
                    break;
                }
                case 6:{
                    columnData.pageType = $(tdEle).find("select").eq(0).val();
                    break;
                }
                case 7:{
                    columnData.dictType = $(tdEle).find("select").eq(0).val();
                    break;
                }
                case 8:{
                    columnData.isRequired = $(tdEle).find("input").eq(0).is(':checked')?1:0;
                    break;
                }
                case 9:{
                    columnData.columnSort = $(tdEle).find("input").eq(0).val();
                    break;
                }



            }

        });
    });

console.log(columnsData)
    $.ajax({
        cache : true,
        type : "POST",
        url : prefix+"/save",
        headers : {
            "Content-Type": "application/json"
        },
        data : JSON.stringify(columnsData),
        async : false,
        error : function(request) {
            parent.layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
        },
        success : function(data) {
            if (data.code == 0) {
                parent.layer.msg(adminMessage('operationSuccess', 'Thao tác thành công'));
                parent.reLoad();
                var index = parent.layer.getFrameIndex(window.name); // Lấy chỉ mục cửa sổ
                parent.layer.close(index);

            } else {
                parent.layer.alert(HtmlUtil.htmlEncode(data.msg || ""))
            }

        }
    });

}
