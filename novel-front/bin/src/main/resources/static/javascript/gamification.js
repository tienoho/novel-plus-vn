(function () {
    'use strict';

    var root = document.getElementById('gamificationApp');
    if (!root) {
        return;
    }

    var statusNode = document.getElementById('gamificationStatus');
    var profileNode = document.getElementById('gamificationProfile');
    var questList = document.getElementById('questList');
    var checkInButton = document.getElementById('checkInButton');
    var checkInMessage = document.getElementById('checkInMessage');
    var numberFormatter = new Intl.NumberFormat('vi-VN');
    var dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Asia/Ho_Chi_Minh'
    });
    var api = window.NovelGamificationApi;
    var messages = root.dataset;
    var realms = {
        NHAP_MON: messages.realmNhapMon,
        TIEN_PHONG: messages.realmTienPhong,
        DAI_SU: messages.realmDaiSu,
        TON_GIA: messages.realmTonGia,
        CHI_TON: messages.realmChiTon
    };
    var currentProfileVersion = null;
    var lastClaimedQuestCode = null;
    var tickerToggle = document.getElementById('gamificationTickerOptOut');
    var tickerToggleStatus = document.getElementById('gamificationTickerOptOutStatus');

    function redirectToLogin() {
        window.location.href = messages.loginUrl + '?originUrl=' +
            encodeURIComponent(window.location.href);
    }

    function handleApiError(error) {
        if (error && error.code === 1001) {
            redirectToLogin();
            error.message = 'AUTH_REDIRECT';
        }
        throw error;
    }

    function setStatus(message, error) {
        statusNode.textContent = message || '';
        statusNode.classList.toggle('is-error', Boolean(error));
        statusNode.hidden = !message;
    }

    function renderProfile(profile) {
        document.getElementById('profileLevel').textContent = numberFormatter.format(profile.level || 1);
        document.getElementById('profileExp').textContent = numberFormatter.format(profile.totalExp || 0);
        document.getElementById('profileRealm').textContent = realms[profile.realmCode] || messages.realmEmpty;
        document.getElementById('profileStreak').textContent = numberFormatter.format(profile.checkinStreak || 0);
        document.getElementById('profileNextLevel').textContent = profile.nextLevelExp == null
            ? '' : messages.nextLevel + ': ' + numberFormatter.format(profile.nextLevelExp) +
                ' ' + messages.expUnit;
        profileNode.hidden = false;
        currentProfileVersion = profile.version;
        if (tickerToggle) {
            tickerToggle.checked = Boolean(profile.tickerOptOut);
            tickerToggle.disabled = false;
        }
    }

    function toggleTickerPreference() {
        if (!tickerToggle || currentProfileVersion == null) { return; }
        var optOut = tickerToggle.checked;
        tickerToggle.disabled = true;
        if (tickerToggleStatus) {
            tickerToggleStatus.textContent = messages.tickerOptOutSaving || '';
            tickerToggleStatus.hidden = false;
        }
        api.updateTickerPreference(optOut, currentProfileVersion).catch(handleApiError)
            .then(function (profile) {
                currentProfileVersion = profile.version;
                tickerToggle.checked = Boolean(profile.tickerOptOut);
                tickerToggle.disabled = false;
                if (tickerToggleStatus) { tickerToggleStatus.hidden = true; }
            }).catch(function (error) {
                if (error.message !== 'AUTH_REDIRECT') {
                    tickerToggle.checked = !optOut;
                    tickerToggle.disabled = false;
                    if (tickerToggleStatus) {
                        tickerToggleStatus.textContent = messages.tickerOptOutSaveFailed || '';
                        tickerToggleStatus.hidden = false;
                    }
                }
            });
    }

    function appendText(parent, tagName, className, value) {
        var node = document.createElement(tagName);
        node.className = className;
        node.textContent = value;
        parent.appendChild(node);
        return node;
    }

    function formatMessage(template, values) {
        return template.replace(/\{([a-zA-Z]+)\}/g, function (match, key) {
            return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match;
        });
    }

    function formatRewards(expAmount, ticketAmount) {
        var rewards = [];
        if (expAmount > 0) {
            rewards.push('+' + numberFormatter.format(expAmount) + ' ' + messages.expUnit);
        }
        if (ticketAmount > 0) {
            rewards.push('+' + numberFormatter.format(ticketAmount) + ' ' + messages.ticketUnit);
        }
        return rewards.join(' · ');
    }

    function rewardText(quest) {
        return formatRewards(quest.expReward, quest.ticketReward);
    }

    function renderCheckIn(result) {
        var detail = result.alreadyCheckedIn
            ? formatMessage(messages.checkInAlready, {
                streak: numberFormatter.format(result.streak || 0)
            })
            : formatMessage(messages.checkInSuccess, {
                reward: formatRewards(result.expGained || 0, result.ticketGained || 0)
            });
        if (result.nextCheckIn) {
            detail += ' ' + formatMessage(messages.checkInNext, {
                next: dateTimeFormatter.format(new Date(result.nextCheckIn))
            });
        }
        checkInMessage.textContent = detail;
        checkInButton.textContent = messages.checkedIn;
        checkInButton.disabled = true;
        if (!result.alreadyCheckedIn) {
            // Animation chỉ chạy sau khi server đã xác nhận điểm danh thành công, không chạy
            // lạc quan trước khi có phản hồi.
            checkInMessage.classList.remove('gamification-flash');
            void checkInMessage.offsetWidth;
            checkInMessage.classList.add('gamification-flash');
        }
    }

    function checkIn() {
        checkInButton.disabled = true;
        checkInButton.textContent = messages.checkingIn;
        checkInMessage.textContent = '';
        setStatus('', false);
        api.checkIn().catch(handleApiError).then(function (result) {
            renderCheckIn(result);
            return loadData();
        }).catch(function (error) {
            if (error.message !== 'AUTH_REDIRECT') {
                setStatus(error.code ? error.message : messages.loadError, true);
                checkInButton.disabled = false;
                checkInButton.textContent = messages.checkIn;
            }
        });
    }

    function claimQuest(quest, button) {
        button.disabled = true;
        button.textContent = messages.claiming;
        setStatus('', false);
        api.claimQuest(quest.questCode).catch(handleApiError).then(function () {
            // Chỉ đặt cờ animation sau khi server xác nhận claim thành công (nhánh .then này).
            lastClaimedQuestCode = quest.questCode;
            setStatus(messages.claimSuccess, false);
            return loadData();
        }).catch(function (error) {
            if (error.message !== 'AUTH_REDIRECT') {
                setStatus(error.code ? error.message : messages.loadError, true);
                button.disabled = false;
                button.textContent = messages.claim;
            }
        });
    }

    function createQuestCard(quest) {
        var article = document.createElement('article');
        article.className = 'gamification-quest';
        if (quest.questCode === lastClaimedQuestCode && quest.claimed) {
            article.classList.add('gamification-flash');
            lastClaimedQuestCode = null;
        }
        var heading = document.createElement('div');
        heading.className = 'gamification-quest-heading';
        appendText(heading, 'h3', '', quest.name);
        appendText(heading, 'span', 'gamification-reward', rewardText(quest));
        article.appendChild(heading);

        var progressLabel = numberFormatter.format(quest.currentCount || 0) + ' / ' +
            numberFormatter.format(quest.targetCount || 0);
        var progress = document.createElement('progress');
        progress.max = Math.max(quest.targetCount || 1, 1);
        progress.value = Math.min(quest.currentCount || 0, progress.max);
        progress.setAttribute('aria-label', quest.name + ': ' + progressLabel);
        article.appendChild(progress);

        var footer = document.createElement('div');
        footer.className = 'gamification-quest-footer';
        appendText(footer, 'span', 'gamification-progress-label', progressLabel);
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'gamification-claim-button';
        if (quest.claimed) {
            button.textContent = messages.claimed;
            button.disabled = true;
        } else if (!quest.completed) {
            button.textContent = messages.incomplete;
            button.disabled = true;
        } else {
            button.textContent = messages.claim;
            button.addEventListener('click', function () { claimQuest(quest, button); });
        }
        footer.appendChild(button);
        article.appendChild(footer);
        return article;
    }

    function renderQuests(quests) {
        questList.replaceChildren();
        if (!Array.isArray(quests) || quests.length === 0) {
            appendText(questList, 'p', 'gamification-empty', messages.empty);
            return;
        }
        var fragment = document.createDocumentFragment();
        quests.forEach(function (quest) { fragment.appendChild(createQuestCard(quest)); });
        questList.appendChild(fragment);
    }

    function loadData() {
        return Promise.all([
            api.getProfile().catch(handleApiError),
            api.getQuests().catch(handleApiError)
        ]).then(function (values) {
            renderProfile(values[0]);
            renderQuests(values[1]);
            if (statusNode.textContent === messages.loading) {
                setStatus('', false);
            }
        }).catch(function (error) {
            if (error.message !== 'AUTH_REDIRECT') {
                setStatus(error.code ? error.message : messages.loadError, true);
            }
        });
    }

    setStatus(messages.loading, false);
    if (!api) {
        setStatus(messages.loadError, true);
        return;
    }
    checkInButton.addEventListener('click', checkIn);
    if (tickerToggle) {
        tickerToggle.addEventListener('change', toggleTickerPreference);
    }
    loadData();
}());
