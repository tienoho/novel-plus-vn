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

    function createRankItem(book, index) {
        var dom = window.NovelSafeDom;
        var bookId = dom.positiveId(book && book.id);
        if (!bookId) return null;
        var href = '/book/' + bookId + '.html';
        var itemClass = index === 0 ? 'on' : '';
        if (index < 3) itemClass += (itemClass ? ' ' : '') + 'num' + (index + 1);
        var item = dom.element('li', itemClass);
        var name = dom.element('div', 'book_name');
        name.appendChild(dom.element('i', null, index + 1));
        name.appendChild(dom.link(href, 'name', book.bookName));
        item.appendChild(name);

        var intro = dom.element('div', 'book_intro');
        var cover = dom.element('div', 'cover');
        var coverLink = dom.link(href);
        if (index === 0) {
            var image = dom.image(book.picUrl, book.bookName, '/images/default.gif');
            image.width = 84;
            image.height = 112;
            coverLink.appendChild(image);
        }
        cover.appendChild(coverLink);
        intro.appendChild(cover);
        intro.appendChild(dom.link(href, 'txt', book.bookDesc));
        item.appendChild(intro);
        return item;
    }

    function renderRankBooks(targetId, books, limit) {
        var source = Array.isArray(books) ? books : [];
        var fragment = document.createDocumentFragment();
        source.slice(0, limit || source.length).forEach(function (book, index) {
            var item = createRankItem(book, index);
            if (item) fragment.appendChild(item);
        });
        document.getElementById(targetId).replaceChildren(fragment);
    }

    function renderUpdateTable(targetId, books) {
        var dom = window.NovelSafeDom;
        var fragment = document.createDocumentFragment();
        (Array.isArray(books) ? books : []).forEach(function (book) {
            var bookId = dom.positiveId(book && book.id);
            if (!bookId) return;
            var href = '/book/' + bookId + '.html';
            var row = dom.element('tr');
            var category = dom.element('td', 'style');
            var categoryId = dom.positiveId(book.catId);
            var categoryText = '[' + dom.text(book.catName) + ']';
            category.appendChild(categoryId
                ? dom.link('/book/bookclass.html?c=' + categoryId, null, categoryText)
                : dom.element('span', null, categoryText));
            row.appendChild(category);
            var name = dom.element('td', 'name');
            name.appendChild(dom.link(href, null, book.bookName));
            row.appendChild(name);
            var chapter = dom.element('td', 'chapter');
            chapter.appendChild(dom.link(href, null, book.lastIndexName));
            chapter.appendChild(dom.element('i'));
            row.appendChild(chapter);
            var author = dom.element('td', 'author');
            author.appendChild(dom.element('span', null, book.authorName));
            row.appendChild(author);
            row.appendChild(dom.element('td', 'time', book.lastIndexUpdateTime));
            fragment.appendChild(row);
        });
        document.getElementById(targetId).replaceChildren(fragment);
    }

    function renderFriendLinks(targetId, links) {
        var dom = window.NovelSafeDom;
        var fragment = document.createDocumentFragment();
        (Array.isArray(links) ? links : []).forEach(function (item) {
            var href = dom.safeUrl(item && item.linkUrl, null);
            if (!href) return;
            var link = dom.link(href, null, item.linkName);
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            fragment.appendChild(link);
        });
        document.getElementById(targetId).replaceChildren(fragment);
    }

    function loadCollection(url, onSuccess, networkErrorText) {
        window.jQuery.ajax({
            type: 'get',
            url: url,
            data: {},
            dataType: 'json',
            success: function (data) {
                if (data.code === 200) {
                    onSuccess(data.data);
                } else {
                    window.novelAlertText(data.msg);
                }
            },
            error: function () {
                window.layer.alert(networkErrorText);
            }
        });
    }

    function load(networkErrorText) {
        loadCollection('/book/listClickRank', function (books) {
            renderRankBooks('clickRankBooks', books);
        }, networkErrorText);
        loadCollection('/book/listNewRank', function (books) {
            renderRankBooks('newRankBooks', books);
        }, networkErrorText);
        loadCollection('/book/listUpdateRank', function (books) {
            renderRankBooks('updateRankBooks', books, 10);
            renderUpdateTable('newRankBooks2', books);
        }, networkErrorText);
        loadCollection('/friendLink/listIndexLink', function (links) {
            renderFriendLinks('friendLink', links);
        }, networkErrorText);
    }

    window.NovelIndexRenderer = Object.freeze({
        renderRankBooks: renderRankBooks,
        renderUpdateTable: renderUpdateTable,
        renderFriendLinks: renderFriendLinks,
        load: load
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', renderContinueReading, {once: true});
    } else {
        renderContinueReading();
    }
})(window, document);
