var UserPay = {
    czData: [[30, "3000 " + novelMessage('currency', 'Xu')], [50, "5000 " + novelMessage('currency', 'Xu')], [100, "10000 " + novelMessage('currency', 'Xu')], [200, "20000 " + novelMessage('currency', 'Xu')], [500, "50000 " + novelMessage('currency', 'Xu')], [365, novelMessage('annualReading', 'Gói đọc toàn trang một năm')]],
    czPayPalData: [[20, "10000 " + novelMessage('currency', 'Xu')], [50, "25000 " + novelMessage('currency', 'Xu')], [100, "50000 " + novelMessage('currency', 'Xu')], [80, novelMessage('annualReading', 'Gói đọc toàn trang một năm')]],
    sendPay: function () {
        $("#payform").submit();
    }
}

$(function () {
    $("#ulPayType li").click(function () {

        if($(this).attr("valp")==2){
            layer.alert(novelMessage('wechatUnavailable', 'Thanh toán WeChat chưa được hỗ trợ.'));
        }

        return ;



        $($(this).parent()).children().each(function () {
            $(this).removeClass("on");
        });
        $(this).addClass("on");

        var type = $(this).attr("valp");
        if (type == "3") {
            $("#ulPayPal").show();
            $("#ulPayPalXJ").show();
            $("#ulZFWX").hide();
            $("#ulZFWXXJ").hide();
        }
        else {
            $("#ulPayPal").hide();
            $("#ulPayPalXJ").hide();
            $("#ulZFWX").show();
            $("#ulZFWXXJ").show();
        }

    })

    $("#ulZFWX li").click(function () {
        $("#ulZFWX li").removeClass("on");
        $(this).addClass("on");
        if ($(this).attr("vals") > 0) {
            $("#pValue").val($(this).attr("vals"));
            $("#showTotal").html('CNY ' + $(this).attr("vals"));
            for (var i = 0; i < UserPay.czData.length; i++) {
                if (UserPay.czData[i][0] == $(this).attr("vals")) {
                    $("#showRemark").html(UserPay.czData[i][1]);
                    break;
                }
            }
        }
    });
    $("#ulPayPal li").click(function () {
        $("#ulPayPal li").removeClass("on");
        $(this).addClass("on");
        if ($(this).attr("vals") > 0) {
            $("#pValue").val($(this).attr("vals"));
            $("#showPayPalTotal").html('USD ' + $(this).attr("vals"));
            for (var i = 0; i < UserPay.czData.length; i++) {
                if (UserPay.czPayPalData[i][0] == $(this).attr("vals")) {
                    $("#showPayPalRemark").html(UserPay.czPayPalData[i][1]);
                    break;
                }
            }
        }
    });
});
