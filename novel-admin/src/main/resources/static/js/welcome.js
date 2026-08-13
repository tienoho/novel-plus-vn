//Thông tin chào mừng

layer.config({
    extend: ['extend/layer.ext.js', 'skin/moon/style.css'],
    skin: 'layer-ext-moon'
});

layer.ready(function () {

    var html = $('#welcome-template').html();
    $('a.viewlog').click(function () {
        logs();
        return false;
    });

    $('#pay-qrcode').click(function(){
        var html=$(this).html();
        parent.layer.open({
            title: false,
            type: 1,
            closeBtn:false,
            shadeClose:true,
            area: ['600px', 'auto'],
            content: html
        });
    });

    function logs() {
        parent.layer.open({
            title: typeof adminMessage === 'function'
                ? adminMessage('welcomeTitle', 'Chào mừng đến với Khởi Thư')
                : 'Chào mừng đến với Khởi Thư',
            type: 1,
            area: ['700px', 'auto'],
            content: html,
            btn: [
                typeof adminMessage === 'function' ? adminMessage('confirm', 'Đồng ý') : 'Đồng ý',
                typeof adminMessage === 'function' ? adminMessage('cancel', 'Hủy') : 'Hủy'
            ]
        });
    }

    console.log(typeof adminMessage === 'function'
        ? adminMessage('welcomeMessage', 'Cảm ơn bạn đã sử dụng Khởi Thư.')
        : 'Cảm ơn bạn đã sử dụng Khởi Thư.');

});
