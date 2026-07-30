(function ($) {
    'use strict';

    var prefix = '/novel/gamification';
    var text = window.GamificationI18n || {};
    var pendingGrant = null;
    var permissions = window.GamificationPermissions || {};

    $(function () {
        loadAccounts();
        loadLedger();
        loadJobs();
        loadSeasons();
        loadRewardCampaigns();
        loadRewardAllocations();
        loadQuestCampaigns();
        loadQuestRewards();
        $('#grantForm').on('submit', submitGrant);
        $('#rewardCalculateForm').on('submit', submitRewardCalculation);
        $('#questCampaignCreateForm').on('submit', submitQuestCampaignCreate);
        $('#questRewardForm').on('submit', submitQuestReward);
    });

    function tableOptions(url, formId, columns) {
        return {
            method: 'get',
            url: url,
            striped: true,
            dataType: 'json',
            pagination: true,
            pageSize: 10,
            pageNumber: 1,
            sidePagination: 'server',
            queryParams: function (params) {
                var query = getFormJson(formId);
                query.limit = params.limit;
                query.offset = params.offset;
                return query;
            },
            responseHandler: responseHandler,
            columns: columns
        };
    }

    function responseHandler(response) {
        if (response && response.code === 0) {
            return response.data;
        }
        layer.alert(response && response.msg ? response.msg : text.connectionError);
        return {total: 0, rows: []};
    }

    function loadAccounts() {
        $('#accountTable').bootstrapTable(tableOptions(prefix + '/accounts/list', 'accountSearchForm', [
            {field: 'userId', title: text.userId},
            {field: 'username', title: text.username, formatter: escapeHtml},
            {field: 'nickName', title: text.nickName, formatter: escapeHtml},
            {field: 'availableBalance', title: text.balance, formatter: formatNumber},
            {field: 'lifetimeGranted', title: text.granted, formatter: formatNumber},
            {field: 'lifetimeSpent', title: text.spent, formatter: formatNumber},
            {field: 'lifetimeExpired', title: text.expired, formatter: formatNumber},
            {field: 'lifetimeRevoked', title: text.revoked, formatter: formatNumber},
            {field: 'status', title: text.status, formatter: escapeHtml},
            {field: 'updateTime', title: text.updatedAt, formatter: formatDateTime}
        ]));
    }

    function loadLedger() {
        $('#ledgerTable').bootstrapTable(tableOptions(prefix + '/ledger/list', 'ledgerSearchForm', [
            {field: 'entryNo', title: text.entryNo, formatter: escapeHtml},
            {field: 'userId', title: text.userId},
            {field: 'entryType', title: text.entryType, formatter: escapeHtml},
            {field: 'amount', title: text.amount, formatter: formatNumber},
            {field: 'balanceAfter', title: text.balanceAfter, formatter: formatNumber},
            {field: 'businessType', title: text.business, formatter: businessFormatter},
            {field: 'operatorType', title: text.operator, formatter: operatorFormatter},
            {field: 'reason', title: text.reason, formatter: escapeHtml},
            {field: 'createTime', title: text.createdAt, formatter: formatDateTime}
        ]));
    }

    function loadJobs() {
        $('#jobTable').bootstrapTable(tableOptions(prefix + '/jobs/list', 'jobSearchForm', [
            {field: 'jobType', title: text.jobType, formatter: escapeHtml},
            {field: 'scopeType', title: text.scope, formatter: scopeFormatter},
            {field: 'status', title: text.status, formatter: escapeHtml},
            {field: 'attempt', title: text.attempt, formatter: formatNumber},
            {field: 'checkpoint', title: text.checkpoint, formatter: escapeHtml},
            {field: 'processedCount', title: text.processed, formatter: formatNumber},
            {field: 'heartbeatAt', title: text.heartbeat, formatter: formatDateTime},
            {field: 'errorMessage', title: text.error, formatter: escapeHtml}
        ]));
    }

    function loadSeasons() {
        $('#seasonTable').bootstrapTable(tableOptions(prefix + '/seasons/list', 'seasonSearchForm', [
            {field: 'periodCode', title: text.periodCode, formatter: escapeHtml},
            {field: 'seasonType', title: text.seasonType, formatter: escapeHtml},
            {field: 'status', title: text.status, formatter: escapeHtml},
            {field: 'voteCutoffAt', title: text.cutoffAt, formatter: formatDateTime},
            {field: 'snapshotId', title: text.snapshotId, formatter: formatOptionalNumber},
            {field: 'finalizedAt', title: text.updatedAt, formatter: formatDateTime},
            {field: 'id', title: text.actions, formatter: seasonActionFormatter}
        ]));
    }

    function loadRewardCampaigns() {
        $('#campaignTable').bootstrapTable(tableOptions(prefix + '/rewards/campaigns/list',
            'campaignSearchForm', [
                {field: 'id', title: text.campaignId, formatter: formatNumber},
                {field: 'periodCode', title: text.periodCode, formatter: escapeHtml},
                {field: 'budgetXu', title: text.budgetXu, formatter: formatNumber},
                {field: 'structureJson', title: text.structure, formatter: escapeHtml},
                {field: 'status', title: text.status, formatter: escapeHtml},
                {field: 'approvedBy', title: text.approvedBy, formatter: formatOptionalNumber},
                {field: 'id', title: text.actions, formatter: rewardCampaignActions}
            ]));
    }

    function loadRewardAllocations() {
        $('#allocationTable').bootstrapTable(tableOptions(prefix + '/rewards/allocations/list',
            'allocationSearchForm', [
                {field: 'allocationNo', title: text.allocationNo, formatter: escapeHtml},
                {field: 'bookName', title: text.bookName, formatter: escapeHtml},
                {field: 'authorName', title: text.authorId, formatter: escapeHtml},
                {field: 'rankNo', title: text.rank, formatter: formatNumber},
                {field: 'amountXu', title: text.amountXu, formatter: formatNumber},
                {field: 'status', title: text.status, formatter: escapeHtml},
                {field: 'id', title: text.actions, formatter: rewardAllocationActions}
            ]));
    }

    function loadQuestCampaigns() {
        $('#questCampaignTable').bootstrapTable(tableOptions(prefix + '/quests/campaigns/list',
            'questCampaignSearchForm', [
                {field: 'id', title: text.campaignId, formatter: formatNumber},
                {field: 'campaignCode', title: text.questCampaignCode, formatter: escapeHtml},
                {field: 'startAt', title: text.startAt, formatter: formatDateTime},
                {field: 'endAt', title: text.endAt, formatter: formatDateTime},
                {field: 'status', title: text.status, formatter: escapeHtml},
                {field: 'policyVersion', title: text.policyVersion, formatter: escapeHtml},
                {field: 'id', title: text.actions, formatter: questCampaignActions}
            ]));
    }

    function loadQuestRewards() {
        $('#questRewardTable').bootstrapTable(tableOptions(prefix + '/quests/rewards/list',
            'questRewardSearchForm', [
                {field: 'campaignCode', title: text.questCampaignCode, formatter: escapeHtml},
                {field: 'questCode', title: text.questCode, formatter: escapeHtml},
                {field: 'rewardType', title: text.rewardType, formatter: escapeHtml},
                {field: 'amount', title: text.amount, formatter: formatNumber}
            ]));
    }

    function questCampaignActions(value, row) {
        if (!permissions.config) { return '-'; }
        if (row.status === 'DRAFT') {
            return questCampaignButton('activate', value, text.activateCampaign, 'success');
        }
        if (row.status === 'ACTIVE') {
            return questCampaignButton('close', value, text.closeCampaign, 'warning');
        }
        return '-';
    }

    function questCampaignButton(action, id, label, style) {
        return '<button class="btn btn-xs btn-' + style + '" onclick="gamificationQuestCampaignAction(\'' +
            action + '\',' + Number(id) + ')">' + escapeHtml(label) + '</button>';
    }

    function submitQuestCampaignCreate(event) {
        event.preventDefault();
        var data = getFormJson('questCampaignCreateForm');
        data.startAtMillis = new Date(data.startAt).getTime();
        data.endAtMillis = new Date(data.endAt).getTime();
        delete data.startAt;
        delete data.endAt;
        postQuestConfig('/quests/campaigns/create', data, function () {
            $('#questCampaignCreateForm')[0].reset();
        });
    }

    function submitQuestReward(event) {
        event.preventDefault();
        postQuestConfig('/quests/rewards/save', getFormJson('questRewardForm'));
    }

    function postQuestConfig(path, data, afterSuccess) {
        $.post(prefix + path, data).done(function (response) {
            if (response && response.code === 0) {
                if (typeof afterSuccess === 'function') { afterSuccess(); }
                layer.msg(text.operationSuccess);
                window.reloadQuestCampaigns();
                window.reloadQuestRewards();
            } else {
                layer.alert(response && response.msg ? response.msg : text.connectionError);
            }
        }).fail(function () { layer.alert(text.connectionError); });
    }

    function rewardCampaignActions(value, row) {
        if (!permissions.reward) { return '-'; }
        var id = Number(value);
        var buttons = [];
        if (row.status === 'DRAFT') {
            buttons.push(actionButton('approve', id, text.approveReward, 'success'));
        }
        if (row.status === 'APPROVED') {
            buttons.push(actionButton('post', id, text.postReward, 'primary'));
        }
        return buttons.length ? buttons.join(' ') : '-';
    }

    function rewardAllocationActions(value, row) {
        if (!permissions.adjust || row.status !== 'POSTED_PENDING') { return '-'; }
        return actionButton('clawback', Number(value), text.clawbackReward, 'danger');
    }

    function actionButton(action, id, label, style) {
        return '<button class="btn btn-xs btn-' + style + '" onclick="gamificationRewardAction(\'' +
            action + '\',' + id + ')">' + escapeHtml(label) + '</button>';
    }

    function submitRewardCalculation(event) {
        event.preventDefault();
        $.post(prefix + '/rewards/calculate', getFormJson('rewardCalculateForm'))
            .done(handleRewardSuccess).fail(function () { layer.alert(text.connectionError); });
    }

    function handleRewardSuccess(response) {
        if (response && response.code === 0) {
            layer.msg(text.operationSuccess);
            window.reloadRewardCampaigns();
            window.reloadRewardAllocations();
            window.reloadGamificationSeasons();
        } else {
            layer.alert(response && response.msg ? response.msg : text.connectionError);
        }
    }

    function seasonActionFormatter(value, row) {
        var seasonId = Number(value);
        if (!isFinite(seasonId) || seasonId <= 0) {
            return '-';
        }
        var buttons = ['<button class="btn btn-xs btn-info" onclick="gamificationReconcileSeason(' +
            seasonId + ')">' + escapeHtml(text.reconcileSeason) + '</button>'];
        if (!permissions.finalize) {
            return buttons.join(' ');
        }
        if (row.status === 'OPEN') {
            buttons.push(seasonButton('close', seasonId, text.closeSeason, 'warning'));
        }
        if (row.status === 'CLOSING') {
            buttons.push(seasonButton('pause', seasonId, text.pauseSeason, 'default'));
            buttons.push(seasonButton('retry', seasonId, text.retrySeason, 'primary'));
        }
        if (row.status === 'REVIEW') {
            buttons.push(seasonButton('finalize', seasonId, text.finalizeSeason, 'success'));
        }
        return buttons.join(' ');
    }

    function seasonButton(action, seasonId, label, style) {
        return '<button class="btn btn-xs btn-' + style + '" onclick="gamificationSeasonAction(\'' +
            action + '\',' + seasonId + ')">' + escapeHtml(label) + '</button>';
    }

    function postSeasonAction(action, seasonId, extra) {
        var data = $.extend({seasonId: seasonId}, extra || {});
        $.post(prefix + '/seasons/' + action, data).done(function (response) {
            if (response && response.code === 0) {
                layer.msg(text.operationSuccess);
                window.reloadGamificationSeasons();
                window.reloadGamificationJobs();
            } else {
                layer.alert(response && response.msg ? response.msg : text.connectionError);
            }
        }).fail(function () {
            layer.alert(text.connectionError);
        });
    }

    function submitGrant(event) {
        event.preventDefault();
        if (!pendingGrant) {
            pendingGrant = {
                userId: $('#grantUserId').val(),
                amount: $('#grantAmount').val(),
                reason: $('#grantReason').val(),
                clientRequestId: newRequestId(),
                effectiveAtMillis: Date.now()
            };
        }
        setGrantBusy(true);
        $.ajax({
            type: 'POST',
            url: prefix + '/grant',
            data: pendingGrant,
            success: function (response) {
                if (response && response.code === 0) {
                    pendingGrant = null;
                    $('#grantForm')[0].reset();
                    layer.msg(text.operationSuccess);
                    window.reloadGamificationAccounts();
                    window.reloadGamificationLedger();
                } else {
                    layer.alert(response && response.msg ? response.msg : text.connectionError);
                }
                setGrantBusy(false);
            },
            error: function () {
                setGrantBusy(false);
                layer.alert(text.retryGrant || text.connectionError);
            }
        });
    }

    function setGrantBusy(busy) {
        $('#grantButton').prop('disabled', busy);
        $('#grantForm input').prop('readonly', busy);
    }

    function newRequestId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 'grant_' + window.crypto.randomUUID();
        }
        return 'grant_' + Date.now() + '_' + Math.random().toString(36).slice(2, 14);
    }

    function businessFormatter(value, row) {
        return escapeHtml((value || '-') + (row.businessId ? ' / ' + row.businessId : ''));
    }

    function operatorFormatter(value, row) {
        return escapeHtml((value || '-') + (row.operatorId ? ' #' + row.operatorId : ''));
    }

    function scopeFormatter(value, row) {
        return escapeHtml((value || '-') + (row.scopeKey ? ' / ' + row.scopeKey : ''));
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

    function formatOptionalNumber(value) {
        return value == null || value === '' ? '-' : formatNumber(value);
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    window.reloadGamificationAccounts = function () {
        $('#accountTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadGamificationLedger = function () {
        $('#ledgerTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadGamificationJobs = function () {
        $('#jobTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadGamificationSeasons = function () {
        $('#seasonTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadRewardCampaigns = function () {
        $('#campaignTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadRewardAllocations = function () {
        $('#allocationTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadQuestCampaigns = function () {
        $('#questCampaignTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.reloadQuestRewards = function () {
        $('#questRewardTable').bootstrapTable('refresh', {pageNumber: 1});
    };
    window.gamificationQuestCampaignAction = function (action, campaignId) {
        postQuestConfig('/quests/campaigns/' + action, {campaignId: campaignId});
    };
    window.gamificationRewardAction = function (action, id) {
        if (action === 'clawback') {
            layer.prompt({title: text.clawbackReason, formType: 2}, function (reason, index) {
                if (String(reason || '').trim().length < 10) { return; }
                layer.close(index);
                $.post(prefix + '/rewards/clawback', {allocationId: id, reason: String(reason).trim()})
                    .done(handleRewardSuccess).fail(function () { layer.alert(text.connectionError); });
            });
            return;
        }
        $.post(prefix + '/rewards/' + action, {campaignId: id})
            .done(handleRewardSuccess).fail(function () { layer.alert(text.connectionError); });
    };
    window.gamificationSeasonAction = function (action, seasonId) {
        if (action === 'pause') {
            layer.prompt({title: text.pauseReason, formType: 2}, function (reason, index) {
                if (String(reason || '').trim().length < 10) {
                    return;
                }
                layer.close(index);
                postSeasonAction(action, seasonId, {reason: String(reason).trim()});
            });
            return;
        }
        postSeasonAction(action, seasonId);
    };
    window.gamificationReconcileSeason = function (seasonId) {
        $.get(prefix + '/seasons/reconcile', {seasonId: seasonId}).done(function (response) {
            if (response && response.code === 0) {
                var rows = response.data || [];
                layer.alert(rows.length ? escapeHtml(JSON.stringify(rows)) : text.noDrift);
            } else {
                layer.alert(response && response.msg ? response.msg : text.connectionError);
            }
        }).fail(function () {
            layer.alert(text.connectionError);
        });
    };
})(jQuery);
