(function () {
    'use strict';

    var api = '/novel/gamification/settings';
    var text = window.GamificationSettingsI18n;
    var selected = null;
    var numericConstraints = {};
    var earliestEffectiveAtMillis = null;
    var fieldGroups = {
        common: [['policyVersion', 'text'], ['zoneId', 'text', 'zone']],
        event: [['eventEnabled', 'checkbox'], ['eventDrainBatchSize', 'number', 'count'],
            ['eventDrainDelayMs', 'number', 'milliseconds'], ['eventMaxAttempt', 'number', 'count']],
        ticket: [['ticketEnabled', 'checkbox'], ['ticketLotValidityDays', 'number', 'days'],
            ['ticketExpiryCron', 'text', 'cron'], ['ticketExpiryBatchSize', 'number', 'count'],
            ['ticketMaxGrantPerBatch', 'number', 'count']],
        vote: [['voteEnabled', 'checkbox'], ['voteAllowCrawledBooks', 'checkbox'],
            ['voteIpHashKeyId', 'text', 'keyId'], ['voteMaxTicketsPerRequest', 'number', 'count'],
            ['voteMaxVotesPerDay', 'number', 'count'], ['voteMaxTicketsPerDay', 'number', 'count'],
            ['voteMaxTicketsPerBookPerSeason', 'number', 'count'],
            ['voteMaxLotsPerSpend', 'number', 'count']],
        quest: [['questEnabled', 'checkbox'],
            ['questHeartbeatIntervalSeconds', 'number', 'seconds'],
            ['questHeartbeatMaxMinutesPerDay', 'number', 'minutes']],
        realm: [['realmEnabled', 'checkbox'], ['realmChangeCooldownHours', 'number', 'hours']],
        season: [['seasonEnabled', 'checkbox'], ['seasonCloseCron', 'text', 'cron'],
            ['seasonCloseDrainSeconds', 'number', 'seconds'],
            ['seasonResumeDelayMs', 'number', 'milliseconds'],
            ['seasonReviewWindowHours', 'number', 'hours']],
        reward: [['rewardEnabled', 'checkbox'], ['rewardClaimWindowDays', 'number', 'days'],
            ['rewardReleaseCron', 'text', 'cron']],
        worker: [['jobLeaseSeconds', 'number', 'seconds'], ['jobBatchSize', 'number', 'count']]
    };

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

    function formatDate(value) {
        if (!value) {
            return '—';
        }
        var date = new Date(value);
        if (!Number.isFinite(date.getTime())) {
            return String(value);
        }
        return new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'medium', timeStyle: 'medium'
        }).format(date);
    }

    function statusText(status) {
        return text.statuses[status] || status;
    }

    function fieldText(fieldName) {
        return text.fields[fieldName] || fieldName;
    }

    function formatValue(value) {
        if (value === true) { return text.enabled; }
        if (value === false) { return text.disabled; }
        if (value === null || value === undefined || value === '') { return '—'; }
        return String(value);
    }

    function formatActor(value) {
        return value === null || value === undefined ? '—' : '#' + value;
    }

    function request(url, options) {
        options = options || {};
        options.credentials = 'same-origin';
        options.headers = Object.assign({'Content-Type': 'application/json'}, options.headers || {});
        return fetch(url, options).then(function (response) {
            return response.json().then(function (body) {
                if (!response.ok || body.code !== 0) {
                    throw new Error(body.msg || text.requestFailed);
                }
                return body.data;
            });
        });
    }

    function body(value) {
        return {method: 'POST', body: JSON.stringify(value)};
    }

    function notice(message, failed) {
        var node = document.getElementById('settingsNotice');
        node.textContent = message || '';
        node.className = failed ? 'text-danger' : 'text-success';
    }

    function createEditor() {
        var root = document.getElementById('settingsGroups');
        Object.keys(fieldGroups).forEach(function (groupName) {
            var column = document.createElement('section');
            column.className = 'col-md-6';
            var title = document.createElement('h4');
            title.textContent = text.groups[groupName];
            column.appendChild(title);
            fieldGroups[groupName].forEach(function (definition) {
                var fieldName = definition[0];
                var unitName = definition[2];
                var wrap = document.createElement('div');
                wrap.className = definition[1] === 'checkbox' ? 'checkbox' : 'form-group';
                var label = document.createElement('label');
                label.setAttribute('for', fieldName);
                var input = document.createElement('input');
                input.id = fieldName;
                input.name = fieldName;
                input.type = definition[1];
                input.className = definition[1] === 'checkbox' ? '' : 'form-control';
                if (definition[1] === 'number') {
                    input.step = '1';
                }
                input.disabled = true;
                if (definition[1] === 'checkbox') {
                    label.appendChild(input);
                    label.appendChild(document.createTextNode(' ' + text.fields[fieldName]));
                } else {
                    label.textContent = text.fields[fieldName];
                    wrap.appendChild(label);
                    wrap.appendChild(input);
                }
                if (unitName) {
                    var help = document.createElement('p');
                    help.className = 'help-block';
                    help.id = fieldName + 'Help';
                    help.textContent = text.units[unitName];
                    wrap.appendChild(help);
                }
                if (definition[1] === 'checkbox') {
                    wrap.appendChild(label);
                }
                column.appendChild(wrap);
            });
            root.appendChild(column);
        });
    }

    function applyConstraints(constraints) {
        numericConstraints = constraints || {};
        Object.keys(numericConstraints).forEach(function (fieldName) {
            var input = document.getElementById(fieldName);
            var constraint = numericConstraints[fieldName];
            if (!input || !constraint) { return; }
            input.min = String(constraint.min);
            input.max = String(constraint.max);
            var help = document.getElementById(fieldName + 'Help');
            if (help) {
                var definition = null;
                Object.keys(fieldGroups).some(function (groupName) {
                    definition = fieldGroups[groupName].find(function (candidate) {
                        return candidate[0] === fieldName;
                    });
                    return Boolean(definition);
                });
                var unit = definition && definition[2] ? text.units[definition[2]] + '. ' : '';
                help.textContent = unit + text.range
                    .replace('{min}', constraint.min).replace('{max}', constraint.max);
            }
        });
    }

    function renderSelectedMetadata(row) {
        document.getElementById('selectedRevision').textContent = row.revisionCode || '—';
        document.getElementById('selectedStatus').textContent = statusText(row.status);
        document.getElementById('selectedCreatedBy').textContent = formatActor(row.createdBy);
        document.getElementById('selectedApprovedBy').textContent = formatActor(row.approvedBy);
        document.getElementById('selectedEffective').textContent = formatDate(row.effectiveAt);
    }

    function setForm(row) {
        selected = row;
        earliestEffectiveAtMillis = null;
        renderSelectedMetadata(row);
        var makerCannotApprove = Boolean(row.highRisk)
            && Number(row.createdBy) === Number(text.currentUserId);
        Object.keys(fieldGroups).forEach(function (groupName) {
            fieldGroups[groupName].forEach(function (definition) {
                var input = document.getElementById(definition[0]);
                var value = row[definition[0]];
                if (definition[1] === 'checkbox') {
                    input.checked = Boolean(value);
                } else {
                    input.value = value == null ? '' : value;
                }
                input.disabled = row.status !== 'DRAFT' || !window.GamificationSettingsPermissions.edit;
            });
        });
        setDisabled('saveDraftButton', row.status !== 'DRAFT'
            || !window.GamificationSettingsPermissions.edit);
        setDisabled('submitButton', row.status !== 'DRAFT'
            || !window.GamificationSettingsPermissions.edit);
        setDisabled('approveButton', row.status !== 'PENDING_APPROVAL'
            || !window.GamificationSettingsPermissions.approve || makerCannotApprove);
        setDisabled('rejectButton', row.status !== 'PENDING_APPROVAL'
            || !window.GamificationSettingsPermissions.approve);
        setDisabled('scheduleButton', true);
        setDisabled('cancelButton', row.status !== 'SCHEDULED'
            || !window.GamificationSettingsPermissions.activate);
        setDisabled('rollbackButton', ['ACTIVE', 'ARCHIVED'].indexOf(row.status) === -1
            || !window.GamificationSettingsPermissions.activate);
        if (row.status === 'PENDING_APPROVAL' && makerCannotApprove) {
            notice(text.makerWarning, true);
        }
        loadDiff(row.id);
        previewCron();
    }

    function snapshotFromForm() {
        var snapshot = {runtimeRevision: selected.revisionNo};
        Object.keys(fieldGroups).forEach(function (groupName) {
            fieldGroups[groupName].forEach(function (definition) {
                var input = document.getElementById(definition[0]);
                snapshot[definition[0]] = definition[1] === 'checkbox' ? input.checked
                    : definition[1] === 'number' ? Number(input.value) : input.value.trim();
            });
        });
        return snapshot;
    }

    function loadActive() {
        return request(api + '/active').then(function (data) {
            applyConstraints(data.constraints);
            document.getElementById('activeRevision').textContent = data.config.revisionCode;
            document.getElementById('activeSource').textContent = data.source;
            document.getElementById('secretStatus').textContent = data.hashSecretReady
                ? text.ready : text.notReady;
            document.getElementById('activeEffective').textContent = formatDate(data.config.activatedAt);
            if (!selected) {
                setForm(data.config);
            }
        });
    }

    function loadHistory() {
        return request(api + '/history?limit=100').then(function (rows) {
            var root = document.getElementById('revisionHistory');
            var queue = document.getElementById('approvalQueue');
            root.replaceChildren();
            queue.replaceChildren();
            rows.forEach(function (row) {
                var tr = document.createElement('tr');
                var revision = document.createElement('td');
                revision.textContent = row.revisionCode;
                var status = document.createElement('td');
                status.textContent = statusText(row.status);
                var action = document.createElement('td');
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn btn-xs btn-default';
                button.textContent = text.view;
                button.addEventListener('click', function () { setForm(row); });
                action.appendChild(button);
                tr.appendChild(revision);
                tr.appendChild(status);
                tr.appendChild(action);
                root.appendChild(tr);
            });
            var pending = rows.filter(function (row) { return row.status === 'PENDING_APPROVAL'; });
            if (pending.length === 0) {
                var emptyRow = document.createElement('tr');
                var emptyCell = document.createElement('td');
                emptyCell.colSpan = 4;
                emptyCell.textContent = text.emptyQueue;
                emptyRow.appendChild(emptyCell);
                queue.appendChild(emptyRow);
                return;
            }
            pending.forEach(function (row) {
                var tr = document.createElement('tr');
                [row.revisionCode, formatActor(row.createdBy),
                    row.highRisk ? text.highRisk : text.normalRisk].forEach(function (value) {
                    var cell = document.createElement('td');
                    cell.textContent = value;
                    tr.appendChild(cell);
                });
                var action = document.createElement('td');
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn btn-xs btn-info';
                button.textContent = text.view;
                button.addEventListener('click', function () { setForm(row); });
                action.appendChild(button);
                tr.appendChild(action);
                queue.appendChild(tr);
            });
        });
    }

    function loadDiff(id) {
        request(api + '/' + id + '/diff').then(function (diff) {
            if (!selected || selected.id !== id) { return; }
            earliestEffectiveAtMillis = Number(diff.earliestEffectiveAtMillis);
            document.getElementById('activationClass').textContent =
                (text.activationClasses[diff.activationClass] || diff.activationClass)
                + ' · ' + (diff.highRisk ? text.highRisk : text.normalRisk);
            var list = document.getElementById('diffKeys');
            list.replaceChildren();
            diff.changedKeys.forEach(function (key) {
                var row = document.createElement('tr');
                [fieldText(key), formatValue(diff.before[key]), formatValue(diff.after[key])]
                    .forEach(function (value) {
                        var cell = document.createElement('td');
                        cell.textContent = value;
                        row.appendChild(cell);
                    });
                list.appendChild(row);
            });
            document.getElementById('earliestEffective').textContent = text.earliest.replace(
                '{time}', formatDate(diff.earliestEffectiveAtMillis));
            setDisabled('scheduleButton', selected.status !== 'APPROVED'
                || !window.GamificationSettingsPermissions.activate);
        }).catch(function (error) { notice(error.message, true); });
    }

    function previewCron() {
        if (!selected) { return; }
        var key = document.getElementById('cronSelector').value;
        var expression = document.getElementById(key).value;
        var zone = document.getElementById('zoneId').value;
        if (!expression || !zone) { return; }
        request(api + '/cron-preview?expression=' + encodeURIComponent(expression)
            + '&zoneId=' + encodeURIComponent(zone)).then(function (values) {
            var root = document.getElementById('cronPreview');
            root.replaceChildren();
            values.forEach(function (value) {
                var item = document.createElement('li');
                item.textContent = value;
                root.appendChild(item);
            });
        }).catch(function (error) { notice(error.message, true); });
    }

    function reason() {
        var value = document.getElementById('changeReason').value.trim();
        if (value.length < 10 || value.length > 500) {
            throw new Error(text.reasonPrompt);
        }
        return value;
    }

    function action(name, extra) {
        if (!selected) { return; }
        var payload;
        try {
            payload = Object.assign({expectedVersion: selected.version, reason: reason()}, extra || {});
        } catch (error) {
            notice(error.message, true);
            return;
        }
        request(api + '/' + selected.id + '/' + name, body(payload)).then(function (row) {
            notice(text.success, false);
            setForm(row);
            loadActive();
            loadHistory();
        }).catch(function (error) { notice(error.message, true); });
    }

    function rollback() {
        if (!selected) { return; }
        var payload;
        try {
            payload = {reason: reason()};
        } catch (error) {
            notice(error.message, true);
            return;
        }
        request(api + '/' + selected.id + '/rollback', body(payload)).then(function (row) {
            notice(text.success, false);
            setForm(row);
            loadHistory();
        }).catch(function (error) { notice(error.message, true); });
    }

    document.addEventListener('DOMContentLoaded', function () {
        createEditor();
        loadActive().then(loadHistory).catch(function (error) { notice(error.message, true); });
        bind('settingsForm', 'submit', function (event) {
            event.preventDefault();
            if (!selected || selected.status !== 'DRAFT') { return; }
            var payload;
            try {
                payload = {expectedVersion: selected.version, reason: reason(), snapshot: snapshotFromForm()};
            } catch (error) {
                notice(error.message, true);
                return;
            }
            request(api + '/' + selected.id + '/save', body(payload)).then(function (row) {
                notice(text.success, false);
                setForm(row);
                loadHistory();
            }).catch(function (error) { notice(error.message, true); });
        });
        bind('cloneActiveButton', 'click', function () {
            var value = window.prompt(text.reasonPrompt);
            if (!value) { return; }
            request(api + '/drafts/clone-active', body({reason:value})).then(function (row) {
                setForm(row); loadHistory();
            }).catch(function (error) { notice(error.message, true); });
        });
        bind('importEnvButton', 'click', function () {
            var value = window.prompt(text.reasonPrompt);
            if (!value) { return; }
            request(api + '/drafts/import-env', body({reason:value})).then(function (row) {
                setForm(row); loadHistory();
            }).catch(function (error) { notice(error.message, true); });
        });
        bind('submitButton', 'click', function () { action('submit'); });
        bind('approveButton', 'click', function () { action('approve'); });
        bind('rejectButton', 'click', function () { action('reject'); });
        bind('scheduleButton', 'click', function () {
            if (!Number.isFinite(earliestEffectiveAtMillis)) {
                notice(text.requestFailed, true);
                return;
            }
            var value = window.prompt(text.schedulePrompt + '\n'
                + text.earliest.replace('{time}', formatDate(earliestEffectiveAtMillis)));
            if (!value) { return; }
            var millis = new Date(value).getTime();
            if (!Number.isFinite(millis)) { notice(text.schedulePrompt, true); return; }
            if (millis < earliestEffectiveAtMillis) {
                notice(text.scheduleTooEarly, true);
                return;
            }
            action('schedule', {effectiveAtMillis:millis});
        });
        bind('cancelButton', 'click', function () { action('cancel'); });
        bind('rollbackButton', 'click', rollback);
        bind('cronSelector', 'change', previewCron);
        ['ticketExpiryCron', 'seasonCloseCron', 'rewardReleaseCron', 'zoneId'].forEach(function (id) {
            bind(id, 'change', previewCron);
        });
    });
}());
