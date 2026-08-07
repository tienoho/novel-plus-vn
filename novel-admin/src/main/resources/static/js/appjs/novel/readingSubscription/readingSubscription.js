(function () {
    'use strict';
    var base = '/novel/readingSubscription';
    var messages = window.ReadingSubscriptionI18n || {};
    var permissions = window.ReadingSubscriptionPermissions || {};
    var plans = [];
    var reviewPage = 1;
    var reviewPageSize = 20;
    var reviewTotalPages = 1;

    function textCell(row, value) {
        var cell = document.createElement('td');
        cell.textContent = value == null ? '' : String(value);
        row.appendChild(cell);
    }

    function actionButton(label, css, handler) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-xs ' + css;
        button.textContent = label;
        button.addEventListener('click', handler);
        return button;
    }

    function statusLabel(status) {
        var labels = {
            DRAFT: messages.statusDraft,
            ACTIVE: messages.statusActive,
            PAUSED: messages.statusPaused,
            CANCELLED: messages.statusCancelled,
            EXPIRED: messages.statusExpired,
            RETIRED: messages.statusRetired,
            PAID_REVIEW: messages.statusPaidReview,
            REFUND_PENDING: messages.statusRefundPending,
            PROCESSING: messages.statusProcessing,
            PROVIDER_PENDING: messages.statusProviderPending,
            RETRY_WAIT: messages.statusRetryWait,
            FAILED: messages.statusFailed,
            GRACE_EXPIRED: messages.statusGraceExpired
        };
        return labels[status] || status;
    }

    function showError(xhr) {
        var message = xhr && xhr.responseJSON && xhr.responseJSON.msg;
        layer.msg(message || messages.requestFailed);
    }

    function loadPlans() {
        $.get(base + '/plans').done(function (response) {
            plans = response.data || [];
            renderPlans();
        }).fail(showError);
    }

    function renderPlans() {
        var body = document.querySelector('#plansTable tbody');
        while (body.firstChild) body.removeChild(body.firstChild);
        plans.forEach(function (plan) {
            var row = document.createElement('tr');
            textCell(row, plan.planCode);
            textCell(row, plan.planName);
            textCell(row, Number(plan.priceVnd || 0).toLocaleString('vi-VN') + ' VND');
            textCell(row, plan.ticketsPerPeriod);
            textCell(row, plan.periodMonths);
            textCell(row, plan.ticketValidityDays);
            textCell(row, statusLabel(plan.status));
            var actions = document.createElement('td');
            if (permissions.config && plan.status !== 'RETIRED') {
                actions.appendChild(actionButton(messages.edit, 'btn-info', function () {
                    fillPlan(plan);
                }));
                if (plan.status === 'DRAFT') {
                    actions.appendChild(document.createTextNode(' '));
                    actions.appendChild(actionButton(messages.activate, 'btn-success', function () {
                        changeStatus(plan, 'ACTIVE');
                    }));
                }
                actions.appendChild(document.createTextNode(' '));
                actions.appendChild(actionButton(messages.retire, 'btn-danger', function () {
                    changeStatus(plan, 'RETIRED');
                }));
            }
            row.appendChild(actions);
            body.appendChild(row);
        });
    }

    function fillPlan(plan) {
        $('#planId').val(plan.id);
        $('#planVersion').val(plan.version);
        $('#planCode').val(plan.planCode).prop('readonly', true);
        $('#planName').val(plan.planName);
        $('#priceVnd').val(plan.priceVnd);
        $('#ticketsPerPeriod').val(plan.ticketsPerPeriod);
        $('#periodMonths').val(plan.periodMonths);
        $('#ticketValidityDays').val(plan.ticketValidityDays);
    }

    function resetPlan() {
        document.getElementById('planForm').reset();
        $('#planId,#planVersion').val('');
        $('#planCode').prop('readonly', false);
    }

    function payChannelLabel(channel) {
        if (Number(channel) === 4) return messages.payVnpay;
        if (Number(channel) === 5) return messages.payVietQr;
        return String(channel == null ? '' : channel);
    }

    function formatDate(value) {
        if (!value) return '';
        var date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('vi-VN');
    }

    function pageInfo(page, totalPages, total) {
        return String(messages.pageInfo || 'Trang {0}/{1} · {2} giao dịch')
            .replace('{0}', page).replace('{1}', totalPages).replace('{2}', total);
    }

    function loadPurchaseReviews() {
        if (!permissions.review) return;
        $.get(base + '/purchases', {
            status: $('#purchaseReviewStatus').val(),
            page: reviewPage,
            pageSize: reviewPageSize
        }).done(function (response) {
            renderPurchaseReviews(response.data || {});
        }).fail(showError);
    }

    function renderPurchaseReviews(result) {
        var body = document.querySelector('#purchaseReviewsTable tbody');
        while (body.firstChild) body.removeChild(body.firstChild);
        var items = result.items || [];
        if (!items.length) {
            var emptyRow = document.createElement('tr');
            var emptyCell = document.createElement('td');
            emptyCell.colSpan = 9;
            emptyCell.textContent = messages.emptyReviews;
            emptyRow.appendChild(emptyCell);
            body.appendChild(emptyRow);
        }
        items.forEach(function (purchase) {
            var row = document.createElement('tr');
            textCell(row, purchase.outTradeNo);
            textCell(row, purchase.userId);
            textCell(row, purchase.planNameSnapshot + ' (' + purchase.planCodeSnapshot + ')');
            textCell(row, Number(purchase.priceVndSnapshot || 0).toLocaleString('vi-VN') + ' VND');
            textCell(row, payChannelLabel(purchase.payChannel));
            textCell(row, statusLabel(purchase.status));
            textCell(row, formatDate(purchase.settledAt));
            textCell(row, purchase.version);
            var actions = document.createElement('td');
            if (purchase.status === 'PAID_REVIEW') {
                actions.appendChild(actionButton(messages.retryActivation, 'btn-success', function () {
                    promptReviewReason(function (reason) {
                        submitPurchaseReview('/purchases/retry', purchase, reason);
                    });
                }));
                actions.appendChild(document.createTextNode(' '));
                actions.appendChild(actionButton(messages.sendToRefund, 'btn-warning', function () {
                    promptReviewReason(function (reason) {
                        layer.confirm(messages.refundConfirm, function (index) {
                            layer.close(index);
                            submitPurchaseReview('/purchases/refund', purchase, reason);
                        });
                    });
                }));
            }
            row.appendChild(actions);
            body.appendChild(row);
        });
        var total = Number(result.total || 0);
        reviewPage = Number(result.page || reviewPage);
        reviewTotalPages = Math.max(1, Math.ceil(total / reviewPageSize));
        document.getElementById('purchaseReviewPageInfo').textContent =
            pageInfo(reviewPage, reviewTotalPages, total);
        document.getElementById('purchaseReviewPrevious').disabled = reviewPage <= 1;
        document.getElementById('purchaseReviewNext').disabled = reviewPage >= reviewTotalPages;
    }

    function promptReviewReason(callback) {
        layer.prompt({title: messages.reviewReasonPrompt, formType: 2}, function (value, index) {
            var reason = String(value || '').trim();
            if (reason.length < 8 || reason.length > 500) {
                layer.msg(messages.reviewReasonPrompt);
                return;
            }
            layer.close(index);
            callback(reason);
        });
    }

    function submitPurchaseReview(path, purchase, reason) {
        $.post(base + path, {
            purchaseId: purchase.id,
            expectedVersion: purchase.version,
            reason: reason
        }).done(function (response) {
            var result = response.data;
            if (result === 'ACTIVATED') layer.msg(messages.retryActivated);
            if (result === 'BLOCKED_BY_OPEN_SUBSCRIPTION') layer.msg(messages.retryBlocked);
            if (result === 'REFUND_PENDING') layer.msg(messages.refundPending);
            loadPurchaseReviews();
        }).fail(showError);
    }

    function loadRenewalQueue() {
        if (!permissions.review) return;
        $.get(base + '/renewals', {
            status: $('#renewalQueueStatus').val(),
            limit: 100
        }).done(function (response) {
            renderRenewalQueue(response.data || []);
        }).fail(showError);
    }

    function renderRenewalQueue(items) {
        var body = document.querySelector('#renewalQueueTable tbody');
        while (body.firstChild) body.removeChild(body.firstChild);
        if (!items.length) {
            var emptyRow = document.createElement('tr');
            var emptyCell = document.createElement('td');
            emptyCell.colSpan = 10;
            emptyCell.textContent = messages.renewalEmpty;
            emptyRow.appendChild(emptyCell);
            body.appendChild(emptyRow);
        }
        items.forEach(function (cycle) {
            var row = document.createElement('tr');
            textCell(row, cycle.cycleId);
            textCell(row, cycle.userId);
            textCell(row, cycle.planCode);
            textCell(row, statusLabel(cycle.status));
            textCell(row, cycle.attemptCount);
            textCell(row, cycle.fundingSource);
            textCell(row, cycle.responseCode);
            textCell(row, formatDate(cycle.nextAttemptAt));
            textCell(row, formatDate(cycle.graceEndAt));
            var actions = document.createElement('td');
            actions.appendChild(actionButton(messages.renewalDetails, 'btn-info', function () {
                loadRenewalDetails(cycle.cycleId);
            }));
            if (cycle.status === 'RETRY_WAIT') {
                actions.appendChild(document.createTextNode(' '));
                actions.appendChild(actionButton(messages.renewalRetry, 'btn-warning', function () {
                    promptRenewalRetry(cycle);
                }));
            }
            row.appendChild(actions);
            body.appendChild(row);
        });
    }

    function loadRenewalDetails(cycleId) {
        $.when(
            $.get(base + '/renewals/' + encodeURIComponent(cycleId) + '/attempts', {limit: 50}),
            $.get(base + '/renewals/' + encodeURIComponent(cycleId) + '/audits', {limit: 50})
        ).done(function (attemptResponse, auditResponse) {
            var detail = document.getElementById('renewalQueueDetail');
            detail.textContent = JSON.stringify({
                attempts: attemptResponse[0].data || [],
                audits: auditResponse[0].data || []
            }, null, 2);
        }).fail(showError);
    }

    function promptRenewalRetry(cycle) {
        layer.prompt({title: messages.renewalRetryReason, formType: 2}, function (value, index) {
            var reason = String(value || '').trim();
            if (reason.length < 8 || reason.length > 500) {
                layer.msg(messages.renewalRetryReason);
                return;
            }
            layer.close(index);
            $.post(base + '/renewals/retry', {
                cycleId: cycle.cycleId,
                expectedVersion: cycle.version,
                reason: reason
            }).done(function () {
                layer.msg(messages.renewalRetryScheduled);
                loadRenewalQueue();
            }).fail(showError);
        });
    }

    function changeStatus(plan, status) {
        $.post(base + '/plans/status', {
            planId: plan.id, expectedVersion: plan.version, status: status
        }).done(function () {
            layer.msg(messages.saved);
            loadPlans();
        }).fail(showError);
    }

    $('#planForm').on('submit', function (event) {
        event.preventDefault();
        var data = {
            planCode: $('#planCode').val(), planName: $('#planName').val(),
            priceVnd: $('#priceVnd').val(),
            ticketsPerPeriod: $('#ticketsPerPeriod').val(), periodMonths: $('#periodMonths').val(),
            ticketValidityDays: $('#ticketValidityDays').val()
        };
        var planId = $('#planId').val();
        var url = base + '/plans/create';
        if (planId) {
            url = base + '/plans/update';
            data.planId = planId;
            data.expectedVersion = $('#planVersion').val();
        }
        $.post(url, data).done(function () {
            layer.msg(messages.saved);
            resetPlan();
            loadPlans();
        }).fail(showError);
    });

    $('#resetPlan').on('click', resetPlan);

    $('#activationForm').on('submit', function (event) {
        event.preventDefault();
        var form = event.currentTarget;
        var data = {
            userId: form.userId.value,
            planCode: form.planCode.value,
            startAtMillis: new Date(form.startAt.value).getTime(),
            clientRequestId: form.clientRequestId.value
        };
        if (form.endAt.value) data.endAtMillis = new Date(form.endAt.value).getTime();
        $.post(base + '/activate', data).done(function () {
            layer.msg(messages.activated);
        }).fail(showError);
    });

    $('#lookupForm').on('submit', function (event) {
        event.preventDefault();
        var userId = event.currentTarget.userId.value;
        $.get(base + '/current', {userId: userId}).done(function (response) {
            var output = document.getElementById('currentSubscription');
            if (!response.data) {
                output.textContent = messages.noSubscription;
                return;
            }
            output.textContent = [
                messages.planCode + ': ' + response.data.planCodeSnapshot,
                messages.status + ': ' + statusLabel(response.data.status),
                messages.nextGrantAt + ': ' + (response.data.nextGrantAt || ''),
                messages.endAt + ': ' + (response.data.endAt || '')
            ].join('\n');
        }).fail(showError);
    });

    if (permissions.review) {
        $('#purchaseReviewFilter').on('submit', function (event) {
            event.preventDefault();
            reviewPage = 1;
            loadPurchaseReviews();
        });
        $('#purchaseReviewPrevious').on('click', function () {
            if (reviewPage > 1) {
                reviewPage -= 1;
                loadPurchaseReviews();
            }
        });
        $('#purchaseReviewNext').on('click', function () {
            if (reviewPage < reviewTotalPages) {
                reviewPage += 1;
                loadPurchaseReviews();
            }
        });
        $('#renewalQueueFilter').on('submit', function (event) {
            event.preventDefault();
            document.getElementById('renewalQueueDetail').textContent = '';
            loadRenewalQueue();
        });
        loadPurchaseReviews();
        loadRenewalQueue();
    }

    loadPlans();
}());
