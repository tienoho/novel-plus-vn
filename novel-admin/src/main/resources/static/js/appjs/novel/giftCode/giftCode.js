(function () {
    'use strict';
    var base = '/novel/giftCode';
    var messages = window.GiftCodeI18n || {};
    var permissions = window.GiftCodePermissions || {};
    var currentCampaign = null;
    var codePage = 1;
    var redemptionPage = 1;
    var dateFormatter = new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh'
    });

    function error(xhr) {
        layer.msg(xhr && xhr.responseJSON && xhr.responseJSON.msg || messages.failed);
    }

    function cell(row, value) {
        var item = document.createElement('td');
        item.textContent = value == null ? '' : String(value);
        row.appendChild(item);
    }

    function statusLabel(status) {
        return {DRAFT: messages.statusDraft, ACTIVE: messages.statusActive,
            CLOSED: messages.statusClosed}[status] || status;
    }

    function codeStatusLabel(status) {
        return {ACTIVE: messages.codeStatusActive, EXHAUSTED: messages.codeStatusExhausted,
            REVOKED: messages.codeStatusRevoked}[status] || status;
    }

    function button(label, css, handler) {
        var item = document.createElement('button');
        item.type = 'button';
        item.className = 'btn btn-xs ' + css;
        item.textContent = label;
        item.addEventListener('click', handler);
        return item;
    }

    function loadCampaigns() {
        $.get(base + '/campaigns').done(function (response) {
            var body = document.querySelector('#campaignTable tbody');
            while (body.firstChild) body.removeChild(body.firstChild);
            (response.data || []).forEach(function (campaign) {
                var row = document.createElement('tr');
                cell(row, campaign.campaignCode);
                cell(row, campaign.campaignName);
                cell(row, campaign.rewardAmount + ' ' + campaign.rewardType);
                cell(row, campaign.redeemedCount + '/' + campaign.maxRedemptions);
                cell(row, statusLabel(campaign.status));
                var actions = document.createElement('td');
                actions.appendChild(button(messages.details, 'btn-info', function () {
                    loadCampaignDetails(campaign);
                }));
                if (permissions.config && campaign.status !== 'CLOSED') {
                    actions.appendChild(document.createTextNode(' '));
                    if (campaign.status === 'DRAFT') {
                        actions.appendChild(button(messages.activate, 'btn-success', function () {
                            changeStatus(campaign, 'ACTIVE');
                        }));
                        actions.appendChild(document.createTextNode(' '));
                    }
                    actions.appendChild(button(messages.close, 'btn-danger', function () {
                        changeStatus(campaign, 'CLOSED');
                    }));
                }
                row.appendChild(actions);
                body.appendChild(row);
            });
        }).fail(error);
    }

    function loadCampaignDetails(campaign) {
        currentCampaign = campaign;
        document.getElementById('giftDetailTitle').textContent =
            messages.detailsTitle + ': ' + campaign.campaignCode;
        loadCodes(1);
        loadRedemptions(1);
    }

    function loadCodes(requestedPage) {
        if (!currentCampaign) return;
        $.get(base + '/codes', {campaignId: currentCampaign.id, page: requestedPage, limit: 50})
            .done(function (response) {
                var data = response.data || {};
                var body = document.querySelector('#giftCodeTable tbody');
                while (body.firstChild) body.removeChild(body.firstChild);
                (data.items || []).forEach(function (code) {
                    var row = document.createElement('tr');
                    cell(row, code.id);
                    cell(row, code.codeHint);
                    cell(row, code.redeemedCount + '/' + code.maxRedemptions);
                    cell(row, codeStatusLabel(code.status));
                    var actions = document.createElement('td');
                    if (permissions.revoke && code.status === 'ACTIVE' &&
                            Number(code.redeemedCount) === 0) {
                        actions.appendChild(button(messages.revoke, 'btn-danger', function () {
                            revokeCode(code);
                        }));
                    }
                    row.appendChild(actions);
                    body.appendChild(row);
                });
                codePage = Number(data.page) || 1;
                document.getElementById('giftCodePrevious').disabled = codePage <= 1;
                document.getElementById('giftCodeNext').disabled =
                    codePage * (Number(data.pageSize) || 50) >= Number(data.total || 0);
            }).fail(error);
    }

    function loadRedemptions(requestedPage) {
        if (!currentCampaign) return;
        $.get(base + '/redemptions', {campaignId: currentCampaign.id,
            page: requestedPage, limit: 50})
            .done(function (response) {
                var data = response.data || {};
                var body = document.querySelector('#giftRedemptionTable tbody');
                while (body.firstChild) body.removeChild(body.firstChild);
                (data.items || []).forEach(function (receipt) {
                    var row = document.createElement('tr');
                    cell(row, receipt.id);
                    cell(row, receipt.userId);
                    cell(row, receipt.codeHint);
                    cell(row, receipt.rewardAmount + ' ' + receipt.rewardType);
                    cell(row, dateFormatter.format(new Date(receipt.redeemedAt)));
                    body.appendChild(row);
                });
                redemptionPage = Number(data.page) || 1;
                document.getElementById('giftRedemptionPrevious').disabled = redemptionPage <= 1;
                document.getElementById('giftRedemptionNext').disabled =
                    redemptionPage * (Number(data.pageSize) || 50) >= Number(data.total || 0);
            }).fail(error);
    }

    function revokeCode(code) {
        if (!window.confirm(messages.revokeConfirm)) return;
        $.post(base + '/codes/revoke', {codeId: code.id, expectedVersion: code.version})
            .done(function () {
                layer.msg(messages.revoked);
                loadCodes(codePage);
            }).fail(error);
    }

    function changeStatus(campaign, status) {
        $.post(base + '/campaigns/status', {campaignId: campaign.id,
            expectedVersion: campaign.version, status: status})
            .done(loadCampaigns).fail(error);
    }

    $('#campaignForm').on('submit', function (event) {
        event.preventDefault();
        var form = event.currentTarget;
        var ticketReward = form.rewardType.value === 'READING_TICKET';
        var data = {
            campaignCode: form.campaignCode.value,
            campaignName: form.campaignName.value,
            rewardType: form.rewardType.value,
            rewardAmount: form.rewardAmount.value,
            startAtMillis: new Date(form.startAt.value).getTime(),
            endAtMillis: new Date(form.endAt.value).getTime(),
            maxRedemptions: form.maxRedemptions.value,
            maxPerUser: form.maxPerUser.value
        };
        if (ticketReward) data.ticketValidityDays = form.ticketValidityDays.value;
        $.post(base + '/campaigns/create', data).done(function () {
            layer.msg(messages.created);
            form.reset();
            loadCampaigns();
        }).fail(error);
    });

    $('#issueForm').on('submit', function (event) {
        event.preventDefault();
        $.post(base + '/codes/issue', $(event.currentTarget).serialize()).done(function (response) {
            document.getElementById('issuedCodes').value = (response.data || []).join('\n');
            layer.msg(messages.issued);
            if (currentCampaign && Number(event.currentTarget.campaignId.value) ===
                    Number(currentCampaign.id)) {
                loadCodes(1);
            }
        }).fail(error);
    });

    document.getElementById('giftCodePrevious').addEventListener('click', function () {
        loadCodes(Math.max(1, codePage - 1));
    });
    document.getElementById('giftCodeNext').addEventListener('click', function () {
        loadCodes(codePage + 1);
    });
    document.getElementById('giftRedemptionPrevious').addEventListener('click', function () {
        loadRedemptions(Math.max(1, redemptionPage - 1));
    });
    document.getElementById('giftRedemptionNext').addEventListener('click', function () {
        loadRedemptions(redemptionPage + 1);
    });
    loadCampaigns();
}());
