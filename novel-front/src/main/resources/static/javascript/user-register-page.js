(function (window, document, $) {
    'use strict';

    if (!$) return;

    var form = document.querySelector('[data-register-form]');
    if (!form) return;

    var usernameInput = document.getElementById('txtUName');
    var passwordInput = document.getElementById('txtPassword');
    var captchaInput = document.getElementById('TxtChkCode');
    var captchaImage = document.getElementById('chkd');
    var submitButton = document.getElementById('btnRegister');
    var errorBox = document.getElementById('LabErr');
    var backButton = document.getElementById('registerBack');
    var isSubmitting = false;
    var isRedirecting = false;
    var messages = {
        phoneRequired: form.getAttribute('data-phone-required') || '',
        phoneInvalid: form.getAttribute('data-phone-invalid') || '',
        passwordRequired: form.getAttribute('data-password-required') || '',
        captchaRequired: form.getAttribute('data-captcha-required') || '',
        network: form.getAttribute('data-network-error') || ''
    };

    function showError(message) {
        if (errorBox) errorBox.textContent = String(message || '');
    }

    function refreshCaptcha() {
        if (captchaImage) captchaImage.src = '/file/getVerify?nonce=' + Date.now() + '-' + Math.random();
    }

    function validate() {
        var username = usernameInput ? usernameInput.value.trim() : '';
        var password = passwordInput ? passwordInput.value : '';
        var captcha = captchaInput ? captchaInput.value.trim() : '';
        if (!username) return {error: messages.phoneRequired};
        if (!/^0(?:3|5|7|8|9)[0-9]{8}$/.test(username)) return {error: messages.phoneInvalid};
        if (!password) return {error: messages.passwordRequired};
        if (!captcha) return {error: messages.captchaRequired};
        return {username: username, password: password, captcha: captcha};
    }

    if (captchaImage) {
        captchaImage.addEventListener('click', refreshCaptcha);
        captchaImage.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            refreshCaptcha();
        });
    }
    if (backButton) {
        backButton.addEventListener('click', function (event) {
            if (window.history.length <= 1) return;
            event.preventDefault();
            window.history.back();
        });
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (isSubmitting) return;

        var values = validate();
        if (values.error) {
            showError(values.error);
            return;
        }

        showError('');
        isSubmitting = true;
        if (submitButton) submitButton.disabled = true;
        $.ajax({
            type: 'post',
            url: '/user/register',
            data: {username: values.username, password: values.password, velCode: values.captcha},
            dataType: 'json',
            success: function (response) {
                if (response && response.code === 200) {
                    isRedirecting = true;
                    window.location.href = '/';
                    return;
                }
                showError(response && response.msg ? response.msg : messages.network);
                refreshCaptcha();
            },
            error: function () {
                showError(messages.network);
                refreshCaptcha();
            },
            complete: function () {
                if (isRedirecting) return;
                isSubmitting = false;
                if (submitButton) submitButton.disabled = false;
            }
        });
    });
})(window, document, window.jQuery);
