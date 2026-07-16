if ($.fn.pagination) {
    $.fn.pagination.defaults.beforePageText = 'Trang';
    $.fn.pagination.defaults.afterPageText = '/ {pages}';
    $.fn.pagination.defaults.displayMsg = 'Hiển thị {from}–{to} trên tổng số {total} bản ghi';
}
if ($.fn.datagrid) {
    $.fn.datagrid.defaults.loadMsg = 'Vui lòng chờ…';
}
if ($.fn.treegrid && $.fn.datagrid) {
    $.fn.treegrid.defaults.loadMsg = $.fn.datagrid.defaults.loadMsg;
}
if ($.messager) {
    $.messager.defaults.ok = 'Đồng ý';
    $.messager.defaults.cancel = 'Hủy';
}
if ($.fn.validatebox) {
    $.fn.validatebox.defaults.missingMessage = 'Trường này là bắt buộc';
    $.fn.validatebox.defaults.rules.email.message = 'Vui lòng nhập địa chỉ email hợp lệ';
    $.fn.validatebox.defaults.rules.url.message = 'Vui lòng nhập URL hợp lệ';
    $.fn.validatebox.defaults.rules.length.message = 'Độ dài phải từ {0} đến {1} ký tự';
    $.fn.validatebox.defaults.rules.remote.message = 'Vui lòng kiểm tra lại trường này';
}
if ($.fn.numberbox) {
    $.fn.numberbox.defaults.missingMessage = 'Trường này là bắt buộc';
}
if ($.fn.combobox) {
    $.fn.combobox.defaults.missingMessage = 'Trường này là bắt buộc';
}
if ($.fn.combotree) {
    $.fn.combotree.defaults.missingMessage = 'Trường này là bắt buộc';
}
if ($.fn.combogrid) {
    $.fn.combogrid.defaults.missingMessage = 'Trường này là bắt buộc';
}
if ($.fn.calendar) {
    $.fn.calendar.defaults.weeks = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    $.fn.calendar.defaults.months = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
}
if ($.fn.datebox) {
    $.fn.datebox.defaults.currentText = 'Hôm nay';
    $.fn.datebox.defaults.closeText = 'Đóng';
    $.fn.datebox.defaults.okText = 'Đồng ý';
    $.fn.datebox.defaults.missingMessage = 'Trường này là bắt buộc';
    $.fn.datebox.defaults.formatter = function (date) {
        var day = date.getDate();
        var month = date.getMonth() + 1;
        return (day < 10 ? '0' + day : day) + '/' + (month < 10 ? '0' + month : month) + '/' + date.getFullYear();
    };
    $.fn.datebox.defaults.parser = function (value) {
        if (!value) return new Date();
        var parts = value.split('/');
        var day = parseInt(parts[0], 10);
        var month = parseInt(parts[1], 10);
        var year = parseInt(parts[2], 10);
        return !isNaN(year) && !isNaN(month) && !isNaN(day) ? new Date(year, month - 1, day) : new Date();
    };
}
if ($.fn.datetimebox && $.fn.datebox) {
    $.extend($.fn.datetimebox.defaults, {
        currentText: $.fn.datebox.defaults.currentText,
        closeText: $.fn.datebox.defaults.closeText,
        okText: $.fn.datebox.defaults.okText,
        missingMessage: $.fn.datebox.defaults.missingMessage
    });
}
