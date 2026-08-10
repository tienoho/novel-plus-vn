(function (window, document, $) {
    'use strict';

    var root = document.getElementById('bookRankingPage');
    if (!root || !$) return;

    var messages = {
        tenThousand: root.getAttribute('data-ten-thousand-unit') || '',
        network: root.getAttribute('data-network-error') || '',
        ranks: [
            root.getAttribute('data-rank-views') || '',
            root.getAttribute('data-rank-new') || '',
            root.getAttribute('data-rank-updates') || '',
            root.getAttribute('data-rank-comments') || ''
        ]
    };

    function numeric(value) {
        var result = String(value == null ? '' : value);
        return /^\d+$/.test(result) ? result : null;
    }

    function rankType(value) {
        var parsed = Number(value);
        return Number.isInteger(parsed) && parsed >= 0 && parsed <= 3 ? parsed : 0;
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = String(text);
        return node;
    }

    function link(href, text) {
        var node = element('a', '', text);
        node.href = href;
        return node;
    }

    function renderBook(book, index) {
        var id = numeric(book.id);
        var categoryId = numeric(book.catId);
        if (!id || !categoryId) return null;
        var row = document.createElement('tr');

        var rank = element('td', 'rank');
        rank.appendChild(element('i', index < 3 ? 'num' + (index + 1) : '', index + 1));
        row.appendChild(rank);

        var category = element('td', 'style');
        category.appendChild(link('/book/bookclass.html?c=' + encodeURIComponent(categoryId), '[' + String(book.catName || '') + ']'));
        row.appendChild(category);

        var name = element('td', 'name');
        name.appendChild(link('/book/' + encodeURIComponent(id) + '.html', book.bookName));
        row.appendChild(name);

        var chapter = element('td', 'chapter');
        chapter.appendChild(link('/book/' + encodeURIComponent(id) + '.html', book.lastIndexName));
        row.appendChild(chapter);

        var author = element('td', 'author');
        author.appendChild(link('/book/bookclass.html?k=' + encodeURIComponent(String(book.authorName || '')), book.authorName));
        row.appendChild(author);

        var wordCount = Number(book.wordCount);
        row.appendChild(element('td', 'word', (Number.isFinite(wordCount) ? (wordCount / 10000).toFixed(2) : '0.00')
            + ' ' + messages.tenThousand));
        return row;
    }

    function setActive(type) {
        var links = document.querySelectorAll('#rankType [data-rank-type]');
        for (var i = 0; i < links.length; i++) {
            links[i].classList.toggle('on', Number(links[i].getAttribute('data-rank-type')) === type);
        }
        document.getElementById('rankName').textContent = messages.ranks[type];
    }

    function loadRank(value) {
        var type = rankType(value);
        setActive(type);
        $.ajax({
            type: 'get',
            url: '/book/listRank',
            data: {type: type, limit: 30},
            dataType: 'json',
            success: function (response) {
                if (response.code !== 200) {
                    if (typeof window.novelAlertText === 'function') window.novelAlertText(response.msg);
                    return;
                }
                var books = Array.isArray(response.data) ? response.data : [];
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < books.length; i++) {
                    var rendered = renderBook(books[i] || {}, i);
                    if (rendered) fragment.appendChild(rendered);
                }
                document.getElementById('bookRankList').replaceChildren(fragment);
            },
            error: function () {
                if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(messages.network);
            }
        });
    }

    document.getElementById('rankType').addEventListener('click', function (event) {
        var target = event.target.closest('[data-rank-type]');
        if (!target) return;
        event.preventDefault();
        loadRank(target.getAttribute('data-rank-type'));
    });

    loadRank(new URLSearchParams(window.location.search).get('type'));
})(window, document, window.jQuery);
