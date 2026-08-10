(function (window, document, $) {
    'use strict';

    var root = document.querySelector('[data-mobile-book-detail-page]');
    if (!root || !$) return;

    var shelfButton = document.getElementById('cFavs');
    var bookId = document.getElementById('bookIdHidden').value;
    var preContentId = document.getElementById('preContentId').value;
    var indexList = document.getElementById('indexList');
    var isInShelf = false;

    function showError(message) {
        novelAlertText(message || root.dataset.networkErrorText);
    }

    function markInShelf() {
        isInShelf = true;
        shelfButton.textContent = root.dataset.inShelfText;
        shelfButton.classList.add('layui-btn-warm');
        shelfButton.disabled = true;
        shelfButton.setAttribute('aria-pressed', 'true');
    }

    function setShelfBusy(isBusy) {
        if (!isInShelf) shelfButton.disabled = isBusy;
    }

    function appendChapter(chapter) {
        var chapterId = chapter && String(chapter.id);
        if (!/^\d+$/.test(chapterId)) return;

        var item = document.createElement('p');
        item.className = 'line-limit-length layui-col-xs12 layui-col-sm4 layui-col-md3 layui-col-lg2';
        item.style.paddingLeft = '10px';
        item.style.height = '50px';
        item.style.lineHeight = '50px';

        var link = document.createElement('a');
        link.href = '/book/' + encodeURIComponent(bookId) + '/' + encodeURIComponent(chapterId) + '.html';
        link.style.color = '#333';
        link.textContent = chapter.indexName == null ? '' : String(chapter.indexName);
        item.appendChild(link);
        indexList.appendChild(item);
    }

    function loadShelfState() {
        $.ajax({
            type: 'GET',
            url: '/user/queryIsInShelf',
            data: {bookId: bookId},
            dataType: 'json',
            success: function (data) {
                if (data.code === 200 && data.data) markInShelf();
                else if (data.code !== 200 && data.code !== 1001) showError(data.msg);
            },
            error: function () {
                showError();
            }
        });
    }

    function loadLatestChapters() {
        $.ajax({
            type: 'GET',
            url: '/book/queryNewIndexList',
            data: {bookId: bookId},
            dataType: 'json',
            success: function (data) {
                if (data.code === 200) {
                    indexList.replaceChildren();
                    (Array.isArray(data.data) ? data.data : []).forEach(appendChapter);
                } else if (data.code !== 1001) {
                    showError(data.msg);
                }
            },
            error: function () {
                showError();
            }
        });
    }

    function addToShelf() {
        setShelfBusy(true);
        $.ajax({
            type: 'POST',
            url: '/user/addToBookShelf',
            data: {bookId: bookId, preContentId: preContentId},
            dataType: 'json',
            success: function (data) {
                if (data.code === 200) markInShelf();
                else if (data.code === 1001) {
                    window.location.assign('/user/login.html?originUrl=' + encodeURIComponent(window.location.href));
                } else {
                    showError(data.msg);
                }
            },
            error: function () {
                showError();
            },
            complete: function () {
                setShelfBusy(false);
            }
        });
    }

    root.querySelector('[data-mobile-book-detail-back]').addEventListener('click', function (event) {
        if (window.history.length <= 1) return;
        event.preventDefault();
        window.history.back();
    });
    shelfButton.addEventListener('click', addToShelf);

    loadShelfState();
    loadLatestChapters();
    $.post('/book/addVisitCount', {bookId: bookId});
})(window, document, window.jQuery);
