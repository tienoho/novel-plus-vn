(function (window, document) {
    'use strict';

    var DB_NAME = 'novel-reader-offline-v1';
    var STORE_NAME = 'chapters';
    var DB_VERSION = 1;
    var LINE_HEIGHT_KEY = 'novel:reader:line-height:v1';
    var speech = window.speechSynthesis;
    var utterance = null;

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

    function injectStyles() {
        if (document.getElementById('novelReaderToolsStyle')) {
            return;
        }
        var style = document.createElement('style');
        style.id = 'novelReaderToolsStyle';
        style.textContent = '.reader-tools{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:10px auto;padding:10px;max-width:1100px;border:1px solid rgba(22,80,57,.22);border-radius:8px;background:#f7fbf8;color:#173b2d;font:14px/1.4 Arial,sans-serif}.reader-tools button,.reader-tools select,.reader-tools a{min-height:36px;padding:6px 10px;border:1px solid #159957;border-radius:6px;background:#fff;color:#126b42;text-decoration:none;cursor:pointer}.reader-tools button:focus-visible,.reader-tools select:focus-visible,.reader-tools a:focus-visible{outline:3px solid #ffbf47;outline-offset:2px}.reader-tools-status{flex:1 1 180px;min-width:160px}.reader-tools-label{display:inline-flex;align-items:center;gap:5px}.reader-tools [disabled]{opacity:.55;cursor:not-allowed}@media(max-width:640px){.reader-tools{margin:8px 6px;padding:8px}.reader-tools button,.reader-tools select,.reader-tools a{flex:1 1 auto}}@media(prefers-reduced-motion:reduce){.reader-tools *{scroll-behavior:auto!important;transition:none!important}}html[data-novel-data-saver="true"] img{content-visibility:auto}';
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

        toolbar.appendChild(play);
        toolbar.appendChild(stop);
        toolbar.appendChild(rateLabel);
        toolbar.appendChild(voiceLabel);
        toolbar.appendChild(lineHeightLabel);
        toolbar.appendChild(saveOffline);
        toolbar.appendChild(library);
        toolbar.appendChild(dataSaver);
        toolbar.appendChild(status);
        contentRoot.parentNode.insertBefore(toolbar, contentRoot);

        document.addEventListener('keydown', function (event) {
            if (!event.altKey || /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)) { return; }
            var key = String(event.key || '').toLowerCase();
            if (key === 'r') { event.preventDefault(); play.click(); }
            if (key === 's') { event.preventDefault(); stop.click(); }
            if (key === 'o' && !saveOffline.disabled) { event.preventDefault(); saveOffline.click(); }
            if (key === 'd') { event.preventDefault(); dataSaver.click(); }
        });

        window.addEventListener('pagehide', cancelSpeechWithoutErrorStatus);
    }

    window.NovelReaderTools = {initialize: initialize, storeFreeChapter: storeFreeChapter};
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
}(window, document));
