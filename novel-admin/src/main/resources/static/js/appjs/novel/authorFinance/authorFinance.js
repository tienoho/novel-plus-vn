(function ($) {
    'use strict';

    var prefix = '/novel/authorFinance';
    var text = window.AuthorFinanceI18n || {};
    var permissions = window.AuthorFinancePermissions || {};

    $(function () {
        loadKycTable();
        loadWithdrawalTable();
    });

    function responseHandler(response) {
        if (response && response.code === 0) {
            return response.data;
        }
        parent.layer.alert(response && response.msg ? response.msg : text.connectionError);
        return {total: 0, rows: []};
    }

    function queryParams(formId, params) {
        var query = getFormJson(formId);
        query.limit = params.limit;
        query.offset = params.offset;
        return query;
    }

    function loadKycTable() {
        $('#kycTable').bootstrapTable({
            method: 'get',
            url: prefix + '/kyc/list',
            iconSize: 'outline',
            striped: true,
            dataType: 'json',
            pagination: true,
            pageSize: 10,
            pageNumber: 1,
            sidePagination: 'server',
            queryParams: function (params) {
                return queryParams('kycSearchForm', params);
            },
            responseHandler: responseHandler,
            columns: [
                {field: 'authorId', title: text.authorId},
                {
                    field: 'identityNumberLast4',
                    title: text.identity,
                    formatter: function (value, row) {
                        return escapeHtml((row.identityType || '') + ' •••• ' + (value || ''));
                    }
                },
                {
                    field: 'bankAccountLast4',
                    title: text.bankAccount,
                    formatter: function (value, row) {
                        return escapeHtml((row.bankCode || '') + ' •••• ' + (value || ''));
                    }
                },
                {
                    field: 'status',
                    title: text.status,
                    formatter: statusLabel
                },
                {
                    field: 'submittedAt',
                    title: text.submittedAt,
                    formatter: formatDateTime
                },
                {
                    field: 'id',
                    title: text.actions,
                    align: 'center',
                    formatter: kycActions
                }
            ]
        });
    }

    function loadWithdrawalTable() {
        $('#withdrawalTable').bootstrapTable({
            method: 'get',
            url: prefix + '/withdrawals/list',
            iconSize: 'outline',
            striped: true,
            dataType: 'json',
            pagination: true,
            pageSize: 10,
            pageNumber: 1,
            sidePagination: 'server',
            queryParams: function (params) {
                return queryParams('withdrawalSearchForm', params);
            },
            responseHandler: responseHandler,
            columns: [
                {field: 'withdrawalNo', title: text.withdrawalNo},
                {field: 'authorId', title: text.authorId},
                {field: 'requestedXu', title: text.requestedXu, formatter: formatNumber},
                {field: 'grossAmountVnd', title: text.grossVnd, formatter: formatNumber},
                {field: 'withheldTaxVnd', title: text.taxVnd, formatter: formatNumber},
                {field: 'netAmountVnd', title: text.netVnd, formatter: formatNumber},
                {
                    field: 'bankAccountLast4',
                    title: text.bankAccount,
                    formatter: function (value, row) {
                        return escapeHtml((row.bankCode || '') + ' •••• ' + (value || ''));
                    }
                },
                {field: 'status', title: text.status, formatter: statusLabel},
                {field: 'requestedAt', title: text.requestedAt, formatter: formatDateTime},
                {
                    field: 'id',
                    title: text.actions,
                    align: 'center',
                    formatter: withdrawalActions
                }
            ]
        });
    }

    function kycActions(value, row) {
        var buttons = [];
        if (permissions.pii) {
            buttons.push(actionButton('btn-info', text.viewPii, 'viewKyc(' + row.id + ')'));
        }
        if (permissions.kyc && row.status === 'PENDING') {
            buttons.push(actionButton('btn-primary', text.approve,
                'approveKyc(' + row.id + ',' + row.submissionVersion + ')'));
            buttons.push(actionButton('btn-danger', text.reject,
                'rejectKyc(' + row.id + ',' + row.submissionVersion + ')'));
        }
        return buttons.length ? buttons.join(' ') : '-';
    }

    function withdrawalActions(value, row) {
        var buttons = [];
        if (permissions.pii) {
            buttons.push(actionButton('btn-info', text.viewPayoutDetails, 'viewPayoutDetails(' + row.id + ')'));
        }
        if (permissions.payout) {
            if (row.status === 'PENDING_REVIEW') {
                buttons.push(actionButton('btn-primary', text.approveWithdrawal,
                    'approveWithdrawal(' + row.id + ',' + row.version + ',' + row.grossAmountVnd + ')'));
                buttons.push(actionButton('btn-danger', text.reject,
                    'rejectWithdrawal(' + row.id + ',' + row.version + ')'));
            } else if (row.status === 'APPROVED') {
                buttons.push(actionButton('btn-primary', text.startProcessing,
                    'startProcessing(' + row.id + ',' + row.version + ')'));
                buttons.push(actionButton('btn-danger', text.reject,
                    'rejectWithdrawal(' + row.id + ',' + row.version + ')'));
            } else if (row.status === 'PROCESSING') {
                buttons.push(actionButton('btn-success', text.markPaid,
                    'markPaid(' + row.id + ',' + row.version + ')'));
                buttons.push(actionButton('btn-danger', text.markFailed,
                    'markFailed(' + row.id + ',' + row.version + ')'));
            }
        }
        return buttons.length ? buttons.join(' ') : '-';
    }

    function actionButton(cssClass, label, action) {
        return '<button type="button" class="btn btn-sm ' + cssClass + '" onclick="' + action + '">' +
            escapeHtml(label) + '</button>';
    }

    function statusLabel(value) {
        var labels = {
            PENDING: text.pending,
            VERIFIED: text.verified,
            REJECTED: text.rejected,
            PENDING_REVIEW: text.pendingReview,
            APPROVED: text.approved,
            PROCESSING: text.processing,
            SETTLEMENT_PENDING: text.settlementPending,
            RELEASE_PENDING: text.releasePending,
            PAID: text.paid,
            FAILED: text.failed,
            CANCELLED: text.cancelled
        };
        return escapeHtml(labels[value] || value || '-');
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        var date = new Date(value);
        return isNaN(date.getTime()) ? escapeHtml(value) : date.toLocaleString('vi-VN');
    }

    function formatNumber(value) {
        var number = Number(value);
        return isFinite(number) ? number.toLocaleString('vi-VN') : '-';
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function post(url, data, onSuccess) {
        $.ajax({
            type: 'POST',
            url: url,
            data: data,
            success: function (response) {
                if (response && response.code === 0) {
                    layer.msg(text.operationSuccess);
                    onSuccess();
                } else {
                    layer.alert(response && response.msg ? response.msg : text.connectionError);
                }
            },
            error: function () {
                layer.alert(text.connectionError);
            }
        });
    }

    window.reloadKyc = function () {
        $('#kycTable').bootstrapTable('refresh', {pageNumber: 1});
    };

    window.reloadWithdrawals = function () {
        $('#withdrawalTable').bootstrapTable('refresh', {pageNumber: 1});
    };

    window.viewKyc = function (id) {
        $.get(prefix + '/kyc/' + id, function (response) {
            if (!response || response.code !== 0) {
                layer.alert(response && response.msg ? response.msg : text.connectionError);
                return;
            }
            var item = response.data || {};
            var rows = [
                [text.legalName, item.legalName],
                [text.dateOfBirth, item.dateOfBirth],
                [text.identityNumber, item.identityNumber],
                [text.taxCode, item.taxCode || '-'],
                [text.bankCode, item.bankCode],
                [text.bankAccount, item.bankAccount],
                [text.bankAccountName, item.bankAccountName]
            ];
            var content = '<div class="p-m"><table class="table table-bordered">';
            $.each(rows, function (_, row) {
                content += '<tr><th>' + escapeHtml(row[0]) + '</th><td>' + escapeHtml(row[1]) + '</td></tr>';
            });
            content += '</table></div>';
            layer.open({type: 1, title: text.kycDetail, area: ['640px', 'auto'], content: content});
        }).fail(function () {
            layer.alert(text.connectionError);
        });
    };

    window.viewPayoutDetails = function (id) {
        $.get(prefix + '/withdrawals/' + id + '/payout-details', function (response) {
            if (!response || response.code !== 0) {
                layer.alert(response && response.msg ? response.msg : text.connectionError);
                return;
            }
            var item = response.data || {};
            var rows = [
                [text.withdrawalNo, item.withdrawalNo],
                [text.bankCode, item.bankCode],
                [text.bankAccount, item.bankAccount],
                [text.bankAccountName, item.bankAccountName],
                [text.netVnd, formatNumber(item.netAmountVnd)]
            ];
            var content = '<div class="p-m"><table class="table table-bordered">';
            $.each(rows, function (_, row) {
                content += '<tr><th>' + escapeHtml(row[0]) + '</th><td>' + escapeHtml(row[1]) + '</td></tr>';
            });
            content += '</table></div>';
            layer.open({type: 1, title: text.payoutDetail, area: ['640px', 'auto'], content: content});
        }).fail(function () {
            layer.alert(text.connectionError);
        });
    };

    window.approveKyc = function (id, version) {
        layer.confirm(text.confirmApproveKyc, function (index) {
            layer.close(index);
            post(prefix + '/kyc/' + id + '/approve', {expectedVersion: version}, window.reloadKyc);
        });
    };

    window.rejectKyc = function (id, version) {
        promptReason(function (reason) {
            post(prefix + '/kyc/' + id + '/reject', {expectedVersion: version, reason: reason}, window.reloadKyc);
        });
    };

    window.approveWithdrawal = function (id, version, grossAmountVnd) {
        layer.prompt({title: text.taxPrompt, formType: 0, value: '0'}, function (value, index) {
            if (!/^\d+$/.test(value) || Number(value) > Number(grossAmountVnd)) {
                layer.msg(text.invalidNonNegativeInteger);
                return;
            }
            layer.close(index);
            post(prefix + '/withdrawals/' + id + '/approve', {
                expectedVersion: version,
                withheldTaxVnd: value
            }, window.reloadWithdrawals);
        });
    };

    window.rejectWithdrawal = function (id, version) {
        promptReason(function (reason) {
            post(prefix + '/withdrawals/' + id + '/reject', {
                expectedVersion: version,
                reason: reason
            }, window.reloadWithdrawals);
        });
    };

    window.startProcessing = function (id, version) {
        layer.confirm(text.confirmProcessing, function (index) {
            layer.close(index);
            post(prefix + '/withdrawals/' + id + '/processing', {
                expectedVersion: version
            }, window.reloadWithdrawals);
        });
    };

    window.markPaid = function (id, version) {
        layer.prompt({title: text.providerReferencePrompt, formType: 0}, function (value, index) {
            if (!value || value.trim().length < 3) {
                return;
            }
            layer.close(index);
            post(prefix + '/withdrawals/' + id + '/paid', {
                expectedVersion: version,
                providerReference: value.trim()
            }, window.reloadWithdrawals);
        });
    };

    window.markFailed = function (id, version) {
        promptReason(function (reason) {
            post(prefix + '/withdrawals/' + id + '/failed', {
                expectedVersion: version,
                reason: reason
            }, window.reloadWithdrawals);
        });
    };

    function promptReason(callback) {
        layer.prompt({title: text.rejectReason, formType: 2}, function (value, index) {
            var reason = value ? value.trim() : '';
            if (reason.length < 10 || reason.length > 500) {
                return;
            }
            layer.close(index);
            callback(reason);
        });
    }
})(jQuery);
