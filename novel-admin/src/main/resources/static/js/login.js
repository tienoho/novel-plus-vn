(function (window, document, $, layui) {
    'use strict';

    var loginForm = document.getElementById('signupForm');
    var captchaLink = document.getElementById('captchaRefresh');
    var captchaImage = document.getElementById('imgVerify');
    if (!loginForm || !captchaLink || !captchaImage) {
        return;
    }

    var messages = {
        usernameRequired: loginForm.getAttribute('data-username-required') || '',
        passwordRequired: loginForm.getAttribute('data-password-required') || '',
        captchaRequired: loginForm.getAttribute('data-captcha-required') || ''
    };

    function refreshCaptcha() {
        captchaImage.src = '/getVerify?' + Date.now();
    }

    captchaLink.addEventListener('click', function (event) {
        event.preventDefault();
        refreshCaptcha();
    });
    refreshCaptcha();

    layui.use(['form'], function () {
        var form = layui.form;
        var layer = layui.layer;

        if (window.top !== window.self) {
            window.top.location = window.self.location;
        }

        $(function () {
            $('.layui-container').particleground({
                dotColor: '#5cbdaa',
                lineColor: '#5cbdaa'
            });
        });

        form.on('submit(login)', function (submission) {
            var data = submission.field;
            if (data.username === '') {
                layer.msg(messages.usernameRequired);
                return false;
            }
            if (data.password === '') {
                layer.msg(messages.passwordRequired);
                return false;
            }
            if (data.verify === '') {
                layer.msg(messages.captchaRequired);
                return false;
            }
            $.ajax({
                type: 'POST',
                url: '/login',
                data: $('#signupForm').serialize(),
                success: function (response) {
                    if (response.code === 0) {
                        window.parent.location.href = response.mustChangePassword === true
                            ? '/sys/user/personal'
                            : '/index';
                    } else {
                        layer.msg(response.msg);
                    }
                }
            });
            return false;
        });
    });
})(window, document, jQuery, layui);
