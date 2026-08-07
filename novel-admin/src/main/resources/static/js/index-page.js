(function (window, document, $) {
    'use strict';

    window.dictList = [];
    $.ajax({
        url: '/common/dict/list',
        data: {limit: 1000, offset: 0},
        success: function (data) {
            window.dictList = data && Array.isArray(data.rows) ? data.rows : [];
        }
    });

    document.addEventListener('click', function (event) {
        var link = event.target.closest('[data-admin-personal]');
        if (!link) {
            return;
        }
        event.preventDefault();
        window.layer.open({
            type: 2,
            title: link.getAttribute('data-personal-title') || '',
            maxmin: true,
            shadeClose: false,
            area: ['800px', '600px'],
            content: '/sys/user/personal'
        });
    });
})(window, document, window.jQuery);
