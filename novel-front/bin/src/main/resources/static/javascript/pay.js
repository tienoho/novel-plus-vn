(function (window, document, $) {
    'use strict';

    if (!$) return;

    var form = document.getElementById('payform');
    var amountList = document.getElementById('ulZFWX') || document.getElementById('payAmount');
    if (!form || !amountList) return;

    var amountInput = document.getElementById('pValue');
    var submitButton = document.getElementById('paySubmit');
    var backButton = document.getElementById('payBack');
    var total = document.getElementById('showTotal');
    var remark = document.getElementById('showRemark');
    var isSubmitting = false;
    var isRedirecting = false;
    var messages = {
        network: form.getAttribute('data-network-error') || '',
        unavailable: form.getAttribute('data-unavailable') || '',
        invalidAmount: form.getAttribute('data-invalid-amount') || '',
        invalidRedirect: form.getAttribute('data-invalid-redirect') || ''
    };

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') {
            window.novelAlertText(String(message || ''));
        } else if (window.layer && typeof window.layer.alert === 'function') {
            window.layer.alert(String(message || ''));
        }
    }

    function redirectToLogin() {
        window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
    }

    function paymentOption(target) {
        var option = target;
        while (option && option !== amountList) {
            if (option.hasAttribute('data-amount')) return option;
            option = option.parentElement;
        }
        return null;
    }

    function validAmount(option) {
        var amount = option ? option.getAttribute('data-amount') : '';
        return /^[1-9]\d*$/.test(amount) ? Number(amount) : 0;
    }

    function selectAmount(option) {
        var amount = validAmount(option);
        if (!amount) return;

        var options = amountList.querySelectorAll('[data-amount]');
        for (var i = 0; i < options.length; i++) {
            options[i].classList.toggle('on', options[i] === option);
            options[i].setAttribute('aria-pressed', options[i] === option ? 'true' : 'false');
        }
        amountInput.value = String(amount);
        if (total) total.textContent = new Intl.NumberFormat('vi-VN').format(amount) + ' VND';
        if (remark) {
            var receive = option.querySelector('.pay_mn');
            remark.textContent = receive ? receive.textContent : '';
        }
    }

    function parseError(xhr) {
        if (xhr && xhr.responseJSON && xhr.responseJSON.msg) return xhr.responseJSON.msg;
        if (xhr && xhr.responseText) {
            try {
                var response = JSON.parse(xhr.responseText);
                if (response && response.msg) return response.msg;
            } catch (ignored) {
                return messages.network;
            }
        }
        return messages.network;
    }

    function navigateToPayment(value) {
        try {
            var url = new URL(String(value || ''), window.location.origin);
            if (url.protocol !== 'https:' && url.protocol !== 'http:') throw new Error('invalid protocol');
            isRedirecting = true;
            window.location.assign(url.href);
        } catch (ignored) {
            showAlert(messages.invalidRedirect);
        }
    }

    function loadUser() {
        $.ajax({
            type: 'get',
            url: '/user/userInfo',
            dataType: 'json',
            success: function (response) {
                if (response.code === 200 && response.data) {
                    var userName = document.getElementById('my_name');
                    var accountBalance = document.getElementById('accountBalance');
                    if (userName) userName.textContent = response.data.nickName || response.data.username || '';
                    if (accountBalance) {
                        accountBalance.textContent = response.data.accountBalance == null
                            ? '0' : String(response.data.accountBalance);
                    }
                } else if (response.code === 1001) {
                    redirectToLogin();
                } else {
                    showAlert(response.msg);
                }
            },
            error: function () {
                showAlert(messages.network);
            }
        });
    }

    amountList.addEventListener('click', function (event) {
        selectAmount(paymentOption(event.target));
    });
    amountList.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        var option = paymentOption(event.target);
        if (!option) return;
        event.preventDefault();
        selectAmount(option);
    });
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
        if (form.getAttribute('data-enabled') !== 'true') {
            showAlert(messages.unavailable);
            return;
        }

        var selected = amountList.querySelector('[data-amount].on');
        var amount = validAmount(selected);
        if (!amount || String(amount) !== amountInput.value) {
            showAlert(messages.invalidAmount);
            return;
        }

        isSubmitting = true;
        if (submitButton) submitButton.disabled = true;
        $.ajax({
            type: 'post',
            url: form.action,
            data: {payAmount: amount},
            dataType: 'json',
            success: function (response) {
                if (response && response.code === 200) navigateToPayment(response.paymentUrl);
                else showAlert(response && response.msg ? response.msg : messages.network);
            },
            error: function (xhr) {
                if (xhr && xhr.status === 401) redirectToLogin();
                else showAlert(parseError(xhr));
            },
            complete: function () {
                if (isRedirecting) return;
                isSubmitting = false;
                if (submitButton) submitButton.disabled = false;
            }
        });
    });

    loadUser();
})(window, document, window.jQuery);
