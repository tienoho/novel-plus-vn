(function (window, document, $) {
    'use strict';

    var root = document.getElementById('mobileFavoritesPage');
    if (!root || !$) return;

    var messages = {
        author: root.getAttribute('data-author-label') || '',
        category: root.getAttribute('data-category-label') || '',
        status: root.getAttribute('data-status-label') || '',
        serializing: root.getAttribute('data-serializing-label') || '',
        completed: root.getAttribute('data-completed-label') || '',
        updated: root.getAttribute('data-updated-label') || '',
        description: root.getAttribute('data-description-label') || '',
        network: root.getAttribute('data-network-error') || ''
    };
    var longPressTimer = null;
    var longPressTriggered = false;
    var selectedItem = null;

    function numeric(value) {
        var result = String(value == null ? '' : value);
        return /^\d+$/.test(result) ? result : null;
    }

    function positiveInteger(value, fallback) {
        var parsed = Number(value);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
    }

    function safeCount(value) {
        var parsed = Number(value);
        return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : 0;
    }

    function safeImage(value) {
        try {
            var url = new URL(String(value == null ? '' : value), window.location.origin);
            if (url.protocol !== 'http:' && url.protocol !== 'https:') return '/images/default.gif';
            return url.origin === window.location.origin ? url.pathname + url.search : url.href;
        } catch (_error) {
            return '/images/default.gif';
        }
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = String(text);
        return node;
    }

    function labelText(label, value) {
        return label + ': ' + String(value == null ? '' : value);
    }

    function plainDescription(value) {
        return String(value == null ? '' : value)
            .replace(/<[^>]*>/g, '')
            .replace(/&nbsp;/gi, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function renderBook(book) {
        var bookId = numeric(book.bookId);
        var contentId = numeric(book.preContentId);
        if (!bookId || !contentId) return null;

        var item = element('div', 'item layui-row mobile-favorites-item');
        item.id = 'shelf-' + bookId;
        item.setAttribute('data-book-id', bookId);
        item.setAttribute('data-content-id', contentId);

        var coverColumn = element('div', 'layui-col-xs6 layui-col-sm3 layui-col-md2 layui-col-lg2 mobile-favorites-cover');
        var cover = element('img');
        cover.alt = String(book.bookName == null ? '' : book.bookName);
        cover.src = safeImage(book.picUrl);
        coverColumn.appendChild(cover);
        item.appendChild(coverColumn);

        var details = element('div', 'layui-col-xs6 layui-col-sm8 layui-col-md8 layui-col-lg8 mobile-favorites-details');
        details.appendChild(element('div', 'mobile-favorites-title', book.bookName));
        details.appendChild(element('div', 'mobile-favorites-author', labelText(messages.author, book.authorName)));
        details.appendChild(element('div', 'mobile-favorites-meta', labelText(messages.category, book.catName)));
        details.appendChild(element('div', 'mobile-favorites-meta', labelText(messages.status,
            String(book.bookStatus) === '0' ? messages.serializing : messages.completed)));

        var updated = element('div', 'mobile-favorites-meta', messages.updated + ': ');
        updated.appendChild(element('i', 'mobile-favorites-updated', String(book.lastIndexUpdateTime == null ? '' : book.lastIndexUpdateTime).slice(0, 11)));
        details.appendChild(updated);

        var description = plainDescription(book.bookDesc);
        if (description.length > 15) description = description.slice(0, 15) + '...';
        details.appendChild(element('div', 'mobile-favorites-meta', labelText(messages.description, description)));
        item.appendChild(details);
        return item;
    }

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(String(message || ''));
    }

    function hideTip() {
        longPressTriggered = false;
        selectedItem = null;
        $('#tipLayer').hide();
    }

    function showTip(touch, item) {
        longPressTriggered = true;
        selectedItem = item;
        var tip = $('#tipLayer');
        tip.css({
            top: touch.pageY - 100,
            left: touch.pageX - (tip.outerWidth() / 2)
        }).show();
    }

    function removeSelectedBook() {
        if (!selectedItem) return;
        var item = selectedItem;
        var bookId = numeric(item.getAttribute('data-book-id'));
        if (!bookId) {
            hideTip();
            return;
        }
        $.ajax({
            type: 'delete',
            url: '/user/removeFromBookShelf/' + encodeURIComponent(bookId),
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) item.remove();
                else showAlert(response.msg);
                hideTip();
            },
            error: function () {
                if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(messages.network);
                hideTip();
            }
        });
    }

    function renderPagination(total, page, limit) {
        var pagination = document.getElementById('books');
        if (total === 0) {
            pagination.replaceChildren();
            return;
        }
        if (!window.layui || typeof window.layui.use !== 'function') return;
        window.layui.use('laypage', function () {
            window.layui.laypage.render({
                elem: 'books',
                count: total,
                curr: page,
                limit: limit,
                jump: function (obj, first) {
                    if (!first) window.loadMobileFavorites(obj.curr, obj.limit);
                }
            });
        });
    }

    window.loadMobileFavorites = function (currentPage, pageSize) {
        var page = positiveInteger(currentPage, 1);
        var limit = positiveInteger(pageSize, 20);
        $.ajax({
            type: 'get',
            url: '/user/listBookShelfByPage',
            data: {curr: page, limit: limit},
            dataType: 'json',
            success: function (response) {
                if (response.code === 1001) {
                    window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                    return;
                }
                if (response.code !== 200) {
                    showAlert(response.msg);
                    return;
                }
                var data = response.data || {};
                var books = Array.isArray(data.list) ? data.list : [];
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < books.length; i++) {
                    var rendered = renderBook(books[i] || {});
                    if (rendered) fragment.appendChild(rendered);
                }
                document.getElementById('bookList').replaceChildren(fragment);
                renderPagination(safeCount(data.total), positiveInteger(data.pageNum, page), positiveInteger(data.pageSize, limit));
            },
            error: function () {
                if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(messages.network);
            }
        });
    };

    document.getElementById('mobileFavoritesBack').addEventListener('click', function (event) {
        event.preventDefault();
        window.history.back();
    });
    document.getElementById('bookList').addEventListener('click', function (event) {
        var item = event.target.closest('.mobile-favorites-item');
        if (!item) return;
        if (longPressTriggered) {
            event.preventDefault();
            hideTip();
            return;
        }
        var bookId = numeric(item.getAttribute('data-book-id'));
        var contentId = numeric(item.getAttribute('data-content-id'));
        if (bookId && contentId) window.location.href = '/book/' + encodeURIComponent(bookId) + '/' + encodeURIComponent(contentId) + '.html';
    });
    document.getElementById('bookList').addEventListener('touchstart', function (event) {
        var item = event.target.closest('.mobile-favorites-item');
        if (!item || !event.touches || !event.touches.length) return;
        clearTimeout(longPressTimer);
        longPressTriggered = false;
        var touch = {pageX: event.touches[0].pageX, pageY: event.touches[0].pageY};
        longPressTimer = window.setTimeout(function () { showTip(touch, item); }, 1000);
    }, {passive: true});
    document.getElementById('bookList').addEventListener('touchend', function () {
        clearTimeout(longPressTimer);
    });
    document.getElementById('bookList').addEventListener('touchmove', function () {
        clearTimeout(longPressTimer);
        hideTip();
    }, {passive: true});
    document.getElementById('bookList').addEventListener('contextmenu', function (event) {
        event.preventDefault();
    });
    document.getElementById('tipLayer').addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        removeSelectedBook();
    });

    window.loadMobileFavorites(1, 20);
})(window, document, window.jQuery);
