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

    function requestId(prefix) {
        var requestPrefix = prefix || 'ticket';
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return requestPrefix + '_' + window.crypto.randomUUID();
        }
        var bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var index = 0; index < bytes.length; index++) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        return requestPrefix + '_' + Array.prototype.map.call(bytes, function (value) {
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

    function csrfToken() {
        var value = document.cookie.split('; ').find(function (item) {
            return item.indexOf('XSRF-TOKEN=') === 0;
        });
        return value ? decodeURIComponent(value.slice('XSRF-TOKEN='.length)) : '';
    }

    function jsonOptions(method, body) {
        var headers = {'Accept': 'application/json', 'Content-Type': 'application/json;charset=UTF-8'};
        var token = csrfToken();
        if (token) { headers['X-XSRF-TOKEN'] = token; }
        return {
            method: method, credentials: 'same-origin', headers: headers,
            body: JSON.stringify(body)
        };
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
        var availablePlans = [];
        var plansByCode = {};
        var mandateState = {configured: false, status: 'NONE'};
        var pendingCheckout = null;
        var pendingMandates = {};

        function setStatus(message, error) {
            status.textContent = message || '';
            status.classList.toggle('is-error', Boolean(error));
            status.hidden = !message;
        }

        function setCheckoutStatus(message, error) {
            checkoutStatus.textContent = message || '';
            checkoutStatus.classList.toggle('is-error', Boolean(error));
            checkoutStatus.hidden = !message;
        }

        function requireSuccess(result) {
            if (result.code === 1001) {
                redirectToLogin(root.dataset.loginUrl);
                throw new Error('LOGIN_REDIRECT');
            }
            if (result.code !== 200) {
                var error = new Error('READING_TICKET_API_ERROR');
                error.publicMessage = result.msg;
                error.responseCode = result.code;
                throw error;
            }
            return result.data;
        }

        function addOption(select, value, label) {
            var option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            select.appendChild(option);
            return option;
        }

        function fundingLabel(value) {
            if (value === 'WALLET_XU') { return root.dataset.fundingWallet; }
            if (value === 'VNPAY_RECURRING') { return root.dataset.fundingVnpay; }
            return root.dataset.fundingNone;
        }

        function statusLabel(value) {
            var labels = {
                ACTIVE: root.dataset.statusActive,
                PAST_DUE: root.dataset.statusPastDue,
                PENDING_PRICE_CONSENT: root.dataset.statusPendingPriceConsent,
                CANCEL_AT_PERIOD_END: root.dataset.statusCancelAtPeriodEnd,
                PAUSED: root.dataset.statusPaused,
                CANCELLED: root.dataset.statusCancelled,
                EXPIRED: root.dataset.statusExpired
            };
            return labels[value] || value || '-';
        }

        function fundingSources(plan, includeCurrent) {
            var result = [];
            if (plan && Number(plan.priceXu) > 0) { result.push('WALLET_XU'); }
            if (mandateState.configured && mandateState.status === 'ACTIVE') {
                result.push('VNPAY_RECURRING');
            }
            if (includeCurrent && result.indexOf(includeCurrent) < 0) { result.push(includeCurrent); }
            return result;
        }

        function createFundingControls(parent, plan, initial) {
            var controls = append(parent, 'div', '', 'reading-ticket-renewal-controls');
            var autoLabel = append(controls, 'label', '', 'reading-ticket-checkbox');
            var autoRenew = document.createElement('input');
            autoRenew.type = 'checkbox';
            autoRenew.checked = Boolean(initial && initial.autoRenew);
            autoLabel.appendChild(autoRenew);
            append(autoLabel, 'span', root.dataset.renewalEnable);

            var primaryLabel = append(controls, 'label', root.dataset.renewalPrimary);
            var primary = document.createElement('select');
            primaryLabel.appendChild(primary);
            var fallbackLabel = append(controls, 'label', root.dataset.renewalFallback);
            var fallback = document.createElement('select');
            fallbackLabel.appendChild(fallback);

            function refreshSources() {
                var initialPrimary = initial && initial.primaryFundingSource;
                var sources = fundingSources(plan, initialPrimary);
                primary.replaceChildren();
                fallback.replaceChildren();
                sources.forEach(function (source) {
                    addOption(primary, source, fundingLabel(source));
                });
                if (initialPrimary && sources.indexOf(initialPrimary) >= 0) {
                    primary.value = initialPrimary;
                }
                addOption(fallback, '', root.dataset.fundingNone);
                sources.forEach(function (source) {
                    if (source !== primary.value) { addOption(fallback, source, fundingLabel(source)); }
                });
                if (initial && initial.fallbackFundingSource
                    && initial.fallbackFundingSource !== primary.value) {
                    fallback.value = initial.fallbackFundingSource;
                }
                var enabled = autoRenew.checked && sources.length > 0;
                primary.disabled = !enabled;
                fallback.disabled = !enabled;
                if (autoRenew.checked && sources.length === 0) { autoRenew.checked = false; }
            }

            autoRenew.addEventListener('change', refreshSources);
            primary.addEventListener('change', function () {
                var previous = fallback.value;
                initial = {
                    autoRenew: autoRenew.checked,
                    primaryFundingSource: primary.value,
                    fallbackFundingSource: previous === primary.value ? '' : previous
                };
                refreshSources();
            });
            refreshSources();
            return {autoRenew: autoRenew, primary: primary, fallback: fallback};
        }

        function renderMandateAction(card, plan) {
            var box = append(card, 'div', '', 'reading-ticket-mandate');
            if (!mandateState.configured) {
                append(box, 'small', root.dataset.mandateUnavailable);
                return;
            }
            if (mandateState.status === 'ACTIVE') {
                append(box, 'small', root.dataset.mandateActive, 'reading-ticket-success');
                return;
            }
            if (mandateState.status === 'REVOKE_PENDING') {
                append(box, 'small', root.dataset.mandateRevokePending);
                return;
            }
            if (mandateState.status === 'PENDING' && mandateState.planCode !== plan.planCode) {
                append(box, 'small', root.dataset.mandatePending);
                return;
            }
            var label = mandateState.status === 'PENDING'
                ? root.dataset.mandateContinue : root.dataset.mandateAuthorize;
            var button = append(box, 'button', label);
            button.type = 'button';
            button.addEventListener('click', function () {
                createMandate(plan, button);
            });
        }

        function renderPlans(items) {
            plans.replaceChildren();
            availablePlans = Array.isArray(items) ? items : [];
            plansByCode = {};
            availablePlans.forEach(function (plan) { plansByCode[plan.planCode] = plan; });
            if (availablePlans.length === 0) {
                append(plans, 'p', root.dataset.plansEmpty, 'reading-ticket-empty');
                return;
            }
            availablePlans.forEach(function (plan) {
                var card = append(plans, 'article', '', 'reading-ticket-plan');
                append(card, 'h3', plan.planName || plan.planCode);
                append(card, 'p', plan.priceVnd > 0
                    ? numberFormatter.format(plan.priceVnd) + ' VND'
                    : root.dataset.priceUnavailable);
                if (Number(plan.priceXu) > 0) {
                    append(card, 'p', root.dataset.priceXu + ': ' +
                        numberFormatter.format(plan.priceXu) + ' Xu');
                }
                append(card, 'p', numberFormatter.format(plan.ticketsPerPeriod || 0) + ' ' +
                    root.dataset.unit + ' / ' + numberFormatter.format(plan.periodMonths || 0) + ' ' +
                    root.dataset.monthUnit);
                append(card, 'p', root.dataset.validity + ': ' +
                    numberFormatter.format(plan.ticketValidityDays || 0) + ' ' + root.dataset.dayUnit);
                renderMandateAction(card, plan);
                var controls = createFundingControls(card, plan, null);
                append(card, 'small', root.dataset.renewalHelp, 'reading-ticket-help');
                var actions = append(card, 'div', '', 'reading-ticket-plan-actions');
                if (plan.priceVnd > 0 && availableChannels[4]) {
                    checkoutButton(actions, plan, 4, root.dataset.payVnpay, controls);
                }
                if (plan.priceVnd > 0 && availableChannels[5]) {
                    checkoutButton(actions, plan, 5, root.dataset.payVietqr, controls);
                }
                if (!actions.firstChild) {
                    append(card, 'small', root.dataset.checkoutUnavailable);
                }
            });
        }

        function checkoutButton(parent, plan, channel, label, controls) {
            var button = append(parent, 'button', label);
            button.type = 'button';
            button.addEventListener('click', function () {
                createCheckout(plan, channel, controls, button);
            });
        }

        function loadChannels() {
            return window.fetch(root.dataset.channelsEndpoint, {
                credentials: 'same-origin', headers: {'Accept': 'application/json'}
            }).then(function (response) {
                return response.json();
            }).then(function (items) {
                availableChannels = {};
                (Array.isArray(items) ? items : []).forEach(function (channel) {
                    availableChannels[Number(channel.code)] = channel.enabled === true;
                });
            }).catch(function () {
                availableChannels = {};
            });
        }

        function loadMandateState() {
            return fetchResult(root.dataset.mandateStateEndpoint).then(requireSuccess).then(function (value) {
                mandateState = value || {configured: false, status: 'NONE'};
                return mandateState;
            });
        }

        function createCheckout(plan, channel, controls, button) {
            var autoRenew = controls.autoRenew.checked;
            var payload = {
                planCode: plan.planCode,
                payChannel: channel,
                autoRenew: autoRenew,
                primaryFundingSource: autoRenew ? controls.primary.value : null,
                fallbackFundingSource: autoRenew && controls.fallback.value
                    ? controls.fallback.value : null,
                acceptedPlanVersion: plan.planVersion
            };
            var fingerprint = JSON.stringify(payload);
            if (!pendingCheckout || pendingCheckout.fingerprint !== fingerprint) {
                payload.clientRequestId = requestId('checkout');
                pendingCheckout = {fingerprint: fingerprint, payload: payload};
            }
            button.disabled = true;
            setCheckoutStatus(root.dataset.checkoutCreating, false);
            checkoutQr.replaceChildren();
            fetchResult(root.dataset.checkoutEndpoint,
                jsonOptions('POST', pendingCheckout.payload)).then(function (result) {
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
                setCheckoutStatus(root.dataset.checkoutQrReady, false);
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
                    setCheckoutStatus(root.dataset.checkoutUncertain, true);
                }
            }).then(function () {
                button.disabled = false;
            });
        }

        function throwCheckout(message) {
            setCheckoutStatus(message, true);
            var error = new Error('CHECKOUT_DEFINITIVE');
            throw error;
        }

        function createMandate(plan, button) {
            var pending = pendingMandates[plan.planCode];
            if (!pending && mandateState.status === 'PENDING'
                && mandateState.planCode === plan.planCode && mandateState.clientRequestId) {
                pending = {
                    planCode: plan.planCode,
                    acceptedPlanVersion: mandateState.acceptedPlanVersion,
                    clientRequestId: mandateState.clientRequestId
                };
            }
            if (!pending) {
                pending = {
                    planCode: plan.planCode,
                    acceptedPlanVersion: plan.planVersion,
                    clientRequestId: requestId('mandate')
                };
            }
            pendingMandates[plan.planCode] = pending;
            button.disabled = true;
            setCheckoutStatus(root.dataset.mandateCreating, false);
            fetchResult(root.dataset.mandateEndpoint, jsonOptions('POST', pending))
                .then(function (result) {
                    if (result.code === 1001) {
                        redirectToLogin(root.dataset.loginUrl);
                        return;
                    }
                    if (result.code !== 200 || !result.data) {
                        if (result.code === 7112) { delete pendingMandates[plan.planCode]; }
                        throwMandate(result.msg || root.dataset.mandateFailed);
                        return;
                    }
                    submitMandateForm(result.data);
                }).catch(function (error) {
                    if (error.message !== 'MANDATE_DEFINITIVE') {
                        setCheckoutStatus(root.dataset.mandateUncertain, true);
                    }
                    button.disabled = false;
                });
        }

        function throwMandate(message) {
            setCheckoutStatus(message, true);
            var error = new Error('MANDATE_DEFINITIVE');
            throw error;
        }

        function submitMandateForm(data) {
            var paymentUrl;
            try { paymentUrl = new URL(data.paymentUrl); } catch (ignored) { paymentUrl = null; }
            if (!paymentUrl || paymentUrl.protocol !== 'https:'
                || !/^[0-9]{8,18}$/.test(data.ispTxnId || '')
                || !/^[A-Za-z0-9]{8}$/.test(data.tmnCode || '')
                || typeof data.dataKey !== 'string' || !data.dataKey || data.dataKey.length > 2000) {
                throwMandate(root.dataset.mandateFailed);
                return;
            }
            var form = document.createElement('form');
            form.method = 'POST';
            form.action = paymentUrl.toString();
            form.hidden = true;
            [['ispTxnId', data.ispTxnId], ['tmnCode', data.tmnCode], ['dataKey', data.dataKey]]
                .forEach(function (entry) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = entry[0];
                    input.value = entry[1];
                    form.appendChild(input);
                });
            document.body.appendChild(form);
            setCheckoutStatus(root.dataset.mandateRedirecting, false);
            form.submit();
        }

        function renderCurrent(subscription) {
            current.replaceChildren();
            if (!subscription) {
                append(current, 'p', root.dataset.currentNone, 'reading-ticket-empty');
                grants.replaceChildren();
                append(grants, 'p', root.dataset.grantsEmpty, 'reading-ticket-empty');
                return Promise.resolve();
            }
            var plan = plansByCode[subscription.planCode] || {
                planCode: subscription.planCode,
                planVersion: subscription.planVersion,
                priceXu: subscription.priceXu
            };
            append(current, 'strong', subscription.planCode || '-');
            append(current, 'span', root.dataset.currentStatus + ': ' + statusLabel(subscription.status));
            append(current, 'span', root.dataset.nextGrant + ': ' + formatDate(subscription.nextGrantAt));
            append(current, 'span', root.dataset.nextRenewal + ': ' + formatDate(subscription.nextRenewalAt));
            append(current, 'span', root.dataset.endAt + ': ' + formatDate(subscription.endAt));
            append(current, 'span', root.dataset.renewalCurrent + ': ' +
                (subscription.autoRenew ? root.dataset.renewalOn : root.dataset.renewalOff));
            if (subscription.autoRenew) {
                append(current, 'span', root.dataset.renewalPrimary + ': ' +
                    fundingLabel(subscription.primaryFundingSource));
                append(current, 'span', root.dataset.renewalFallback + ': ' +
                    fundingLabel(subscription.fallbackFundingSource));
            }

            var controls = createFundingControls(current, plan, subscription);
            var actions = append(current, 'div', '', 'reading-ticket-current-actions');
            var save = append(actions, 'button', root.dataset.renewalSave);
            save.type = 'button';
            save.addEventListener('click', function () {
                updateRenewal(subscription, controls, save);
            });
            if (Number(plan.planVersion) > Number(subscription.acceptedPlanVersion)) {
                var consent = append(actions, 'button', root.dataset.consentAction);
                consent.type = 'button';
                consent.addEventListener('click', function () {
                    consentPrice(subscription, plan, consent);
                });
                append(current, 'small', root.dataset.consentRequired, 'reading-ticket-warning');
            }
            if (subscription.status !== 'CANCEL_AT_PERIOD_END') {
                var cancel = append(actions, 'button', root.dataset.cancelAction,
                    'reading-ticket-danger');
                cancel.type = 'button';
                cancel.addEventListener('click', function () {
                    cancelSubscription(subscription, cancel);
                });
            }
            return fetchResult(root.dataset.grantsEndpoint.replace('{id}', subscription.id) + '?limit=50')
                .then(requireSuccess).then(renderGrants);
        }

        function updateRenewal(subscription, controls, button) {
            var autoRenew = controls.autoRenew.checked;
            button.disabled = true;
            setStatus(root.dataset.renewalSaving, false);
            fetchResult(root.dataset.renewalSettingsEndpoint.replace('{id}', subscription.id),
                jsonOptions('PATCH', {
                    expectedVersion: subscription.version,
                    autoRenew: autoRenew,
                    primaryFundingSource: autoRenew ? controls.primary.value : null,
                    fallbackFundingSource: autoRenew && controls.fallback.value
                        ? controls.fallback.value : null,
                    acceptedPlanVersion: subscription.acceptedPlanVersion
                })).then(requireSuccess).then(function (updated) {
                    setStatus(root.dataset.renewalSaved, false);
                    return loadMandateState().then(function () {
                        renderPlans(availablePlans);
                        return renderCurrent(updated);
                    });
                }).catch(function (error) {
                    if (error.message !== 'LOGIN_REDIRECT') {
                        setStatus(error.publicMessage || root.dataset.renewalFailed, true);
                    }
                }).then(function () { button.disabled = false; });
        }

        function consentPrice(subscription, plan, button) {
            button.disabled = true;
            setStatus(root.dataset.consentSaving, false);
            fetchResult(root.dataset.priceConsentEndpoint.replace('{id}', subscription.id),
                jsonOptions('POST', {
                    expectedVersion: subscription.version,
                    acceptedPlanVersion: plan.planVersion,
                    clientRequestId: requestId('consent')
                })).then(requireSuccess).then(function (updated) {
                    setStatus(root.dataset.consentSuccess, false);
                    return renderCurrent(updated);
                }).catch(function (error) {
                    if (error.message !== 'LOGIN_REDIRECT') {
                        setStatus(error.publicMessage || root.dataset.renewalFailed, true);
                    }
                }).then(function () { button.disabled = false; });
        }

        function cancelSubscription(subscription, button) {
            if (!window.confirm(root.dataset.cancelConfirm)) { return; }
            button.disabled = true;
            setStatus(root.dataset.cancelSaving, false);
            fetchResult(root.dataset.cancelEndpoint.replace('{id}', subscription.id),
                jsonOptions('POST', {expectedVersion: subscription.version}))
                .then(requireSuccess).then(function (updated) {
                    setStatus(root.dataset.cancelSuccess, false);
                    return loadMandateState().then(function () {
                        renderPlans(availablePlans);
                        return renderCurrent(updated);
                    });
                }).catch(function (error) {
                    if (error.message !== 'LOGIN_REDIRECT') {
                        setStatus(error.publicMessage || root.dataset.renewalFailed, true);
                    }
                }).then(function () { button.disabled = false; });
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
                loadChannels(),
                fetchResult(root.dataset.plansEndpoint).then(requireSuccess),
                fetchResult(root.dataset.currentEndpoint).then(requireSuccess),
                loadMandateState(),
                loadLedgerHistory(),
                loadLotHistory()
            ]);
        }).then(function (results) {
            renderPlans(results[1]);
            return renderCurrent(results[2]);
        }).then(function () {
            var mandateResult = new URLSearchParams(window.location.search).get('mandate');
            if (mandateResult === 'success') {
                setStatus(root.dataset.mandateSuccess, false);
            } else if (mandateResult === 'processing') {
                setStatus(root.dataset.mandateProcessing, false);
            } else if (mandateResult === 'failed' || mandateResult === 'cancelled') {
                setStatus(root.dataset.mandateFailed, true);
            } else {
                setStatus('', false);
            }
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
            fetchResult(root.dataset.endpoint,
                jsonOptions('POST', {clientRequestId: pendingRequestId})).then(function (result) {
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
