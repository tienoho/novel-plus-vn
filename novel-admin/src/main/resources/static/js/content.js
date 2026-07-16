var $parentNode = window.parent.document;

function $childNode(name) {
    return window.frames[name]
}

// tooltips
$('.tooltip-demo').tooltip({
    selector: "[data-toggle=tooltip]",
    container: "body"
});

// Dùng animation.css cho Bootstrap Modal
$('.modal').appendTo("body");

$("[data-toggle=popover]").popover();

//Thu gọn ibox
$('.collapse-link').click(function () {
    var ibox = $(this).closest('div.ibox');
    var button = $(this).find('i');
    var content = ibox.find('div.ibox-content');
    content.slideToggle(200);
    button.toggleClass('fa-chevron-up').toggleClass('fa-chevron-down');
    ibox.toggleClass('').toggleClass('border-bottom');
    setTimeout(function () {
        ibox.resize();
        ibox.find('[id^=map-]').resize();
    }, 50);
});

//Đóng ibox
$('.close-link').click(function () {
    var content = $(this).closest('div.ibox');
    content.remove();
});

//Kiểm tra trang hiện tại có nằm trong iframe hay không
//if (top == this) {
//    var gohome = '<div class="gohome"><a class="animated bounceInUp" href="index.html?v=4.0" title="Về trang chủ"><i class="fa fa-home"></i></a></div>';
//    $('body').append(gohome);
//}

//animation.css
function animationHover(element, animation) {
    element = $(element);
    element.hover(
        function () {
            element.addClass('animated ' + animation);
        },
        function () {
            //Xóa class trước khi hoạt ảnh hoàn tất
            window.setTimeout(function () {
                element.removeClass('animated ' + animation);
            }, 2000);
        });
}

//Kéo bảng điều khiển
function WinMove() {
    var element = "[class*=col]";
    var handle = ".ibox-title";
    var connect = "[class*=col]";
    $(element).sortable({
            handle: handle,
            connectWith: connect,
            tolerance: 'pointer',
            forcePlaceholderSize: true,
            opacity: 0.8,
        })
        .disableSelection();
};


//Hàm AJAX tải ảnh của trình soạn thảo
function sendFile(files, editor, $editable) {
    var size = files[0].size;
    if((size / 1024 / 1024) > 2) {
        alert(typeof adminMessage === 'function'
            ? adminMessage('imageMax2M', 'Kích thước ảnh không được vượt quá 2 MB.')
            : 'Kích thước ảnh không được vượt quá 2 MB.');
        return false;
    }
    console.log("size="+size);
    var formData = new FormData();
    formData.append("file", files[0]);
    $.ajax({
        data : formData,
        type : "POST",
        url : "/common/sysFile/upload",    // API tải ảnh trả về đường dẫn ảnh dạng HTTP
        cache : false,
        contentType : false,
        processData : false,
        dataType : "json",
        success: function(data) {//data là dữ liệu trả về; key là tên tệp đã định nghĩa
            $('.summernote').summernote('insertImage',data.fileName);
        },
        error:function(){
            alert(typeof adminMessage === 'function'
                ? adminMessage('uploadFailed', 'Tải lên thất bại')
                : 'Tải lên thất bại');
        }
    });
}
