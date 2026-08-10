(function (window, document, $) {
    'use strict';

    var root = document.querySelector('[data-mobile-home-page]');
    if (!root || !$) return;

    var list = document.getElementById('updateRankBooks');
    var searchInput = document.getElementById('title');
    var searchButton = document.getElementById('mobileHomeSearch');

    function validId(value) {
        var id = String(value == null ? '' : value);
        return /^\d+$/.test(id) ? id : null;
    }

    function plainText(value) {
        return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ')
            .replace(/\s+/g, ' ').trim();
    }

    function appendBook(book, index) {
        var bookId = validId(book && book.id);
        if (!bookId) return;

        var item = document.createElement('div');
        item.className = 'layui-col-xs12 layui-col-sm6 layui-col-md6 layui-col-lg6';
        item.style.paddingBottom = '30px';
        var link = document.createElement('a');
        link.href = '/book/' + encodeURIComponent(bookId) + '.html';

        var summary = document.createElement('div');
        summary.className = 'line-limit-length layui-col-xs8 layui-col-sm6 layui-col-md6 layui-col-lg6';
        var title = document.createElement('span');
        title.textContent = (index + 1) + '.' + String(book.bookName || '');
        var author = document.createElement('span');
        author.className = 'layui-elip';
        author.style.color = '#a6a6a6';
        author.textContent = String(book.authorName || '');
        summary.append(title, document.createTextNode(' - '), author);

        var updated = document.createElement('div');
        updated.className = 'layui-col-sm3 layui-col-md3 layui-col-lg3';
        updated.style.cssText = 'color:#ff5722;float:right;margin-right:5px';
        var time = document.createElement('i');
        time.textContent = String(book.lastIndexUpdateTime || '');
        updated.appendChild(time);

        var clear = document.createElement('div');
        clear.style.clear = 'both';
        var description = document.createElement('div');
        description.className = 'layui-elip layui-col-md11 layui-col-sm11 layui-col-lg11';
        description.style.cssText = 'color:#a6a6a6;padding-left:5px;padding-top:5px';
        description.textContent = root.dataset.descriptionLabel + ': ' + plainText(book.bookDesc);

        link.append(summary, updated, clear, description);
        item.appendChild(link);
        list.appendChild(item);
    }

    function loadLatestBooks() {
        $.ajax({
            type: 'GET',
            url: '/book/listUpdateRank',
            dataType: 'json',
            success: function (response) {
                if (Number(response.code) !== 200) {
                    novelAlertText(response.msg);
                    return;
                }
                list.replaceChildren();
                (Array.isArray(response.data) ? response.data : []).forEach(appendBook);
            },
            error: function () {
                novelAlertText(root.dataset.networkErrorText);
            }
        });
    }

    function search() {
        var params = new URLSearchParams();
        var keyword = searchInput.value.trim();
        if (keyword) params.set('keyword', keyword);
        window.location.assign('/book/book_ranking.html' + (params.size ? '?' + params : ''));
    }

    searchButton.addEventListener('click', search);
    searchInput.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        search();
    });
    loadLatestBooks();
})(window, document, window.jQuery);
