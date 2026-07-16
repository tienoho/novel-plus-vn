(function (factory) {
    if (typeof define === 'function' && define.amd) {
        define(['jquery', '../jquery.validate.min'], factory);
    } else {
        factory(jQuery);
    }
}(function ($) {
    $.extend($.validator.messages, {
        required: adminMessage('validationRequired', 'Trường này là bắt buộc.'),
        remote: adminMessage('validationRemote', 'Vui lòng sửa trường này.'),
        email: adminMessage('validationEmail', 'Vui lòng nhập địa chỉ email hợp lệ.'),
        url: adminMessage('validationUrl', 'Vui lòng nhập URL hợp lệ.'),
        date: adminMessage('validationDate', 'Vui lòng nhập ngày hợp lệ.'),
        number: adminMessage('validationNumber', 'Vui lòng nhập số hợp lệ.'),
        digits: adminMessage('validationDigits', 'Chỉ được nhập chữ số.'),
        equalTo: adminMessage('validationEqualTo', 'Vui lòng nhập lại cùng một giá trị.'),
        maxlength: $.validator.format(adminMessage('validationMaxlength', 'Vui lòng nhập không quá {0} ký tự.')),
        minlength: $.validator.format(adminMessage('validationMinlength', 'Vui lòng nhập ít nhất {0} ký tự.')),
        rangelength: $.validator.format(adminMessage('validationRangelength', 'Vui lòng nhập từ {0} đến {1} ký tự.')),
        range: $.validator.format(adminMessage('validationRange', 'Vui lòng nhập giá trị từ {0} đến {1}.')),
        max: $.validator.format(adminMessage('validationMax', 'Vui lòng nhập giá trị không lớn hơn {0}.')),
        min: $.validator.format(adminMessage('validationMin', 'Vui lòng nhập giá trị không nhỏ hơn {0}.'))
    });
}));
