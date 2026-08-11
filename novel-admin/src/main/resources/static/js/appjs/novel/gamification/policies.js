(function () {
    'use strict';

    var api = '/novel/gamification/policies';
    var text = window.GamificationPolicyI18n;
    var selected = null;

    function request(url, options) {
        options = options || {};
        options.credentials = 'same-origin';
        options.headers = Object.assign({'Content-Type': 'application/json'}, options.headers || {});
        return fetch(url, options).then(function (response) {
            return response.json().then(function (payload) {
                if (!response.ok || payload.code !== 0) {
                    throw new Error(payload.msg || text.failed);
                }
                return payload.data;
            });
        });
    }

    function post(value) {
        return {method: 'POST', body: JSON.stringify(value)};
    }

    function notice(message, failed) {
        var node = document.getElementById('policyNotice');
        if (!node) { return; }
        node.textContent = message || '';
        node.className = failed ? 'text-danger' : 'text-success';
    }

    function setDisabled(id, disabled) {
        var node = document.getElementById(id);
        if (node) {
            node.disabled = disabled;
        }
    }

    function bind(id, eventName, handler) {
        var node = document.getElementById(id);
        if (node) {
            node.addEventListener(eventName, handler);
        }
    }

    function statusText(status) {
        return text.statuses[status] || status;
    }

    function value(form, name) {
        return form.elements[name].value.trim();
    }

    function number(form, name) {
        var raw = form.elements[name].value;
        return raw === '' ? null : Number(raw);
    }

    function reason() {
        var result = document.getElementById('actionReason').value.trim();
        if (result.length < 10 || result.length > 500) {
            throw new Error(text.reasonRequired);
        }
        return result;
    }

    function requireDraft() {
        if (!selected || !selected.bundle || selected.bundle.status !== 'DRAFT') {
            throw new Error(text.failed);
        }
        return selected.bundle;
    }

    function renderList(rows) {
        var target = document.getElementById('policyList');
        target.textContent = '';
        (rows || []).forEach(function (row) {
            var tr = document.createElement('tr');
            var version = document.createElement('td');
            var status = document.createElement('td');
            var action = document.createElement('td');
            var button = document.createElement('button');
            version.textContent = row.policyVersion;
            status.textContent = statusText(row.status);
            button.type = 'button';
            button.className = 'btn btn-default btn-xs';
            button.textContent = text.select;
            button.addEventListener('click', function () { loadDetail(row.policyVersion); });
            action.appendChild(button);
            tr.appendChild(version);
            tr.appendChild(status);
            tr.appendChild(action);
            target.appendChild(tr);
        });
    }

    function renderDetail(detail) {
        selected = detail;
        var bundle = detail.bundle;
        var makerCannotApprove = Number(bundle.createdBy) === Number(text.currentUserId);
        document.getElementById('policyHeading').textContent = bundle.policyVersion
            + ' · ' + statusText(bundle.status);
        document.getElementById('policyMetadata').textContent = [
            text.metadata.version + ': ' + bundle.version,
            text.metadata.creator + ': ' + bundle.createdBy,
            text.metadata.approver + ': ' + (bundle.approvedBy || '—'),
            text.metadata.hash + ': ' + bundle.contentHash
        ].join(' · ');
        document.getElementById('levelsPreview').textContent = JSON.stringify({
            levels: detail.levels, rewards: detail.levelRewards
        }, null, 2);
        document.getElementById('questsPreview').textContent = JSON.stringify({
            quests: detail.quests, rewards: detail.questRewards
        }, null, 2);
        document.getElementById('realmsPreview').textContent = JSON.stringify(detail.realms, null, 2);
        document.getElementById('abusePreview').textContent = JSON.stringify({
            policy: detail.abusePolicy, rules: detail.abuseRules
        }, null, 2);
        if (detail.publicPolicy) {
            document.querySelector('#publicPolicyForm [name=title]').value = detail.publicPolicy.title || '';
            document.querySelector('#publicPolicyForm [name=contentText]').value =
                detail.publicPolicy.contentText || '';
        }
        Array.prototype.forEach.call(document.querySelectorAll('.policy-editor input, .policy-editor select, .policy-editor textarea, .policy-editor button'),
            function (node) {
                node.disabled = bundle.status !== 'DRAFT'
                    || !window.GamificationPolicyPermissions.edit;
            });
        setDisabled('submitPolicy', bundle.status !== 'DRAFT'
            || !window.GamificationPolicyPermissions.edit);
        setDisabled('approvePolicy', bundle.status !== 'PENDING_APPROVAL'
            || !window.GamificationPolicyPermissions.approve || makerCannotApprove);
        setDisabled('publishPolicy', bundle.status !== 'APPROVED'
            || !window.GamificationPolicyPermissions.publish);
        if (bundle.status === 'PENDING_APPROVAL' && makerCannotApprove) {
            notice(text.makerWarning, true);
        }
    }

    function loadList() {
        return request(api + '?limit=100').then(renderList).catch(function (error) {
            notice(error.message, true);
        });
    }

    function loadDetail(version) {
        return request(api + '/' + encodeURIComponent(version)).then(renderDetail).catch(function (error) {
            notice(error.message, true);
        });
    }

    function refresh(detail) {
        renderDetail(detail);
        loadList();
        notice(text.success, false);
    }

    bind('createPolicyForm', 'submit', function (event) {
        event.preventDefault();
        var form = event.currentTarget;
        request(api + '/drafts', post({
            policyVersion: value(form, 'policyVersion'),
            sourceVersion: value(form, 'sourceVersion') || null,
            reason: value(form, 'reason')
        })).then(refresh).catch(function (error) { notice(error.message, true); });
    });

    bind('levelForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/levels', post({
                expectedVersion: bundle.version, reason: reason(), level: {
                    level: number(form, 'level'), minExp: number(form, 'minExp'),
                    titleKey: value(form, 'titleKey'), frameCode: value(form, 'frameCode') || null
                }
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    bind('levelRewardForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/level-rewards', post({
                expectedVersion: bundle.version, reason: reason(), reward: {
                    level: number(form, 'level'), ticketAmount: number(form, 'ticketAmount'),
                    ticketValidityDays: number(form, 'ticketValidityDays')
                }
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    bind('questForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/quests', post({
                expectedVersion: bundle.version, reason: reason(),
                expReward: number(form, 'expReward'), ticketReward: number(form, 'ticketReward'),
                quest: {
                    questCode: value(form, 'questCode'), eventType: value(form, 'eventType'),
                    periodType: value(form, 'periodType'), targetCount: number(form, 'targetCount'),
                    nameKey: value(form, 'nameKey'), sortNo: number(form, 'sortNo'),
                    active: form.elements.active.checked
                }
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    bind('realmForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/realms', post({
                expectedVersion: bundle.version, reason: reason(), realm: {
                    realmCode: value(form, 'realmCode'), nameKey: value(form, 'nameKey'),
                    minLevel: number(form, 'minLevel'), sortNo: number(form, 'sortNo'),
                    active: form.elements.active.checked
                }
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    bind('abuseForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/abuse-rules', post({
                expectedVersion: bundle.version, reason: reason(),
                reviewScoreThreshold: number(form, 'reviewScoreThreshold'), rule: {
                    ruleCode: value(form, 'ruleCode'), metricName: value(form, 'metricName'),
                    thresholdValue: number(form, 'thresholdValue'),
                    windowMinutes: number(form, 'windowMinutes'), score: number(form, 'score'),
                    hardBlock: form.elements.hardBlock.checked
                }
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    bind('publicPolicyForm', 'submit', function (event) {
        event.preventDefault();
        try {
            var bundle = requireDraft();
            var form = event.currentTarget;
            request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/public-policy', post({
                expectedVersion: bundle.version, reason: reason(), title: value(form, 'title'),
                contentText: value(form, 'contentText')
            })).then(refresh).catch(function (error) { notice(error.message, true); });
        } catch (error) { notice(error.message, true); }
    });

    [['submitPolicy', 'submit'], ['approvePolicy', 'approve'], ['publishPolicy', 'publish']]
        .forEach(function (entry) {
            bind(entry[0], 'click', function () {
                try {
                    if (!selected || !selected.bundle) { throw new Error(text.failed); }
                    var bundle = selected.bundle;
                    request(api + '/' + encodeURIComponent(bundle.policyVersion) + '/' + entry[1], post({
                        expectedVersion: bundle.version, reason: reason()
                    })).then(function () { return loadDetail(bundle.policyVersion); })
                        .then(loadList).then(function () { notice(text.success, false); })
                        .catch(function (error) { notice(error.message, true); });
                } catch (error) { notice(error.message, true); }
            });
        });

    loadList();
}());
