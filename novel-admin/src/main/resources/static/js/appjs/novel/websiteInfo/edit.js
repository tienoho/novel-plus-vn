var E = window.wangEditor;
$("[id^='contentEditor']").each(function (index, ele) {
    var relName = $(ele).attr("id").substring(13);
    var editor = new E('#contentEditor' + relName);
// Cấu hình menu tùy chỉnh
    editor.customConfig.menus = [
        'head',  // Tiêu đề
        'bold',  // In đậm
        'fontSize',  // Cỡ chữ
        'fontName',  // Phông chữ
        'italic',  // In nghiêng
        'underline',  // Gạch chân
        'strikeThrough',  // Gạch ngang
        'foreColor',  // Màu chữ
        //'backColor',  // Màu nền
        //'link',  // Chèn liên kết
        'list',  // Danh sách
        'justify',  // Căn lề
        'quote',  // Trích dẫn
        'emoticon',  // Biểu tượng cảm xúc
        'image',  // Chèn ảnh
        //'table',  // Bảng
        //'video',  // Chèn video
        //'code',  // Chèn mã nguồn
        'undo',  // Hoàn tác
        'redo'  // Làm lại
    ];
    editor.customConfig.onchange = function (html) {
        // HTML sau khi nội dung thay đổi
        $("#" + relName).val(html);
    }
    editor.customConfig.uploadImgShowBase64 = true;
    editor.create();
    editor.txt.html($("#" + relName).val());

})

$("[id^='picImage']").each(function (index, ele) {
    var relName = $(ele).attr("id").substring(8);
    layui.use('upload', function () {
        var upload = layui.upload;
        //Khởi tạo
        var uploadInst = upload.render({
            elem: '#picImage' + relName, //Phần tử liên kết
            url: '/common/sysFile/upload', //API tải lên
            size: 1000,
            accept: 'file',
            done: function (r) {
                $("#picImage" + relName).attr("src", r.fileName);
                $("#" + relName).val(r.fileName);
            },
            error: function (r) {
                layer.msg(r.msg);
            }
        });
    });

});

$().ready(function () {
    validateRule();
});

$.validator.setDefaults({
    submitHandler: function () {
        update();
    }
});

function update() {
    $.ajax({
        cache: true,
        type: "POST",
        url: "/novel/websiteInfo/update",
        data: $('#signupForm').serialize(),// ID biểu mẫu
        async: false,
        error: function (request) {
            layer.alert(adminMessage('connectionError', 'Lỗi kết nối'));
        },
        success: function (data) {
            if (data.code == 0) {
                layer.msg(adminMessage('websiteSavedRestart', 'Thao tác thành công; khởi động lại novel-front để áp dụng.'));
            } else {
                layer.alert(data.msg)
            }

        }
    });

}

function validateRule() {
    var icon = "<i class='fa fa-times-circle'></i> ";
    $("#signupForm").validate({
        ignore: "",
        rules: {},
        messages: {}
    })
}
