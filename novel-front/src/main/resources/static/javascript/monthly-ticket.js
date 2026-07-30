(function (window, document) {
    'use strict';

    var api = window.NovelGamificationApi;
    var numberFormatter = new Intl.NumberFormat('vi-VN');
    var dateFormatter = new Intl.DateTimeFormat('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Asia/Ho_Chi_Minh'
    });

    function formatMessage(template, values) {
        return String(template || '').replace(/\{([a-zA-Z]+)\}/g, function (match, key) {
            return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match;
        });
    }

    function randomRequestId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        var bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var index = 0; index < bytes.length; index++) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        return Array.prototype.map.call(bytes, function (value) {
            var hex = value.toString(16);
            return hex.length === 1 ? '0' + hex : hex;
        }).join('');
    }

    function setStatus(node, message, error) {
        node.textContent = message || '';
        node.classList.toggle('is-error', Boolean(error));
        node.hidden = !message;
    }

    function appendText(parent, tagName, className, value) {
        var node = document.createElement(tagName);
        node.className = className || '';
        node.textContent = value == null ? '' : String(value);
        parent.appendChild(node);
        return node;
    }

    function createRankingEntry(messages, entry) {
        var item = document.createElement('li');
        item.className = 'monthly-ticket-ranking-item';
        appendText(item, 'span', 'monthly-ticket-rank-number', entry.rank);

        if (entry.picUrl) {
            var imageLink = document.createElement('a');
            imageLink.href = '/book/' + encodeURIComponent(entry.bookId) + '.html';
            var image = document.createElement('img');
            image.className = 'monthly-ticket-ranking-cover';
            image.src = entry.picUrl;
            image.alt = entry.bookName || '';
            image.loading = 'lazy';
            image.width = 48;
            image.height = 64;
            imageLink.appendChild(image);
            item.appendChild(imageLink);
        }

        var identity = document.createElement('div');
        identity.className = 'monthly-ticket-ranking-identity';
        var book = appendText(identity, 'a', 'monthly-ticket-ranking-book', entry.bookName);
        book.href = '/book/' + encodeURIComponent(entry.bookId) + '.html';
        appendText(identity, 'span', 'monthly-ticket-ranking-author', entry.authorName || '');
        item.appendChild(identity);

        var metrics = document.createElement('div');
        metrics.className = 'monthly-ticket-ranking-metrics';
        appendText(metrics, 'strong', '', numberFormatter.format(entry.totalTickets || 0) +
            ' ' + messages.ticketUnit);
        appendText(metrics, 'span', '', formatMessage(messages.voterCount, {
            count: numberFormatter.format(entry.distinctVoterCount || 0)
        }));
        item.appendChild(metrics);
        return item;
    }

    function redirectToLogin(loginUrl) {
        window.location.href = loginUrl + '?originUrl=' + encodeURIComponent(window.location.href);
    }

    function initializeBookWidget() {
        var root = document.getElementById('monthlyTicketBookApp');
        if (!root || !api) { return; }

        var messages = root.dataset;
        var bookId = Number(messages.bookId);
        var content = document.getElementById('monthlyTicketBookContent');
        var login = document.getElementById('monthlyTicketBookLogin');
        var loginButton = document.getElementById('monthlyTicketLoginButton');
        var status = document.getElementById('monthlyTicketBookStatus');
        var period = document.getElementById('monthlyTicketPeriod');
        var total = document.getElementById('monthlyTicketBookTotal');
        var balance = document.getElementById('monthlyTicketBalance');
        var expiry = document.getElementById('monthlyTicketExpiry');
        var countInput = document.getElementById('monthlyTicketCount');
        var voteButton = document.getElementById('monthlyTicketVoteButton');
        var currentSummary = null;
        var currentBalance = 0;
        var pendingVote = null;
        var inFlight = false;

        function showLogin() {
            content.hidden = true;
            login.hidden = false;
            root.hidden = false;
            setStatus(status, messages.loginRequired, false);
        }

        function terminalError(error) {
            if (error && error.code === 1001) {
                showLogin();
                return true;
            }
            if (error && error.code === 7001) {
                root.hidden = true;
                return true;
            }
            return false;
        }

        function canVote() {
            return currentSummary && currentSummary.eligible === true && currentBalance > 0;
        }

        function updateVoteButton() {
            voteButton.disabled = inFlight || !canVote();
            voteButton.textContent = inFlight ? messages.voting : messages.voteAction;
        }

        function renderExpiry(account) {
            var expiringLots = Array.isArray(account.expiringLots) ? account.expiringLots
                .filter(function (lot) {
                    return Number(lot.remainingAmount) > 0 && Boolean(lot.expireAt);
                }).sort(function (left, right) {
                    return new Date(left.expireAt).getTime() - new Date(right.expireAt).getTime();
                }) : [];
            if (expiringLots.length === 0) {
                expiry.textContent = messages.noExpiry;
                return;
            }
            expiry.textContent = formatMessage(messages.expiryValue, {
                count: numberFormatter.format(expiringLots[0].remainingAmount),
                date: dateFormatter.format(new Date(expiringLots[0].expireAt))
            });
        }

        function render(summary, account) {
            currentSummary = summary;
            currentBalance = Math.max(0, Number(account.availableBalance) || 0);
            period.textContent = summary.periodCode || messages.periodUnavailable;
            total.textContent = numberFormatter.format(summary.totalTickets || 0);
            balance.textContent = numberFormatter.format(currentBalance);
            renderExpiry(account);
            countInput.min = '1';
            countInput.max = String(Math.max(1, Math.min(
                Number(summary.maxTicketsPerRequest) || 1,
                Math.max(currentBalance, 1)
            )));
            if (Number(countInput.value) > Number(countInput.max)) {
                countInput.value = countInput.max;
            }
            content.hidden = false;
            login.hidden = true;
            root.hidden = false;
            if (!summary.eligible) {
                setStatus(status, messages.notEligible, false);
            } else if (currentBalance === 0) {
                setStatus(status, messages.emptyBalance, false);
            } else {
                setStatus(status, '', false);
            }
            updateVoteButton();
        }

        function load() {
            setStatus(status, messages.loading, false);
            return Promise.all([
                api.getBookMonthlyTicketSummary(bookId),
                api.getMonthlyTicketAccount()
            ]).then(function (values) {
                render(values[0], values[1]);
            }).catch(function (error) {
                if (!terminalError(error)) {
                    root.hidden = false;
                    setStatus(status, error && error.code ? error.message : messages.loadError, true);
                }
            });
        }

        function vote() {
            var count = Number(countInput.value);
            var limit = currentSummary ? Number(currentSummary.maxTicketsPerRequest) : 0;
            if (!Number.isInteger(count) || count < 1 || count > limit || count > currentBalance) {
                setStatus(status, messages.invalidCount, true);
                return;
            }
            if (count > 1 && !window.confirm(formatMessage(messages.confirmVote, {
                count: numberFormatter.format(count)
            }))) {
                return;
            }
            if (!pendingVote || pendingVote.count !== count) {
                pendingVote = {count: count, clientRequestId: randomRequestId()};
            }
            inFlight = true;
            countInput.readOnly = true;
            updateVoteButton();
            setStatus(status, '', false);
            api.castMonthlyTicketVote(bookId, pendingVote.count, pendingVote.clientRequestId)
                .then(function (result) {
                    pendingVote = null;
                    currentBalance = Math.max(0, Number(result.availableBalance) || 0);
                    currentSummary.totalTickets = Math.max(0, Number(result.bookTotal) || 0);
                    total.textContent = numberFormatter.format(currentSummary.totalTickets);
                    balance.textContent = numberFormatter.format(currentBalance);
                    countInput.max = String(Math.max(1, Math.min(
                        Number(currentSummary.maxTicketsPerRequest) || 1,
                        Math.max(currentBalance, 1)
                    )));
                    return api.getMonthlyTicketAccount().then(function (account) {
                        renderExpiry(account);
                    }).catch(function () {
                        return null;
                    }).then(function () {
                        setStatus(status, formatMessage(messages.voteSuccess, {
                            count: numberFormatter.format(count)
                        }), false);
                    });
                }).catch(function (error) {
                    if (error && error.code) {
                        pendingVote = null;
                    }
                    if (!terminalError(error)) {
                        setStatus(status, error && error.code ? error.message : messages.voteUncertain,
                            true);
                    }
                }).then(function () {
                    inFlight = false;
                    countInput.readOnly = Boolean(pendingVote);
                    updateVoteButton();
                });
        }

        loginButton.addEventListener('click', function () {
            redirectToLogin(messages.loginUrl);
        });
        voteButton.addEventListener('click', vote);
        load();
    }

    function initializeRanking() {
        var root = document.getElementById('monthlyTicketRankingApp');
        if (!root || !api) { return; }

        var messages = root.dataset;
        var status = document.getElementById('monthlyTicketRankingStatus');
        var metadata = document.getElementById('monthlyTicketRankingMeta');
        var list = document.getElementById('monthlyTicketRankingList');
        var periodInput = document.getElementById('monthlyTicketRankingPeriod');
        var loadButton = document.getElementById('monthlyTicketRankingLoad');
        var previousButton = document.getElementById('monthlyTicketRankingPrevious');
        var nextButton = document.getElementById('monthlyTicketRankingNext');
        var page = 1;
        var loading = false;
        var seasonLabels = {
            OPEN: messages.seasonOpen,
            CLOSING: messages.seasonClosing,
            REVIEW: messages.seasonReview,
            FINALIZED: messages.seasonFinalized,
            CLOSED: messages.seasonClosed
        };

        function render(result) {
            list.replaceChildren();
            if (!Array.isArray(result.entries) || result.entries.length === 0) {
                var empty = appendText(list, 'li', 'monthly-ticket-ranking-empty', messages.empty);
                empty.setAttribute('role', 'status');
            } else {
                var fragment = document.createDocumentFragment();
                result.entries.forEach(function (entry) {
                    fragment.appendChild(createRankingEntry(messages, entry));
                });
                list.appendChild(fragment);
            }
            page = Number(result.page) || 1;
            periodInput.value = result.periodCode || periodInput.value;
            var statusLabel = seasonLabels[result.seasonStatus] || result.seasonStatus || '';
            var modeLabel = result.snapshot ? messages.snapshot : messages.live;
            var cutoff = result.cutoffAt ? dateFormatter.format(new Date(result.cutoffAt)) : '';
            metadata.textContent = [result.periodCode, statusLabel, modeLabel, cutoff]
                .filter(function (value) { return Boolean(value); }).join(' · ');
            previousButton.disabled = page <= 1;
            nextButton.disabled = page * (Number(result.pageSize) || 20) >= Number(result.total || 0);
            root.hidden = false;
            setStatus(status, '', false);
        }

        function setLoading(value) {
            loading = value;
            loadButton.disabled = value;
            previousButton.disabled = value || previousButton.disabled;
            nextButton.disabled = value || nextButton.disabled;
            loadButton.textContent = value ? messages.loading : messages.loadAction;
        }

        function load(requestedPage) {
            if (loading) { return; }
            setLoading(true);
            setStatus(status, messages.loading, false);
            api.getMonthlyTicketRanking(periodInput.value || null, requestedPage, 20)
                .then(render)
                .catch(function (error) {
                    if (error && error.code === 7001) {
                        root.hidden = true;
                    } else {
                        root.hidden = false;
                        setStatus(status, error && error.code ? error.message : messages.loadError,
                            true);
                    }
                }).then(function () {
                    setLoading(false);
                });
        }

        loadButton.addEventListener('click', function () { load(1); });
        previousButton.addEventListener('click', function () { load(Math.max(1, page - 1)); });
        nextButton.addEventListener('click', function () { load(page + 1); });
        load(1);
    }

    function initializeHomeWidget() {
        var root = document.getElementById('monthlyTicketHomeApp');
        if (!root || !api) { return; }

        var messages = root.dataset;
        var status = document.getElementById('monthlyTicketHomeStatus');
        var metadata = document.getElementById('monthlyTicketHomeMeta');
        var countdown = document.getElementById('monthlyTicketHomeCountdown');
        var list = document.getElementById('monthlyTicketHomeList');
        var countdownTimer = null;
        var seasonLabels = {
            OPEN: messages.seasonOpen,
            CLOSING: messages.seasonClosing,
            REVIEW: messages.seasonReview,
            FINALIZED: messages.seasonFinalized,
            CLOSED: messages.seasonClosed
        };

        function formatDuration(milliseconds) {
            var totalMinutes = Math.max(1, Math.ceil(milliseconds / 60000));
            var days = Math.floor(totalMinutes / 1440);
            var hours = Math.floor((totalMinutes % 1440) / 60);
            var minutes = totalMinutes % 60;
            var parts = [];
            if (days > 0) {
                parts.push(formatMessage(messages.day, {count: numberFormatter.format(days)}));
            }
            if (hours > 0) {
                parts.push(formatMessage(messages.hour, {count: numberFormatter.format(hours)}));
            }
            if (minutes > 0 || parts.length === 0) {
                parts.push(formatMessage(messages.minute, {count: numberFormatter.format(minutes)}));
            }
            return parts.join(' ');
        }

        function renderCountdown(result) {
            if (countdownTimer !== null) {
                window.clearInterval(countdownTimer);
                countdownTimer = null;
            }
            if (!result.cutoffAt) {
                countdown.textContent = '';
                return;
            }
            var cutoffDate = new Date(result.cutoffAt);
            var cutoffTime = cutoffDate.getTime();
            if (!Number.isFinite(cutoffTime)) {
                countdown.textContent = '';
                return;
            }
            var cutoffLabel = formatMessage(messages.cutoff, {
                date: dateFormatter.format(cutoffDate)
            });
            function update() {
                var remaining = cutoffTime - Date.now();
                if (result.snapshot || result.seasonStatus !== 'OPEN' || remaining <= 0) {
                    countdown.textContent = cutoffLabel + ' · ' + messages.locked;
                    if (countdownTimer !== null) {
                        window.clearInterval(countdownTimer);
                        countdownTimer = null;
                    }
                    return;
                }
                countdown.textContent = cutoffLabel + ' · ' + formatMessage(messages.remaining, {
                    time: formatDuration(remaining)
                });
            }
            update();
            if (!result.snapshot && result.seasonStatus === 'OPEN' && cutoffTime > Date.now()) {
                countdownTimer = window.setInterval(update, 60000);
            }
        }

        function render(result) {
            list.replaceChildren();
            if (!Array.isArray(result.entries) || result.entries.length === 0) {
                var empty = appendText(list, 'li', 'monthly-ticket-ranking-empty', messages.empty);
                empty.setAttribute('role', 'status');
            } else {
                var fragment = document.createDocumentFragment();
                result.entries.forEach(function (entry) {
                    fragment.appendChild(createRankingEntry(messages, entry));
                });
                list.appendChild(fragment);
            }
            var statusLabel = seasonLabels[result.seasonStatus] || result.seasonStatus || '';
            var modeLabel = result.snapshot ? messages.snapshot : messages.live;
            metadata.textContent = [result.periodCode, statusLabel, modeLabel]
                .filter(function (value) { return Boolean(value); }).join(' · ');
            renderCountdown(result);
            root.hidden = false;
            setStatus(status, '', false);
        }

        api.getMonthlyTicketRanking(null, 1, 5)
            .then(render)
            .catch(function (error) {
                if (error && error.code === 7001) {
                    root.hidden = true;
                    return;
                }
                root.hidden = false;
                setStatus(status, error && error.code ? error.message : messages.loadError, true);
            });
    }

    function initializeTicker() {
        var root = document.getElementById('gamificationTickerApp');
        if (!root || !api) { return; }

        var messages = root.dataset;
        var list = document.getElementById('gamificationTickerList');
        var pollTimer = null;

        function createEntry(entry) {
            var item = document.createElement('li');
            item.className = 'gamification-ticker-item';
            appendText(item, 'span', 'gamification-ticker-nickname', entry.nickname);
            appendText(item, 'span', 'gamification-ticker-action', messages.action);
            appendText(item, 'span', 'gamification-ticker-book', entry.bookName);
            appendText(item, 'span', 'gamification-ticker-count',
                '+' + numberFormatter.format(entry.ticketCount || 0));
            return item;
        }

        function render(entries) {
            list.replaceChildren();
            if (!Array.isArray(entries) || entries.length === 0) {
                appendText(list, 'li', 'gamification-ticker-empty', messages.empty);
                root.hidden = false;
                return;
            }
            var fragment = document.createDocumentFragment();
            entries.forEach(function (entry) { fragment.appendChild(createEntry(entry)); });
            list.appendChild(fragment);
            root.hidden = false;
        }

        function poll() {
            api.getTicker(20).then(render).catch(function () {
                // Ticker là nội dung trang trí, không thiết yếu; lỗi tạm thời không hiện thông
                // báo lỗi cho người dùng, chỉ giữ nguyên nội dung lần tải trước.
            });
        }

        poll();
        pollTimer = window.setInterval(poll, 25000);
        window.addEventListener('pagehide', function () {
            if (pollTimer !== null) {
                window.clearInterval(pollTimer);
                pollTimer = null;
            }
        });
    }

    initializeBookWidget();
    initializeRanking();
    initializeHomeWidget();
    initializeTicker();
}(window, document));
