(function (window, document) {
    'use strict';

    var DB_NAME = 'novel-reader-offline-v1';
    var STORE_NAME = 'chapters';
    var DB_VERSION = 1;
    var LINE_HEIGHT_KEY = 'novel:reader:line-height:v1';
    var FONT_FAMILY_KEY = 'novel:reader:font-family:v1';
    var BACKGROUND_KEY = 'novel:reader:background:v1';
    var READING_HEARTBEAT_INTERVAL_MS = 60000;
    var READING_HEARTBEAT_SAMPLE_MS = 1000;
    var READING_HEARTBEAT_RETRY_MS = 5000;
    var READING_HEARTBEAT_MIN_ACTIVE_SECONDS = 30;
    var READING_HEARTBEAT_MAX_ACTIVE_SECONDS = 120;
    var speech = window.speechSynthesis;
    var utterance = null;

    var FONT_FAMILIES = {
        system: '"Segoe UI", Arial, sans-serif',
        serif: 'Georgia, "Times New Roman", serif',
        readable: 'Verdana, "Segoe UI", sans-serif'
    };

    var READER_BACKGROUNDS = {
        light: {backgroundColor: '#ffffff', color: '#1f2937', colorScheme: 'light'},
        sepia: {backgroundColor: '#f5ecd8', color: '#3f3528', colorScheme: 'light'},
        night: {backgroundColor: '#171717', color: '#e5e7eb', colorScheme: 'dark'}
    };

    function message(key, fallback) {
        return window.NovelI18n && window.NovelI18n[key] ? window.NovelI18n[key] : fallback;
    }

    function firstElement(ids) {
        for (var index = 0; index < ids.length; index++) {
            var element = document.getElementById(ids[index]);
            if (element) {
                return element;
            }
        }
        return null;
    }

    function valueOf(ids) {
        var element = firstElement(ids);
        return element ? String(element.value || element.textContent || '').trim() : '';
    }

    function requestJson(url, options) {
        if (!window.fetch) {
            return Promise.reject(new Error('FETCH_UNSUPPORTED'));
        }
        options = options || {};
        options.credentials = 'same-origin';
        options.headers = Object.assign({'Accept': 'application/json'}, options.headers || {});
        if (options.body && !options.headers['Content-Type']) {
            options.headers['Content-Type'] = 'application/json;charset=UTF-8';
        }
        return window.fetch(url, options).then(function (response) {
            return response.json();
        });
    }

    function readingSessionId() {
        var bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var index = 0; index < bytes.length; index++) {
                bytes[index] = Math.floor(Math.random() * 256);
            }
        }
        return Array.prototype.map.call(bytes, function (value) {
            return value.toString(16).padStart(2, '0');
        }).join('');
    }

    function textWalker(root) {
        return document.createTreeWalker(root, window.NodeFilter ? window.NodeFilter.SHOW_TEXT : 4);
    }

    function absoluteTextOffset(root, targetNode, targetOffset) {
        var walker = textWalker(root);
        var offset = 0;
        var node;
        while ((node = walker.nextNode())) {
            if (node === targetNode) {
                return offset + Math.max(0, Math.min(Number(targetOffset) || 0, node.nodeValue.length));
            }
            offset += node.nodeValue.length;
        }
        return offset;
    }

    function textPointAtOffset(root, requestedOffset) {
        var walker = textWalker(root);
        var remaining = Math.max(0, Number(requestedOffset) || 0);
        var last = null;
        var node;
        while ((node = walker.nextNode())) {
            last = node;
            if (remaining <= node.nodeValue.length) {
                return {node: node, offset: remaining};
            }
            remaining -= node.nodeValue.length;
        }
        return last ? {node: last, offset: last.nodeValue.length} : null;
    }

    function paragraphIndexForNode(root, node) {
        var element = node && node.nodeType === 1 ? node : node && node.parentElement;
        var blocks = root.querySelectorAll('p,li,blockquote,div');
        for (var index = 0; index < blocks.length; index++) {
            if (blocks[index] === element || blocks[index].contains(element)) {
                return index;
            }
        }
        return 0;
    }

    function firstTextNode(element) {
        if (!element) { return null; }
        var walker = textWalker(element);
        var node;
        while ((node = walker.nextNode())) {
            if (String(node.nodeValue || '').trim()) { return node; }
        }
        return null;
    }

    function progressPercent(contentRoot) {
        var rect = contentRoot.getBoundingClientRect();
        var height = Math.max(rect.height || contentRoot.scrollHeight || 1, 1);
        var position = Math.max(0, Math.min(height, -rect.top + (window.innerHeight || 0) * 0.45));
        return Math.round(position / height * 10000) / 100;
    }

    function currentAnchor(contentRoot) {
        var x = Math.max(1, Math.min((window.innerWidth || 2) / 2, (window.innerWidth || 2) - 1));
        var y = Math.max(1, Math.min((window.innerHeight || 2) * 0.45, (window.innerHeight || 2) - 1));
        var element = document.elementFromPoint ? document.elementFromPoint(x, y) : null;
        while (element && element !== contentRoot && !contentRoot.contains(element)) {
            element = element.parentElement;
        }
        if (!element || !contentRoot.contains(element)) { element = contentRoot; }
        var node = firstTextNode(element) || firstTextNode(contentRoot);
        var percent = progressPercent(contentRoot);
        var totalLength = String(contentRoot.textContent || '').length;
        var offset = node ? absoluteTextOffset(contentRoot, node, 0) : Math.round(totalLength * percent / 100);
        var quote = String((element || contentRoot).textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500);
        return {
            paragraphIndex: paragraphIndexForNode(contentRoot, node),
            characterOffset: Math.max(0, offset),
            progressPercent: percent,
            selectedText: quote || null
        };
    }

    function selectedAnchor(contentRoot) {
        var selection = window.getSelection && window.getSelection();
        if (!selection || selection.rangeCount === 0 || selection.isCollapsed) { return null; }
        var range = selection.getRangeAt(0);
        if (!contentRoot.contains(range.startContainer) || !contentRoot.contains(range.endContainer)) {
            return null;
        }
        var selectedText = String(selection.toString() || '').replace(/\s+/g, ' ').trim();
        if (!selectedText) { return null; }
        return {
            paragraphIndex: paragraphIndexForNode(contentRoot, range.startContainer),
            characterOffset: absoluteTextOffset(contentRoot, range.startContainer, range.startOffset),
            progressPercent: progressPercent(contentRoot),
            selectedText: selectedText.slice(0, 500)
        };
    }

    function scrollToTextOffset(contentRoot, offset) {
        var point = textPointAtOffset(contentRoot, offset);
        var target = point && (point.node.parentElement || point.node.parentNode);
        if (target && target.scrollIntoView) {
            target.scrollIntoView({behavior: 'smooth', block: 'center'});
            return true;
        }
        return false;
    }

    function openDatabase() {
        return new Promise(function (resolve, reject) {
            if (!window.indexedDB) {
                reject(new Error('INDEXED_DB_UNSUPPORTED'));
                return;
            }
            var request = window.indexedDB.open(DB_NAME, DB_VERSION);
            request.onupgradeneeded = function () {
                var database = request.result;
                if (!database.objectStoreNames.contains(STORE_NAME)) {
                    var store = database.createObjectStore(STORE_NAME, {keyPath: 'path'});
                    store.createIndex('bookId', 'bookId', {unique: false});
                    store.createIndex('savedAt', 'savedAt', {unique: false});
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    function storeFreeChapter(contentRoot) {
        var eligibility = document.getElementById('offlineEligible');
        if (!eligibility || eligibility.value !== 'true') {
            return Promise.reject(new Error('FREE_ONLY'));
        }
        var content = String(contentRoot.textContent || '').replace(/\s+/g, ' ').trim();
        if (!content) {
            return Promise.reject(new Error('EMPTY_CONTENT'));
        }
        if (content.length > 1500000) {
            return Promise.reject(new Error('CONTENT_TOO_LARGE'));
        }
        var snapshot = {
            path: window.location.pathname,
            bookId: valueOf(['bookId', 'bookIdHidden']),
            chapterId: valueOf(['preContentId', 'contentIdHidden']),
            bookName: valueOf(['bookName', 'bookNameHidden']),
            chapterName: valueOf(['preIndexName', 'indexNameHidden']),
            contentText: content,
            savedAt: new Date().toISOString()
        };
        return openDatabase().then(function (database) {
            return new Promise(function (resolve, reject) {
                var transaction = database.transaction(STORE_NAME, 'readwrite');
                transaction.objectStore(STORE_NAME).put(snapshot);
                transaction.oncomplete = function () {
                    database.close();
                    resolve(snapshot);
                };
                transaction.onerror = function () {
                    database.close();
                    reject(transaction.error);
                };
            });
        });
    }

    function preferredVietnameseVoices() {
        if (!speech || !speech.getVoices) {
            return [];
        }
        return speech.getVoices().filter(function (voice) {
            return /^vi([-_]|$)/i.test(voice.lang || '') || /vietnam/i.test(voice.name || '');
        });
    }

    function createButton(label, className) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = className || 'reader-tool-button';
        button.textContent = label;
        return button;
    }

    function addOption(select, value, label) {
        var option = document.createElement('option');
        option.value = value;
        option.textContent = label;
        select.appendChild(option);
    }

    function injectStyles() {
        if (document.getElementById('novelReaderToolsStyle')) {
            return;
        }
        var style = document.createElement('style');
        style.id = 'novelReaderToolsStyle';
        style.textContent = '.reader-tools{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:10px auto;padding:10px;max-width:1100px;border:1px solid rgba(22,80,57,.22);border-radius:8px;background:#f7fbf8;color:#173b2d;font:14px/1.4 Arial,sans-serif}.reader-tools button,.reader-tools select,.reader-tools a{min-height:36px;padding:6px 10px;border:1px solid #159957;border-radius:6px;background:#fff;color:#126b42;text-decoration:none;cursor:pointer}.reader-tools button:focus-visible,.reader-tools select:focus-visible,.reader-tools a:focus-visible,.reader-annotation-item button:focus-visible{outline:3px solid #ffbf47;outline-offset:2px}.reader-tools-status{flex:1 1 180px;min-width:160px}.reader-tools-label{display:inline-flex;align-items:center;gap:5px}.reader-tools [disabled]{opacity:.55;cursor:not-allowed}.reader-annotations{margin:0 auto 10px;padding:12px;max-width:1100px;border:1px solid rgba(22,80,57,.22);border-radius:8px;background:#fff;color:#173b2d;font:14px/1.4 Arial,sans-serif}.reader-annotations[hidden]{display:none}.reader-annotations h3{margin:0 0 10px;font-size:16px}.reader-annotation-item{display:flex;flex-wrap:wrap;gap:8px;align-items:flex-start;padding:9px 0;border-top:1px solid #e3eee8}.reader-annotation-item:first-of-type{border-top:0}.reader-annotation-jump{flex:1 1 220px;text-align:left;border:0;background:transparent;color:#173b2d;cursor:pointer}.reader-annotation-actions{display:flex;gap:6px}.reader-annotation-actions button{min-height:32px;padding:4px 8px;border:1px solid #159957;border-radius:5px;background:#fff;color:#126b42;cursor:pointer}@media(max-width:640px){.reader-tools,.reader-annotations{margin:8px 6px;padding:8px}.reader-tools button,.reader-tools select,.reader-tools a{flex:1 1 auto}.reader-annotation-actions{width:100%}}@media(prefers-reduced-motion:reduce){.reader-tools *,.reader-annotations *{scroll-behavior:auto!important;transition:none!important}}html[data-novel-data-saver="true"] img{content-visibility:auto}';
        document.head.appendChild(style);
    }

    function initialize() {
        if (!document.getElementById('readerContentAvailable')) {
            return;
        }
        var contentRoot = firstElement(['chaptercontent', 'showReading']);
        if (!contentRoot || !String(contentRoot.textContent || '').trim()) {
            return;
        }

        injectStyles();
        var toolbar = document.createElement('section');
        toolbar.className = 'reader-tools';
        toolbar.setAttribute('aria-label', message('readerTools', 'Công cụ đọc'));

        var status = document.createElement('span');
        status.className = 'reader-tools-status';
        status.setAttribute('role', 'status');
        status.setAttribute('aria-live', 'polite');
        status.textContent = message('readerTtsReady', 'Sẵn sàng đọc tiếng Việt.');

        var play = createButton(message('readerTtsPlay', 'Đọc'));
        var stop = createButton(message('readerTtsStop', 'Dừng'));
        stop.disabled = true;

        var rateLabel = document.createElement('label');
        rateLabel.className = 'reader-tools-label';
        rateLabel.appendChild(document.createTextNode(message('readerTtsRate', 'Tốc độ') + ' '));
        var rate = document.createElement('select');
        rate.setAttribute('aria-label', message('readerTtsRate', 'Tốc độ'));
        [0.75, 1, 1.25, 1.5].forEach(function (value) {
            var option = document.createElement('option');
            option.value = String(value);
            option.textContent = value + '×';
            if (value === 1) { option.selected = true; }
            rate.appendChild(option);
        });
        rateLabel.appendChild(rate);

        var voiceLabel = document.createElement('label');
        voiceLabel.className = 'reader-tools-label';
        voiceLabel.appendChild(document.createTextNode(message('readerTtsVoice', 'Giọng đọc') + ' '));
        var voiceSelect = document.createElement('select');
        voiceSelect.setAttribute('aria-label', message('readerTtsVoice', 'Giọng đọc'));
        voiceLabel.appendChild(voiceSelect);

        var lineHeightLabel = document.createElement('label');
        lineHeightLabel.className = 'reader-tools-label';
        lineHeightLabel.appendChild(document.createTextNode(message('readerLineHeight', 'Giãn dòng') + ' '));
        var lineHeight = document.createElement('select');
        lineHeight.setAttribute('aria-label', message('readerLineHeight', 'Giãn dòng'));
        ['1.5', '1.8', '2', '2.2'].forEach(function (value) {
            var option = document.createElement('option');
            option.value = value;
            option.textContent = value;
            lineHeight.appendChild(option);
        });
        try { lineHeight.value = window.localStorage.getItem(LINE_HEIGHT_KEY) || '1.8'; } catch (ignored) { lineHeight.value = '1.8'; }
        contentRoot.style.lineHeight = lineHeight.value;
        lineHeightLabel.appendChild(lineHeight);

        var originalAppearance = {
            fontFamily: contentRoot.style.fontFamily,
            backgroundColor: contentRoot.style.backgroundColor,
            color: contentRoot.style.color,
            colorScheme: contentRoot.style.colorScheme
        };

        var fontFamilyLabel = document.createElement('label');
        fontFamilyLabel.className = 'reader-tools-label';
        fontFamilyLabel.appendChild(document.createTextNode(message('readerFont', 'Phông chữ') + ' '));
        var fontFamily = document.createElement('select');
        fontFamily.setAttribute('aria-label', message('readerFont', 'Phông chữ'));
        addOption(fontFamily, 'theme', message('readerAppearanceDefault', 'Theo giao diện'));
        addOption(fontFamily, 'system', message('readerFontSystem', 'Không chân'));
        addOption(fontFamily, 'serif', message('readerFontSerif', 'Có chân'));
        addOption(fontFamily, 'readable', message('readerFontReadable', 'Dễ đọc'));
        try {
            var savedFontFamily = window.localStorage.getItem(FONT_FAMILY_KEY);
            var legacyFontFamily = window.localStorage.getItem('fontNum');
            fontFamily.value = savedFontFamily || (legacyFontFamily === '1' ? 'serif' :
                legacyFontFamily === '2' ? 'readable' : 'system');
        } catch (ignored) { fontFamily.value = 'system'; }
        fontFamilyLabel.appendChild(fontFamily);

        var backgroundLabel = document.createElement('label');
        backgroundLabel.className = 'reader-tools-label';
        backgroundLabel.appendChild(document.createTextNode(message('readerTheme', 'Giao diện đọc') + ' '));
        var background = document.createElement('select');
        background.setAttribute('aria-label', message('readerTheme', 'Giao diện đọc'));
        addOption(background, 'theme', message('readerAppearanceDefault', 'Theo giao diện'));
        addOption(background, 'light', message('readerThemeLight', 'Trắng'));
        addOption(background, 'sepia', message('readerThemeSepia', 'Giấy ngà'));
        addOption(background, 'night', message('readerThemeNight', 'Ban đêm'));
        try { background.value = window.localStorage.getItem(BACKGROUND_KEY) || 'theme'; }
        catch (ignored) { background.value = 'theme'; }
        backgroundLabel.appendChild(background);

        function applyAppearance() {
            contentRoot.style.fontFamily = FONT_FAMILIES[fontFamily.value] || originalAppearance.fontFamily;
            var selectedBackground = READER_BACKGROUNDS[background.value];
            contentRoot.style.backgroundColor = selectedBackground ?
                selectedBackground.backgroundColor : originalAppearance.backgroundColor;
            contentRoot.style.color = selectedBackground ? selectedBackground.color : originalAppearance.color;
            contentRoot.style.colorScheme = selectedBackground ?
                selectedBackground.colorScheme : originalAppearance.colorScheme;
        }
        applyAppearance();

        var saveOffline = createButton(message('readerOfflineSave', 'Lưu offline'));
        var eligibility = document.getElementById('offlineEligible');
        if (!eligibility || eligibility.value !== 'true') {
            saveOffline.disabled = true;
            saveOffline.title = message('readerOfflineFreeOnly', 'Chỉ chương miễn phí được lưu offline.');
        }

        var library = document.createElement('a');
        library.href = '/offline-reader.htm';
        library.textContent = message('readerOfflineLibrary', 'Thư viện offline');

        var dataSaver = createButton('');
        function refreshDataSaverLabel() {
            var enabled = !!(window.NovelPwa && window.NovelPwa.isDataSaverEnabled());
            dataSaver.textContent = enabled ?
                message('readerDataSaverOn', 'Tiết kiệm dữ liệu: Bật') :
                message('readerDataSaverOff', 'Tiết kiệm dữ liệu: Tắt');
            dataSaver.setAttribute('aria-pressed', enabled ? 'true' : 'false');
        }
        refreshDataSaverLabel();

        var bookId = valueOf(['bookId', 'bookIdHidden']);
        var chapterId = valueOf(['preContentId', 'contentIdHidden']);
        var readingHeartbeatEnabled = valueOf(['readingHeartbeatEnabled']) === 'true';
        var readerState = {authenticated: null, progress: null, annotations: []};
        var readingHeartbeat = {
            activeSeconds: 0,
            activityTimer: null,
            heartbeatTimer: null,
            inFlight: false,
            lastSampleAt: 0,
            nextSequence: 0,
            pending: null,
            retryTimer: null,
            sessionId: null,
            started: false,
            stopped: false,
            wasActive: false
        };
        var savePosition = createButton(message('readerSyncSave', 'Lưu vị trí'));
        var addBookmark = createButton(message('readerBookmarkAdd', 'Đánh dấu đoạn'));
        var addNote = createButton(message('readerNoteAdd', 'Ghi chú'));
        var showAnnotations = createButton(message('readerAnnotationsShow', 'Xem dấu trang/ghi chú'));
        showAnnotations.setAttribute('aria-controls', 'readerAnnotationsPanel');
        showAnnotations.setAttribute('aria-expanded', 'false');
        var resume = createButton(message('readerSyncResume', 'Tiếp tục từ vị trí đã lưu'));
        resume.hidden = true;

        play.setAttribute('aria-keyshortcuts', 'Alt+R');
        stop.setAttribute('aria-keyshortcuts', 'Alt+S');
        saveOffline.setAttribute('aria-keyshortcuts', 'Alt+O');
        dataSaver.setAttribute('aria-keyshortcuts', 'Alt+D');
        savePosition.setAttribute('aria-keyshortcuts', 'Alt+P');
        addBookmark.setAttribute('aria-keyshortcuts', 'Alt+B');
        addNote.setAttribute('aria-keyshortcuts', 'Alt+N');

        var annotationsPanel = document.createElement('section');
        annotationsPanel.className = 'reader-annotations';
        annotationsPanel.id = 'readerAnnotationsPanel';
        annotationsPanel.hidden = true;
        annotationsPanel.setAttribute('aria-labelledby', 'readerAnnotationsTitle');
        var annotationsTitle = document.createElement('h3');
        annotationsTitle.id = 'readerAnnotationsTitle';
        annotationsTitle.textContent = message('readerAnnotations', 'Dấu trang và ghi chú');
        var annotationsList = document.createElement('div');
        annotationsPanel.appendChild(annotationsTitle);
        annotationsPanel.appendChild(annotationsList);

        function setReaderStateEnabled(enabled) {
            savePosition.disabled = !enabled;
            addBookmark.disabled = !enabled;
            addNote.disabled = !enabled;
            showAnnotations.disabled = !enabled;
            if (!enabled) { resume.hidden = true; }
        }

        function isSuccessful(result) {
            return result && result.code === 200;
        }

        function isReadingActive() {
            return document.visibilityState !== 'hidden' &&
                (typeof document.hasFocus !== 'function' || document.hasFocus());
        }

        function sampleReadingActivity() {
            if (!readingHeartbeat.started || readingHeartbeat.stopped) { return; }
            var now = Date.now();
            var elapsedSeconds = Math.floor((now - readingHeartbeat.lastSampleAt) / 1000);
            if (elapsedSeconds > 0 && readingHeartbeat.wasActive) {
                readingHeartbeat.activeSeconds += Math.min(elapsedSeconds, 2);
            }
            readingHeartbeat.lastSampleAt = now;
            readingHeartbeat.wasActive = isReadingActive();
        }

        function stopReadingHeartbeat() {
            readingHeartbeat.stopped = true;
            readingHeartbeat.pending = null;
            window.clearInterval(readingHeartbeat.activityTimer);
            window.clearInterval(readingHeartbeat.heartbeatTimer);
            window.clearTimeout(readingHeartbeat.retryTimer);
            readingHeartbeat.activityTimer = null;
            readingHeartbeat.heartbeatTimer = null;
            readingHeartbeat.retryTimer = null;
        }

        function heartbeatPayload(force) {
            if (readingHeartbeat.pending) { return readingHeartbeat.pending; }
            if (readingHeartbeat.nextSequence !== 0 && !force &&
                readingHeartbeat.activeSeconds < READING_HEARTBEAT_MIN_ACTIVE_SECONDS) {
                return null;
            }
            var activeSeconds = readingHeartbeat.nextSequence === 0 ? 0 :
                Math.min(Math.floor(readingHeartbeat.activeSeconds),
                    READING_HEARTBEAT_MAX_ACTIVE_SECONDS);
            if (readingHeartbeat.nextSequence !== 0 && activeSeconds <= 0) { return null; }
            readingHeartbeat.activeSeconds -= activeSeconds;
            readingHeartbeat.pending = {
                sessionId: readingHeartbeat.sessionId,
                bookId: bookId,
                bookIndexId: chapterId,
                sequence: readingHeartbeat.nextSequence,
                activeSeconds: activeSeconds
            };
            return readingHeartbeat.pending;
        }

        function scheduleHeartbeatRetry() {
            if (readingHeartbeat.stopped || readingHeartbeat.retryTimer) { return; }
            readingHeartbeat.retryTimer = window.setTimeout(function () {
                readingHeartbeat.retryTimer = null;
                sendReadingHeartbeat(false, false);
            }, READING_HEARTBEAT_RETRY_MS);
        }

        function isTerminalHeartbeatResult(result) {
            return result && (result.code === 1001 || result.code === 7001 ||
                result.code === 7007 || result.code === 7016);
        }

        function validHeartbeatResponse(payload, data) {
            return data && data.sessionId === payload.sessionId &&
                Number(data.nextSequence) === payload.sequence + 1;
        }

        function sendReadingHeartbeat(force, keepalive) {
            if (!readingHeartbeat.started || readingHeartbeat.stopped || readingHeartbeat.inFlight) {
                return Promise.resolve(null);
            }
            var payload = heartbeatPayload(force);
            if (!payload) { return Promise.resolve(null); }
            readingHeartbeat.inFlight = true;
            return requestJson('/user/gamification/reading-heartbeat', {
                method: 'POST',
                body: JSON.stringify(payload),
                keepalive: !!keepalive
            }).then(function (result) {
                readingHeartbeat.inFlight = false;
                if (isSuccessful(result) && validHeartbeatResponse(payload, result.data)) {
                    readingHeartbeat.pending = null;
                    readingHeartbeat.nextSequence = Number(result.data.nextSequence);
                    if (result.data.capReached === true) { stopReadingHeartbeat(); }
                    return result.data;
                }
                if (isTerminalHeartbeatResult(result)) {
                    stopReadingHeartbeat();
                    return null;
                }
                scheduleHeartbeatRetry();
                return null;
            }).catch(function () {
                readingHeartbeat.inFlight = false;
                scheduleHeartbeatRetry();
                return null;
            });
        }

        function startReadingHeartbeat() {
            if (!readingHeartbeatEnabled || readingHeartbeat.started || readerState.authenticated !== true ||
                !bookId || !chapterId || !window.fetch) {
                return;
            }
            readingHeartbeat.started = true;
            readingHeartbeat.sessionId = readingSessionId();
            readingHeartbeat.lastSampleAt = Date.now();
            readingHeartbeat.wasActive = isReadingActive();
            readingHeartbeat.activityTimer = window.setInterval(
                sampleReadingActivity, READING_HEARTBEAT_SAMPLE_MS);
            readingHeartbeat.heartbeatTimer = window.setInterval(function () {
                sampleReadingActivity();
                sendReadingHeartbeat(false, false);
            }, READING_HEARTBEAT_INTERVAL_MS);
            sendReadingHeartbeat(true, false);
        }

        function markLoginRequired(result) {
            if (result && result.code === 1001) {
                readerState.authenticated = false;
                stopReadingHeartbeat();
                setReaderStateEnabled(false);
                status.textContent = message('readerSyncLogin', 'Đăng nhập để đồng bộ vị trí và ghi chú.');
                return true;
            }
            return false;
        }

        function progressPayload() {
            var anchor = currentAnchor(contentRoot);
            return {
                bookId: bookId,
                bookIndexId: chapterId,
                paragraphIndex: anchor.paragraphIndex,
                characterOffset: anchor.characterOffset,
                progressPercent: anchor.progressPercent
            };
        }

        function saveReaderProgress(silent, keepalive) {
            if (readerState.authenticated !== true || !bookId || !chapterId) {
                return Promise.resolve(null);
            }
            return requestJson('/user/reader-state/progress', {
                method: 'PUT',
                body: JSON.stringify(progressPayload()),
                keepalive: !!keepalive
            }).then(function (result) {
                if (isSuccessful(result)) {
                    readerState.progress = result.data;
                    if (!silent) { status.textContent = message('readerSyncSaved', 'Đã đồng bộ vị trí đọc.'); }
                    return result.data;
                }
                if (!markLoginRequired(result) && !silent) {
                    status.textContent = result && result.msg ? result.msg : message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                }
                return null;
            }).catch(function () {
                if (!silent) { status.textContent = message('readerSyncError', 'Không thể đồng bộ vị trí đọc.'); }
                return null;
            });
        }

        function annotationLabel(item) {
            var type = item.type === 'NOTE' ?
                message('readerAnnotationNote', 'Ghi chú') : message('readerAnnotationBookmark', 'Dấu trang');
            var body = item.noteText || item.selectedText || '';
            return type + (body ? ': ' + body : '');
        }

        function replaceAnnotation(updated) {
            for (var index = 0; index < readerState.annotations.length; index++) {
                if (readerState.annotations[index].id === updated.id) {
                    readerState.annotations[index] = updated;
                    return;
                }
            }
            readerState.annotations.push(updated);
        }

        function renderAnnotations() {
            annotationsList.textContent = '';
            if (!readerState.annotations.length) {
                var empty = document.createElement('p');
                empty.textContent = message('readerAnnotationsEmpty', 'Chưa có dấu trang hoặc ghi chú trong chương này.');
                annotationsList.appendChild(empty);
                return;
            }
            readerState.annotations.forEach(function (item) {
                var row = document.createElement('div');
                row.className = 'reader-annotation-item';
                var jump = createButton(annotationLabel(item), 'reader-annotation-jump');
                jump.addEventListener('click', function () {
                    scrollToTextOffset(contentRoot, item.characterOffset);
                });
                var actions = document.createElement('div');
                actions.className = 'reader-annotation-actions';
                if (item.type === 'NOTE') {
                    var edit = createButton(message('readerNoteEdit', 'Sửa ghi chú'));
                    edit.addEventListener('click', function () {
                        var note = window.prompt(message('readerNotePrompt', 'Nhập ghi chú cho đoạn đã chọn:'), item.noteText || '');
                        if (note === null) { return; }
                        requestJson('/user/reader-state/annotations/' + encodeURIComponent(item.id), {
                            method: 'PUT',
                            body: JSON.stringify({noteText: note, expectedVersion: item.version})
                        }).then(function (result) {
                            if (isSuccessful(result)) {
                                replaceAnnotation(result.data);
                                renderAnnotations();
                            } else if (!markLoginRequired(result)) {
                                status.textContent = result && result.msg ? result.msg : message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                            }
                        }).catch(function () { status.textContent = message('readerSyncError', 'Không thể đồng bộ vị trí đọc.'); });
                    });
                    actions.appendChild(edit);
                }
                var remove = createButton(message('readerAnnotationDelete', 'Xóa'));
                remove.addEventListener('click', function () {
                    requestJson('/user/reader-state/annotations/' + encodeURIComponent(item.id) +
                        '?expectedVersion=' + encodeURIComponent(item.version), {method: 'DELETE'})
                        .then(function (result) {
                            if (isSuccessful(result)) {
                                readerState.annotations = readerState.annotations.filter(function (candidate) {
                                    return candidate.id !== item.id;
                                });
                                renderAnnotations();
                                status.textContent = message('readerAnnotationDeleted', 'Đã xóa dấu trang hoặc ghi chú.');
                            } else if (!markLoginRequired(result)) {
                                status.textContent = result && result.msg ? result.msg : message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                            }
                        }).catch(function () { status.textContent = message('readerSyncError', 'Không thể đồng bộ vị trí đọc.'); });
                });
                actions.appendChild(remove);
                row.appendChild(jump);
                row.appendChild(actions);
                annotationsList.appendChild(row);
            });
        }

        function createAnnotation(type, anchor, noteText) {
            if (readerState.authenticated !== true) {
                status.textContent = message('readerSyncLogin', 'Đăng nhập để đồng bộ vị trí và ghi chú.');
                return;
            }
            requestJson('/user/reader-state/annotations', {
                method: 'POST',
                body: JSON.stringify({
                    bookId: bookId,
                    bookIndexId: chapterId,
                    type: type,
                    paragraphIndex: anchor.paragraphIndex,
                    characterOffset: anchor.characterOffset,
                    selectedText: anchor.selectedText,
                    noteText: noteText || null
                })
            }).then(function (result) {
                if (isSuccessful(result)) {
                    replaceAnnotation(result.data);
                    renderAnnotations();
                    status.textContent = type === 'NOTE' ?
                        message('readerNoteSaved', 'Đã lưu ghi chú.') : message('readerBookmarkSaved', 'Đã đánh dấu đoạn này.');
                } else if (!markLoginRequired(result)) {
                    status.textContent = result && result.msg ? result.msg : message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                }
            }).catch(function () { status.textContent = message('readerSyncError', 'Không thể đồng bộ vị trí đọc.'); });
        }

        function loadReaderState() {
            if (!bookId || !chapterId || !window.fetch) {
                setReaderStateEnabled(false);
                return;
            }
            requestJson('/user/reader-state?bookId=' + encodeURIComponent(bookId) +
                '&bookIndexId=' + encodeURIComponent(chapterId), {method: 'GET'})
                .then(function (result) {
                    if (isSuccessful(result)) {
                        readerState.authenticated = true;
                        readerState.progress = result.data && result.data.progress;
                        readerState.annotations = result.data && result.data.annotations || [];
                        setReaderStateEnabled(true);
                        renderAnnotations();
                        startReadingHeartbeat();
                        if (readerState.progress && readerState.progress.bookIndexId === chapterId &&
                            readerState.progress.characterOffset > 0) {
                            resume.hidden = false;
                        }
                    } else if (!markLoginRequired(result)) {
                        setReaderStateEnabled(false);
                        status.textContent = result && result.msg ? result.msg : message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                    }
                }).catch(function () {
                    setReaderStateEnabled(false);
                    status.textContent = message('readerSyncError', 'Không thể đồng bộ vị trí đọc.');
                });
        }

        function loadVoices() {
            var voices = preferredVietnameseVoices();
            voiceSelect.textContent = '';
            var automatic = document.createElement('option');
            automatic.value = '';
            automatic.textContent = message('readerTtsVoiceAuto', 'Tự động');
            voiceSelect.appendChild(automatic);
            voices.forEach(function (voice, index) {
                var option = document.createElement('option');
                option.value = String(index);
                option.textContent = voice.name + ' (' + voice.lang + ')';
                voiceSelect.appendChild(option);
            });
        }
        loadVoices();
        if (speech) { speech.onvoiceschanged = loadVoices; }

        function setStoppedStatus(text) {
            play.textContent = message('readerTtsPlay', 'Đọc');
            stop.disabled = true;
            status.textContent = text;
        }

        function cancelSpeechWithoutErrorStatus() {
            if (utterance) {
                utterance.onend = null;
                utterance.onerror = null;
                utterance = null;
            }
            if (speech) { speech.cancel(); }
        }

        play.addEventListener('click', function () {
            if (!speech || !window.SpeechSynthesisUtterance) {
                status.textContent = message('readerTtsUnsupported', 'Trình duyệt không hỗ trợ đọc văn bản.');
                return;
            }
            if (speech.speaking && !speech.paused) {
                speech.pause();
                play.textContent = message('readerTtsResume', 'Tiếp tục');
                status.textContent = message('readerTtsPaused', 'Đã tạm dừng.');
                return;
            }
            if (speech.speaking && speech.paused) {
                speech.resume();
                play.textContent = message('readerTtsPause', 'Tạm dừng');
                status.textContent = message('readerTtsSpeaking', 'Đang đọc chương.');
                return;
            }
            cancelSpeechWithoutErrorStatus();
            utterance = new window.SpeechSynthesisUtterance(String(contentRoot.textContent || '').trim());
            utterance.lang = 'vi-VN';
            utterance.rate = Number(rate.value) || 1;
            var voices = preferredVietnameseVoices();
            if (voiceSelect.value !== '' && voices[Number(voiceSelect.value)]) {
                utterance.voice = voices[Number(voiceSelect.value)];
            } else if (voices.length) {
                utterance.voice = voices[0];
            }
            utterance.onend = function () { setStoppedStatus(message('readerTtsFinished', 'Đã đọc xong chương.')); };
            utterance.onerror = function () { setStoppedStatus(message('readerTtsError', 'Không thể đọc chương này.')); };
            speech.speak(utterance);
            play.textContent = message('readerTtsPause', 'Tạm dừng');
            stop.disabled = false;
            status.textContent = message('readerTtsSpeaking', 'Đang đọc chương.');
        });

        stop.addEventListener('click', function () {
            cancelSpeechWithoutErrorStatus();
            setStoppedStatus(message('readerTtsStopped', 'Đã dừng đọc.'));
        });

        lineHeight.addEventListener('change', function () {
            contentRoot.style.lineHeight = lineHeight.value;
            try { window.localStorage.setItem(LINE_HEIGHT_KEY, lineHeight.value); } catch (ignored) {}
        });

        fontFamily.addEventListener('change', function () {
            applyAppearance();
            try { window.localStorage.setItem(FONT_FAMILY_KEY, fontFamily.value); } catch (ignored) {}
        });

        background.addEventListener('change', function () {
            applyAppearance();
            try { window.localStorage.setItem(BACKGROUND_KEY, background.value); } catch (ignored) {}
        });

        saveOffline.addEventListener('click', function () {
            saveOffline.disabled = true;
            status.textContent = message('readerOfflineSaving', 'Đang lưu chương...');
            storeFreeChapter(contentRoot).then(function () {
                status.textContent = message('readerOfflineSaved', 'Đã lưu chương để đọc offline.');
            }).catch(function (error) {
                status.textContent = error && error.message === 'FREE_ONLY' ?
                    message('readerOfflineFreeOnly', 'Chỉ chương miễn phí được lưu offline.') :
                    message('readerOfflineError', 'Không thể lưu chương offline.');
            }).then(function () { saveOffline.disabled = false; });
        });

        dataSaver.addEventListener('click', function () {
            if (!window.NovelPwa) { return; }
            window.NovelPwa.setDataSaverEnabled(!window.NovelPwa.isDataSaverEnabled());
            refreshDataSaverLabel();
        });
        window.addEventListener('novel:data-saver-changed', refreshDataSaverLabel);

        savePosition.addEventListener('click', function () { saveReaderProgress(false, false); });
        addBookmark.addEventListener('click', function () {
            createAnnotation('BOOKMARK', currentAnchor(contentRoot), null);
        });
        addNote.addEventListener('click', function () {
            var anchor = selectedAnchor(contentRoot);
            if (!anchor) {
                status.textContent = message('readerNoteSelection', 'Hãy chọn một đoạn văn trước khi tạo ghi chú.');
                return;
            }
            var note = window.prompt(message('readerNotePrompt', 'Nhập ghi chú cho đoạn đã chọn:'), '');
            if (note === null) { return; }
            createAnnotation('NOTE', anchor, note);
        });
        showAnnotations.addEventListener('click', function () {
            annotationsPanel.hidden = !annotationsPanel.hidden;
            showAnnotations.setAttribute('aria-expanded', annotationsPanel.hidden ? 'false' : 'true');
            showAnnotations.textContent = annotationsPanel.hidden ?
                message('readerAnnotationsShow', 'Xem dấu trang/ghi chú') :
                message('readerAnnotationsHide', 'Ẩn dấu trang/ghi chú');
        });
        resume.addEventListener('click', function () {
            if (readerState.progress) {
                scrollToTextOffset(contentRoot, readerState.progress.characterOffset);
            }
        });

        toolbar.appendChild(play);
        toolbar.appendChild(stop);
        toolbar.appendChild(rateLabel);
        toolbar.appendChild(voiceLabel);
        toolbar.appendChild(fontFamilyLabel);
        toolbar.appendChild(backgroundLabel);
        toolbar.appendChild(lineHeightLabel);
        toolbar.appendChild(saveOffline);
        toolbar.appendChild(library);
        toolbar.appendChild(dataSaver);
        toolbar.appendChild(savePosition);
        toolbar.appendChild(addBookmark);
        toolbar.appendChild(addNote);
        toolbar.appendChild(showAnnotations);
        toolbar.appendChild(resume);
        toolbar.appendChild(status);
        contentRoot.parentNode.insertBefore(toolbar, contentRoot);
        contentRoot.parentNode.insertBefore(annotationsPanel, contentRoot);

        setReaderStateEnabled(false);
        loadReaderState();

        var progressLine = document.getElementById('chapterProgressLine');
        if (!progressLine) {
            progressLine = document.createElement('div');
            progressLine.id = 'chapterProgressLine';
            document.body.appendChild(progressLine);
        }

        var progressSaveTimer = null;
        window.addEventListener('scroll', function () {
            var scrollTotal = document.documentElement.scrollHeight - window.innerHeight;
            if (scrollTotal > 0 && progressLine) {
                var pct = Math.min(100, Math.max(0, (window.scrollY / scrollTotal) * 100));
                progressLine.style.width = pct + '%';
            }
            if (readerState.authenticated !== true) { return; }
            window.clearTimeout(progressSaveTimer);
            progressSaveTimer = window.setTimeout(function () { saveReaderProgress(true, false); }, 2500);
        }, {passive: true});

        document.addEventListener('keydown', function (event) {
            if (!event.altKey || /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) { return; }
            var key = String(event.key || '').toLowerCase();
            if (key === 'r') { event.preventDefault(); play.click(); }
            if (key === 's') { event.preventDefault(); stop.click(); }
            if (key === 'o' && !saveOffline.disabled) { event.preventDefault(); saveOffline.click(); }
            if (key === 'd') { event.preventDefault(); dataSaver.click(); }
            if (key === 'p' && !savePosition.disabled) { event.preventDefault(); savePosition.click(); }
            if (key === 'b' && !addBookmark.disabled) { event.preventDefault(); addBookmark.click(); }
            if (key === 'n' && !addNote.disabled) { event.preventDefault(); addNote.click(); }
        });

        window.addEventListener('pagehide', function () {
            cancelSpeechWithoutErrorStatus();
            saveReaderProgress(true, true);
            sampleReadingActivity();
            sendReadingHeartbeat(true, true);
        });

        document.addEventListener('visibilitychange', function () {
            sampleReadingActivity();
            if (document.visibilityState === 'hidden') {
                sendReadingHeartbeat(true, true);
            }
        });
    }

    window.NovelReaderTools = {initialize: initialize, storeFreeChapter: storeFreeChapter};
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
}(window, document));
