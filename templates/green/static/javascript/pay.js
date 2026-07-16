var UserPay = {
    sendPay: function () {
        $("#payform").submit();
    }
};

$(function () {
    $("#ulZFWX li").click(function () {
        $("#ulZFWX li").removeClass("on");
        $(this).addClass("on");

        var amount = Number($(this).attr("vals"));
        if (amount > 0) {
            $("#pValue").val(amount);
            $("#showTotal").text(new Intl.NumberFormat("vi-VN").format(amount) + " VND");
            $("#showRemark").text($(this).find(".pay_mn").text());
        }
    });
});
