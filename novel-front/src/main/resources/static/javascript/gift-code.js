(function (window, document) {
    'use strict';

    var root = document.getElementById('giftCodeApp');
    if (!root || !window.fetch) { return; }

    var form = document.getElementById('giftCodeForm');
    var input = document.getElementById('giftCodeInput');
    var submit = document.getElementById('giftCodeSubmit');
    var status = document.getElementById('giftCodeStatus');
    var historyStatus = document.getElementById('giftCodeHistoryStatus');
    var historyList = document.getElementById('giftCodeHistoryList');
    var historyPrevious = document.getElementById('giftCodeHistoryPrevious');
    var historyNext = document.getElementById('giftCodeHistoryNext');
    var pendingRequest = null;
    var historyPage = 1;
    var numberFormatter = new Intl.NumberFormat('vi-VN');
    var dateFormatter = new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh'
    });

    function requestId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 'gift_' + window.crypto.randomUUID();
        }
        var bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var index = 0; index < bytes.length; index++) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        return 'gift_' + Array.prototype.map.call(bytes, function (value) {
            return value.toString(16).padStart(2, '0');
        }).join('');
    }

    function setStatus(message, error) {
        status.textContent = message || '';
        status.classList.toggle('is-error', Boolean(error));
        status.hidden = !message;
    }

    function format(template, values) {
        return String(template || '').replace(/\{([a-zA-Z]+)}/g, function (match, key) {
            return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match;
        });
    }

    function rewardUnit(type) {
        return type === 'READING_TICKET' ? root.dataset.ticketUnit : root.dataset.xuUnit;
    }

    function redirectToLogin() {
        window.location.href = root.dataset.loginUrl + '?originUrl=' +
            encodeURIComponent(window.location.pathname + window.location.search);
    }

    function append(parent, tagName, value) {
        var node = document.createElement(tagName);
        node.textContent = value == null ? '' : String(value);
        parent.appendChild(node);
        return node;
    }

    function renderHistory(data) {
        while (historyList.firstChild) { historyList.removeChild(historyList.firstChild); }
        var items = Array.isArray(data.items) ? data.items : [];
        if (items.length === 0) {
            historyStatus.textContent = root.dataset.historyEmpty;
        } else {
            historyStatus.textContent = '';
            items.forEach(function (entry) {
                var item = document.createElement('article');
                item.className = 'gift-code-history-item';
                append(item, 'strong', numberFormatter.format(entry.rewardAmount || 0) +
                    ' ' + rewardUnit(entry.rewardType));
                append(item, 'span', root.dataset.historyCampaign + ': ' +
                    (entry.campaignName || '-'));
                append(item, 'span', root.dataset.historyCode + ': ••••-' +
                    (entry.codeHint || '----'));
                append(item, 'span', root.dataset.historyReceipt + ': #' + entry.receiptId +
                    ' · ' + dateFormatter.format(new Date(entry.redeemedAt)));
                historyList.appendChild(item);
            });
        }
        historyPage = Number(data.page) || 1;
        historyPrevious.disabled = historyPage <= 1;
        historyNext.disabled = historyPage * (Number(data.pageSize) || 20) >=
            Number(data.total || 0);
    }

    function loadHistory(requestedPage) {
        historyPrevious.disabled = true;
        historyNext.disabled = true;
        historyStatus.textContent = root.dataset.historyLoading;
        window.fetch(root.dataset.historyEndpoint + '?page=' +
            encodeURIComponent(requestedPage) + '&limit=20', {
            credentials: 'same-origin', headers: {'Accept': 'application/json'}
        }).then(function (response) {
            return response.json();
        }).then(function (result) {
            if (result.code === 1001) {
                redirectToLogin();
                return;
            }
            if (result.code !== 200 || !result.data) {
                throw new Error('HISTORY_API_ERROR');
            }
            renderHistory(result.data);
        }).catch(function () {
            historyStatus.textContent = root.dataset.historyFailed;
        });
    }

    function redeem(event) {
        event.preventDefault();
        var code = input.value.trim().toUpperCase();
        if (code.replace(/[\s-]/g, '').length < 16) {
            setStatus(root.dataset.invalid, true);
            input.focus();
            return;
        }
        if (!pendingRequest || pendingRequest.code !== code) {
            pendingRequest = {code: code, clientRequestId: requestId()};
        }
        submit.disabled = true;
        submit.textContent = root.dataset.redeeming;
        setStatus('', false);

        window.fetch(root.dataset.endpoint, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json;charset=UTF-8'
            },
            body: JSON.stringify(pendingRequest)
        }).then(function (response) {
            return response.json();
        }).then(function (result) {
            if (result.code === 1001) {
                redirectToLogin();
                return;
            }
            if (result.code !== 200 || !result.data) {
                pendingRequest = null;
                setStatus(result.msg || root.dataset.failed, true);
                return;
            }
            var data = result.data;
            var message = data.status === 'ALREADY_REDEEMED'
                ? root.dataset.already : root.dataset.success;
            setStatus(format(message, {
                amount: numberFormatter.format(data.rewardAmount || 0),
                unit: rewardUnit(data.rewardType)
            }), false);
            pendingRequest = null;
            input.value = '';
            loadHistory(1);
        }).catch(function () {
            setStatus(root.dataset.networkError, true);
        }).then(function () {
            submit.disabled = false;
            submit.textContent = root.dataset.action;
        });
    }

    form.addEventListener('submit', redeem);
    historyPrevious.addEventListener('click', function () {
        loadHistory(Math.max(1, historyPage - 1));
    });
    historyNext.addEventListener('click', function () {
        loadHistory(historyPage + 1);
    });
    loadHistory(1);
}(window, document));
