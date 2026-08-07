(function (window, document) {
    'use strict';

    function message(key, fallback) {
        return window.NovelI18n && window.NovelI18n[key] ? window.NovelI18n[key] : fallback;
    }

    function validId(value) {
        var id = String(value == null ? '' : value);
        return /^\d+$/.test(id) ? id : null;
    }

    function readHistory() {
        try {
            return JSON.parse(window.localStorage.getItem('recent_read_history'));
        } catch (error) {
            return null;
        }
    }

    function renderContinueReading() {
        var container = document.getElementById('continueReadingWidget');
        if (!container) return;

        var history = readHistory();
        var bookId = validId(history && history.bookId);
        var contentId = validId(history && history.contentId);
        if (!history || !history.bookName || !bookId || !contentId) return;

        var bar = document.createElement('div');
        bar.className = 'continue-reading-bar glass-card cf';
        var info = document.createElement('div');
        info.className = 'cr-info fl';
        var badge = document.createElement('span');
        badge.className = 'cr-badge';
        badge.textContent = message('currentlyReading', 'Đang đọc dở');
        var title = document.createElement('strong');
        title.className = 'cr-title';
        title.textContent = String(history.bookName);
        var chapter = document.createElement('span');
        chapter.className = 'cr-chapter';
        chapter.textContent = String(history.indexName || message('lastReadChapter', 'Chương vừa đọc'));
        info.append(badge, title, chapter);

        var action = document.createElement('div');
        action.className = 'cr-action fr';
        var link = document.createElement('a');
        link.className = 'cr-btn btn_ora';
        link.href = '/book/' + encodeURIComponent(bookId) + '/' + encodeURIComponent(contentId) + '.html';
        link.textContent = message('continueReading', 'Đọc tiếp') + ' →';
        action.appendChild(link);
        bar.append(info, action);
        container.replaceChildren(bar);
        container.style.display = 'block';
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', renderContinueReading, {once: true});
    } else {
        renderContinueReading();
    }
})(window, document);
