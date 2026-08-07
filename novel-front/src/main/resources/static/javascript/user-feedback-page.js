(function (window, document, $) {
    'use strict';

    var root = document.getElementById('userFeedbackPage');
    if (!root || !$) return;
    var messages = {
        required: root.getAttribute('data-feedback-required') || '',
        minLength: root.getAttribute('data-feedback-min-length') || '',
        network: root.getAttribute('data-network-error') || ''
    };
    var form = document.getElementById('feedbackForm');
    var submit = document.getElementById('btnSave');

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var content = String(document.getElementById('txtDescription').value || '').trim();
        if (!content) {
            window.layer.alert(messages.required);
            return;
        }
        if (content.length < 5) {
            window.layer.alert(messages.minLength);
            return;
        }
        submit.disabled = true;
        $.ajax({
            type: 'post',
            url: '/user/addFeedBack',
            data: {content: content},
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) window.location.href = '/user/feedback_list.html';
                else if (response.code === 1001) {
                    window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                } else if (typeof window.novelAlertText === 'function') window.novelAlertText(response.msg);
            },
            error: function () { window.layer.alert(messages.network); },
            complete: function () { submit.disabled = false; }
        });
    });
})(window, document, window.jQuery);
