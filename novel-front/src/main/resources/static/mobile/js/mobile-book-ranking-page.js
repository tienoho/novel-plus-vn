(function (window, document, $) {
    'use strict';

    var root = document.querySelector('[data-mobile-book-ranking-page]');
    if (!root || !$) return;

    var searchInput = document.getElementById('title');
    var searchButton = document.getElementById('mobileBookRankingSearch');
    var bookList = document.getElementById('bookList');
    var params = new URLSearchParams(window.location.search);
    var allowedSorts = ['last_index_update_time', 'word_count', 'visit_count'];

    function showError(message) {
        novelAlertText(message || root.dataset.networkErrorText);
    }

    function positiveInteger(value, fallback) {
        var parsed = Number.parseInt(value, 10);
        return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
    }

    function validId(value) {
        var id = String(value == null ? '' : value);
        return /^\d+$/.test(id) ? id : null;
    }

    function safeCoverUrl(value) {
        try {
            var url = new URL(String(value || ''), window.location.origin);
            return /^(https?:)$/.test(url.protocol) ? url.href : '/images/default.gif';
        } catch (error) {
            return '/images/default.gif';
        }
    }

    function plainDescription(value) {
        return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ')
            .replace(/\s+/g, ' ').trim();
    }

    function vietnameseDate(value) {
        var text = String(value || '');
        var match = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
        return match ? match[3] + '/' + match[2] + '/' + match[1] : text.slice(0, 10);
    }

    function detailRow(label, value, valueClass) {
        var row = document.createElement('div');
        row.className = 'mobile-ranking-detail';
        row.appendChild(document.createTextNode(label + ': '));
        var content = document.createElement(valueClass ? 'i' : 'span');
        if (valueClass) content.className = valueClass;
        content.textContent = value == null ? '' : String(value);
        row.appendChild(content);
        return row;
    }

    function appendBook(book) {
        var bookId = validId(book && book.id);
        if (!bookId) return;

        var detailUrl = '/book/' + encodeURIComponent(bookId) + '.html';
        var card = document.createElement('article');
        card.className = 'mobile-ranking-card layui-row';

        var coverLink = document.createElement('a');
        coverLink.className = 'mobile-ranking-cover-link layui-col-xs5 layui-col-sm3 layui-col-md2 layui-col-lg2';
        coverLink.href = detailUrl;
        var cover = document.createElement('img');
        cover.className = 'mobile-ranking-cover';
        cover.src = safeCoverUrl(book.picUrl);
        cover.alt = book.bookName == null ? '' : String(book.bookName);
        cover.loading = 'lazy';
        coverLink.appendChild(cover);
        card.appendChild(coverLink);

        var info = document.createElement('div');
        info.className = 'mobile-ranking-info layui-col-xs7 layui-col-sm9 layui-col-md10 layui-col-lg10';
        var titleLink = document.createElement('a');
        titleLink.href = detailUrl;
        var title = document.createElement('div');
        title.className = 'mobile-ranking-title line-limit-length';
        title.textContent = book.bookName == null ? '' : String(book.bookName);
        titleLink.appendChild(title);
        info.appendChild(titleLink);

        var authorLink = document.createElement('a');
        authorLink.href = '/book/book_ranking.html?keyword=' + encodeURIComponent(book.authorName == null ? '' : String(book.authorName));
        var author = detailRow(root.dataset.authorLabel, book.authorName);
        author.classList.add('line-limit-length');
        authorLink.appendChild(author);
        info.appendChild(authorLink);
        info.appendChild(detailRow(root.dataset.categoryLabel, book.catName));
        info.appendChild(detailRow(root.dataset.statusLabel,
            Number(book.bookStatus) === 0 ? root.dataset.serializingLabel : root.dataset.completedLabel));
        info.appendChild(detailRow(root.dataset.updatedLabel, vietnameseDate(book.lastIndexUpdateTime), 'mobile-ranking-updated'));
        var description = detailRow(root.dataset.descriptionLabel, plainDescription(book.bookDesc));
        description.classList.add('book_desc');
        info.appendChild(description);
        card.appendChild(info);
        bookList.appendChild(card);
    }

    function renderPagination(page) {
        layui.use('laypage', function () {
            layui.laypage.render({
                elem: 'books',
                count: positiveInteger(page.total, 0),
                curr: positiveInteger(page.pageNum, 1),
                limit: positiveInteger(page.pageSize, 20),
                jump: function (pagination, first) {
                    if (!first) search(pagination.curr, pagination.limit);
                }
            });
        });
    }

    function search(currentPage, pageSize) {
        var data = {
            curr: positiveInteger(currentPage, 1),
            limit: positiveInteger(pageSize, 20)
        };
        var catId = validId(params.get('catId'));
        var bookStatus = params.get('bookStatus');
        var sort = params.get('sortBy');
        var keyword = searchInput.value.trim();
        if (catId) data.catId = catId;
        if (/^[01]$/.test(bookStatus || '')) data.bookStatus = bookStatus;
        if (allowedSorts.indexOf(sort) !== -1) data.sort = sort;
        if (keyword) data.keyword = keyword;

        $.ajax({
            type: 'GET',
            url: '/book/searchByPage',
            data: data,
            dataType: 'json',
            success: function (response) {
                if (Number(response.code) !== 200) {
                    showError(response.msg);
                    return;
                }
                var page = response.data || {};
                bookList.replaceChildren();
                (Array.isArray(page.list) ? page.list : []).forEach(appendBook);
                renderPagination(page);
            },
            error: function () {
                showError();
            }
        });
    }

    var keyword = params.get('keyword');
    if (keyword) searchInput.value = keyword;
    root.querySelector('[data-mobile-book-ranking-back]').addEventListener('click', function (event) {
        if (window.history.length <= 1) return;
        event.preventDefault();
        window.history.back();
    });
    searchButton.addEventListener('click', function () {
        search(1, 20);
    });
    searchInput.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        search(1, 20);
    });
    search(1, 20);
})(window, document, window.jQuery);
