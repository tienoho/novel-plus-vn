(function (window, document) {
    'use strict';

    if (!window.fetch) { return; }

    var numberFormatter = new Intl.NumberFormat('vi-VN');
    var dateFormatter = new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh'
    });

    function redirectToLogin(loginUrl) {
        window.location.href = loginUrl + '?originUrl=' +
            encodeURIComponent(window.location.pathname + window.location.search);
    }

    function requestId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 'ticket_' + window.crypto.randomUUID();
        }
        var bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var index = 0; index < bytes.length; index++) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        return 'ticket_' + Array.prototype.map.call(bytes, function (value) {
            return value.toString(16).padStart(2, '0');
        }).join('');
    }

    function fetchResult(url, options) {
        return window.fetch(url, options || {
            credentials: 'same-origin', headers: {'Accept': 'application/json'}
        }).then(function (response) {
            return response.json();
        });
    }

    function append(parent, tagName, value, className) {
        var node = document.createElement(tagName);
        node.textContent = value == null ? '' : String(value);
        if (className) { node.className = className; }
        parent.appendChild(node);
        return node;
    }

    function formatDate(value) {
        if (!value) { return '-'; }
        var date = new Date(value);
        return Number.isNaN(date.getTime()) ? '-' : dateFormatter.format(date);
    }

    function initAccountPage(root) {
        var balance = document.getElementById('readingTicketBalance');
        var status = document.getElementById('readingTicketAccountStatus');
        var current = document.getElementById('readingTicketCurrent');
        var plans = document.getElementById('readingTicketPlans');
        var grants = document.getElementById('readingTicketGrants');
        var ledgerHistory = document.getElementById('readingTicketLedgerHistory');
        var lotHistory = document.getElementById('readingTicketLotHistory');
        var checkoutStatus = document.getElementById('readingTicketCheckoutStatus');
        var checkoutQr = document.getElementById('readingTicketCheckoutQr');
        var availableChannels = {};
        var pendingCheckout = null;

        function setStatus(message, error) {
            status.textContent = message || '';
            status.classList.toggle('is-error', Boolean(error));
            status.hidden = !message;
        }

        function requireSuccess(result) {
            if (result.code === 1001) {
                redirectToLogin(root.dataset.loginUrl);
                throw new Error('LOGIN_REDIRECT');
            }
            if (result.code !== 200) {
                var error = new Error('READING_TICKET_API_ERROR');
                error.publicMessage = result.msg;
                throw error;
            }
            return result.data;
        }

        function renderPlans(items) {
            plans.replaceChildren();
            if (!Array.isArray(items) || items.length === 0) {
                append(plans, 'p', root.dataset.plansEmpty, 'reading-ticket-empty');
                return;
            }
            items.forEach(function (plan) {
                var card = append(plans, 'article', '', 'reading-ticket-plan');
                append(card, 'h3', plan.planName || plan.planCode);
                append(card, 'p', plan.priceVnd > 0
                    ? numberFormatter.format(plan.priceVnd) + ' VND'
                    : root.dataset.priceUnavailable);
                append(card, 'p', numberFormatter.format(plan.ticketsPerPeriod || 0) + ' ' +
                    root.dataset.unit + ' / ' + numberFormatter.format(plan.periodMonths || 0) + ' ' +
                    root.dataset.monthUnit);
                append(card, 'p', root.dataset.validity + ': ' +
                    numberFormatter.format(plan.ticketValidityDays || 0) + ' ' + root.dataset.dayUnit);
                var actions = append(card, 'div', '', 'reading-ticket-plan-actions');
                if (plan.priceVnd > 0 && availableChannels[4]) {
                    checkoutButton(actions, plan.planCode, 4, root.dataset.payVnpay);
                }
                if (plan.priceVnd > 0 && availableChannels[5]) {
                    checkoutButton(actions, plan.planCode, 5, root.dataset.payVietqr);
                }
                if (!actions.firstChild) {
                    append(card, 'small', root.dataset.checkoutUnavailable);
                }
            });
        }

        function checkoutButton(parent, planCode, channel, label) {
            var button = append(parent, 'button', label);
            button.type = 'button';
            button.addEventListener('click', function () {
                createCheckout(planCode, channel, button);
            });
        }

        function loadChannels() {
            return window.fetch(root.dataset.channelsEndpoint, {
                credentials: 'same-origin', headers: {'Accept': 'application/json'}
            }).then(function (response) {
                return response.json();
            }).then(function (items) {
                (Array.isArray(items) ? items : []).forEach(function (channel) {
                    availableChannels[Number(channel.code)] = channel.enabled === true;
                });
            }).catch(function () {
                availableChannels = {};
            });
        }

        function createCheckout(planCode, channel, button) {
            if (!pendingCheckout || pendingCheckout.planCode !== planCode
                || pendingCheckout.payChannel !== channel) {
                pendingCheckout = {
                    planCode: planCode, payChannel: channel, clientRequestId: requestId()
                };
            }
            button.disabled = true;
            checkoutStatus.textContent = root.dataset.checkoutCreating;
            checkoutStatus.classList.remove('is-error');
            checkoutStatus.hidden = false;
            checkoutQr.replaceChildren();
            fetchResult(root.dataset.checkoutEndpoint, {
                method: 'POST', credentials: 'same-origin',
                headers: {'Accept': 'application/json', 'Content-Type': 'application/json;charset=UTF-8'},
                body: JSON.stringify(pendingCheckout)
            }).then(function (result) {
                if (result.code === 1001) {
                    redirectToLogin(root.dataset.loginUrl);
                    return;
                }
                if (result.code !== 200 || !result.data) {
                    pendingCheckout = null;
                    throwCheckout(result.msg || root.dataset.checkoutFailed);
                    return;
                }
                if (result.data.paymentUrl) {
                    window.location.href = result.data.paymentUrl;
                    return;
                }
                checkoutStatus.textContent = root.dataset.checkoutQrReady;
                if (result.data.qrImageUrl) {
                    var image = document.createElement('img');
                    image.src = result.data.qrImageUrl;
                    image.alt = root.dataset.checkoutQrAlt;
                    checkoutQr.appendChild(image);
                }
                if (result.data.qrCodeData) {
                    append(checkoutQr, 'code', result.data.qrCodeData);
                }
            }).catch(function (error) {
                if (error.message !== 'CHECKOUT_DEFINITIVE') {
                    checkoutStatus.textContent = root.dataset.checkoutUncertain;
                    checkoutStatus.classList.add('is-error');
                }
            }).then(function () {
                button.disabled = false;
            });
        }

        function throwCheckout(message) {
            checkoutStatus.textContent = message;
            checkoutStatus.classList.add('is-error');
            var error = new Error('CHECKOUT_DEFINITIVE');
            throw error;
        }

        function renderCurrent(subscription) {
            current.replaceChildren();
            if (!subscription) {
                append(current, 'p', root.dataset.currentNone, 'reading-ticket-empty');
                grants.replaceChildren();
                append(grants, 'p', root.dataset.grantsEmpty, 'reading-ticket-empty');
                return Promise.resolve();
            }
            var rawStatus = subscription.status || '';
            var statusKey = 'status' + rawStatus.charAt(0) + rawStatus.slice(1).toLowerCase();
            append(current, 'strong', subscription.planCode || '-');
            append(current, 'span', root.dataset.currentStatus + ': ' +
                (root.dataset[statusKey] || rawStatus || '-'));
            append(current, 'span', root.dataset.nextGrant + ': ' + formatDate(subscription.nextGrantAt));
            append(current, 'span', root.dataset.endAt + ': ' + formatDate(subscription.endAt));
            return fetchResult(root.dataset.grantsEndpoint.replace('{id}', subscription.id) + '?limit=50')
                .then(requireSuccess)
                .then(renderGrants);
        }

        function renderGrants(items) {
            grants.replaceChildren();
            if (!Array.isArray(items) || items.length === 0) {
                append(grants, 'p', root.dataset.grantsEmpty, 'reading-ticket-empty');
                return;
            }
            items.forEach(function (grant) {
                var card = append(grants, 'article', '', 'reading-ticket-grant');
                append(card, 'strong', '+' + numberFormatter.format(grant.ticketAmount || 0) +
                    ' ' + root.dataset.unit);
                append(card, 'span', root.dataset.period + ': ' + formatDate(grant.periodStart) +
                    ' – ' + formatDate(grant.periodEnd));
                append(card, 'span', root.dataset.expiresAt + ': ' + formatDate(grant.ticketExpireAt));
            });
        }

        function renderLedgerHistory(page) {
            ledgerHistory.replaceChildren();
            var items = page && Array.isArray(page.items) ? page.items : [];
            if (items.length === 0) {
                append(ledgerHistory, 'p', root.dataset.ledgerEmpty, 'reading-ticket-empty');
                return;
            }
            items.forEach(function (entry) {
                var row = append(ledgerHistory, 'article', '', 'reading-ticket-ledger-entry');
                var amount = entry.amount || 0;
                append(row, 'strong', (amount > 0 ? '+' : '') + numberFormatter.format(amount) +
                    ' ' + root.dataset.unit);
                append(row, 'span', entry.entryType || '-');
                append(row, 'span', numberFormatter.format(entry.balanceAfter || 0) + ' ' + root.dataset.unit);
            });
        }

        function loadLedgerHistory() {
            return fetchResult(root.dataset.ledgerEndpoint + '?page=1&pageSize=20')
                .then(requireSuccess).then(renderLedgerHistory);
        }

        function renderLotHistory(page) {
            lotHistory.replaceChildren();
            var items = page && Array.isArray(page.items) ? page.items : [];
            if (items.length === 0) {
                append(lotHistory, 'p', root.dataset.lotsEmpty, 'reading-ticket-empty');
                return;
            }
            items.forEach(function (lot) {
                var row = append(lotHistory, 'article', '', 'reading-ticket-lot-entry');
                append(row, 'strong', numberFormatter.format(lot.remainingAmount || 0) + ' / ' +
                    numberFormatter.format(lot.grantedAmount || 0) + ' ' + root.dataset.unit);
                append(row, 'span', root.dataset.expiresAt + ': ' + formatDate(lot.expireAt));
            });
        }

        function loadLotHistory() {
            return fetchResult(root.dataset.lotsEndpoint + '?page=1&pageSize=20')
                .then(requireSuccess).then(renderLotHistory);
        }

        setStatus(root.dataset.loading, false);
        fetchResult(root.dataset.accountEndpoint).then(requireSuccess).then(function (account) {
            balance.textContent = numberFormatter.format(account.availableBalance || 0);
            return Promise.all([
                Promise.all([
                    loadChannels(), fetchResult(root.dataset.plansEndpoint).then(requireSuccess)
                ]).then(function (results) { renderPlans(results[1]); }),
                fetchResult(root.dataset.currentEndpoint).then(requireSuccess).then(renderCurrent),
                loadLedgerHistory(),
                loadLotHistory()
            ]);
        }).then(function () {
            setStatus('', false);
        }).catch(function (error) {
            if (error.message !== 'LOGIN_REDIRECT') {
                setStatus(error.publicMessage || root.dataset.loadFailed, true);
            }
        });
    }

    function initUnlock(root) {
        var button = root.querySelector('[data-reading-ticket-action]');
        var balance = root.querySelector('[data-reading-ticket-balance]');
        var status = root.querySelector('[data-reading-ticket-status]');
        var pendingRequestId = null;
        var authenticated = true;

        function setStatus(message, error) {
            status.textContent = message || '';
            status.classList.toggle('is-error', Boolean(error));
            status.hidden = !message;
        }

        function restoreButton() {
            button.disabled = false;
            button.textContent = authenticated ? root.dataset.action : root.dataset.loginAction;
        }

        fetchResult(root.dataset.accountEndpoint).then(function (result) {
            if (result.code === 7101) { return; }
            root.hidden = false;
            if (result.code === 1001) {
                authenticated = false;
                balance.textContent = '–';
                setStatus(root.dataset.loginRequired, false);
                restoreButton();
                return;
            }
            if (result.code !== 200 || !result.data) {
                button.disabled = true;
                setStatus(result.msg || root.dataset.loadFailed, true);
                return;
            }
            balance.textContent = numberFormatter.format(result.data.availableBalance || 0);
            restoreButton();
        }).catch(function () {
            root.hidden = false;
            button.disabled = true;
            setStatus(root.dataset.loadFailed, true);
        });

        button.addEventListener('click', function () {
            if (!authenticated) {
                redirectToLogin(root.dataset.loginUrl);
                return;
            }
            if (!pendingRequestId) { pendingRequestId = requestId(); }
            button.disabled = true;
            button.textContent = root.dataset.unlocking;
            setStatus('', false);
            fetchResult(root.dataset.endpoint, {
                method: 'POST', credentials: 'same-origin',
                headers: {'Accept': 'application/json', 'Content-Type': 'application/json;charset=UTF-8'},
                body: JSON.stringify({clientRequestId: pendingRequestId})
            }).then(function (result) {
                if (result.code === 1001) {
                    redirectToLogin(root.dataset.loginUrl);
                    return;
                }
                if (result.code === 200 && result.data) {
                    window.location.reload();
                    return;
                }
                pendingRequestId = null;
                setStatus(result.msg || root.dataset.unlockFailed, true);
                restoreButton();
            }).catch(function () {
                setStatus(root.dataset.unlockUncertain, true);
                restoreButton();
            });
        });
    }

    var accountRoot = document.getElementById('readingTicketAccountApp');
    if (accountRoot) { initAccountPage(accountRoot); }
    var unlockRoot = document.getElementById('readingTicketUnlock');
    if (unlockRoot) { initUnlock(unlockRoot); }
}(window, document));
