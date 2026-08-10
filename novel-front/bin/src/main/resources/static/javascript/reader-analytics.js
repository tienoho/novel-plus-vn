(function (window, document) {
    'use strict';

    var STORAGE_KEY = 'novel:analytics:visitor:v1';

    function randomHex(bytes) {
        var values = new Uint8Array(bytes);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(values);
        } else {
            for (var i = 0; i < values.length; i++) {
                values[i] = Math.floor(Math.random() * 256);
            }
        }
        var result = '';
        for (var j = 0; j < values.length; j++) {
            result += ('0' + values[j].toString(16)).slice(-2);
        }
        return result;
    }

    function visitorId() {
        var value;
        try {
            value = window.localStorage.getItem(STORAGE_KEY);
            if (!value) {
                value = 'v1_' + randomHex(16);
                window.localStorage.setItem(STORAGE_KEY, value);
            }
        } catch (ignored) {
            value = 'session_' + randomHex(16);
        }
        return value;
    }

    function postEvent(state, type, progress, useBeacon, suffix) {
        var sentKey = type + ':' + suffix;
        if (state.sent[sentKey]) {
            return;
        }
        state.sent[sentKey] = true;
        var duration = Math.min(86400, Math.max(0, Math.floor((Date.now() - state.startedAt) / 1000)));
        var payload = JSON.stringify({
            clientEventId: state.pageViewId + ':' + type + ':' + suffix,
            visitorId: state.visitorId,
            bookId: state.bookId,
            indexId: state.indexId,
            eventType: type,
            progressPercent: progress,
            durationSeconds: duration
        });

        if (useBeacon && navigator.sendBeacon) {
            navigator.sendBeacon('/book/analytics/read-event',
                new Blob([payload], {type: 'application/json'}));
            return;
        }

        var request = new XMLHttpRequest();
        request.open('POST', '/book/analytics/read-event', true);
        request.setRequestHeader('Content-Type', 'application/json');
        request.send(payload);
    }

    function progressPercent() {
        var root = document.documentElement;
        var body = document.body;
        var scrollTop = window.pageYOffset || root.scrollTop || body.scrollTop || 0;
        var height = Math.max(root.scrollHeight, body.scrollHeight) - window.innerHeight;
        if (height <= 0) {
            return 100;
        }
        return Math.max(0, Math.min(100, Math.round(scrollTop * 100 / height)));
    }

    function start() {
        var dataSaverEnabled = window.NovelPwa && window.NovelPwa.isDataSaverEnabled &&
            window.NovelPwa.isDataSaverEnabled();
        if (dataSaverEnabled || (navigator.connection && navigator.connection.saveData)) {
            return;
        }
        var enabled = document.getElementById('analyticsEnabled');
        var book = document.getElementById('bookId');
        var index = document.getElementById('preContentId');
        if (!enabled || enabled.value !== 'true' || !book || !index) {
            return;
        }

        var state = {
            bookId: Number(book.value),
            indexId: Number(index.value),
            visitorId: visitorId(),
            pageViewId: 'pv_' + randomHex(16),
            startedAt: Date.now(),
            sent: {},
            highestProgress: 0,
            ticking: false
        };
        if (!state.bookId || !state.indexId) {
            return;
        }

        postEvent(state, 'START', 0, false, '0');

        function measure() {
            state.ticking = false;
            var progress = progressPercent();
            state.highestProgress = Math.max(state.highestProgress, progress);
            [25, 50, 75].forEach(function (threshold) {
                if (progress >= threshold) {
                    postEvent(state, 'PROGRESS', threshold, false, String(threshold));
                }
            });
            if (progress >= 90 && Date.now() - state.startedAt >= 5000) {
                postEvent(state, 'COMPLETE', 100, false, '100');
            }
        }

        window.addEventListener('scroll', function () {
            if (!state.ticking) {
                state.ticking = true;
                window.setTimeout(measure, 400);
            }
        }, {passive: true});
        window.addEventListener('pagehide', function () {
            var finalProgress = Math.min(99, Math.max(state.highestProgress, progressPercent()));
            if (finalProgress > 0) {
                postEvent(state, 'PROGRESS', finalProgress, true, 'FINAL');
            }
        });
        window.setTimeout(measure, 5000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
}(window, document));
