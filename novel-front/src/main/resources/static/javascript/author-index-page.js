(function (window, document, $) {
    'use strict';

    if (!$) return;

    var root = document.getElementById('authorBookPage');
    var bookList = document.getElementById('bookList');
    if (!root || !bookList) return;

    var messages = {
        coverChange: root.getAttribute('data-cover-change') || '',
        updated: root.getAttribute('data-updated') || '',
        previous: root.getAttribute('data-previous-page') || '',
        next: root.getAttribute('data-next-page') || '',
        chapters: root.getAttribute('data-chapters') || '',
        income: root.getAttribute('data-income') || '',
        analytics: root.getAttribute('data-analytics') || '',
        story: root.getAttribute('data-story') || '',
        collaborators: root.getAttribute('data-collaborators') || '',
        workInfo: root.getAttribute('data-work-info') || '',
        network: root.getAttribute('data-network-error') || '',
        selectUpload: root.getAttribute('data-select-upload') || ''
    };
    var coverUpdateInterval;
    var coverUpdateTimeout;

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(String(message || ''));
    }

    function redirectToLogin() {
        window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
    }

    function bookId(value) {
        var id = String(value == null ? '' : value);
        return /^\d{1,20}$/.test(id) ? id : null;
    }

    function safeCover(value) {
        var cover = String(value == null ? '' : value);
        if (cover === '/images/default.gif'
            || /^\/localPic\/(?:aiGen\/)?\d{4}\/\d{2}\/\d{2}\/[A-Za-z0-9]+\.(?:jpg|jpeg|gif|png)$/i.test(cover)) {
            return cover;
        }
        try {
            var url = new URL(cover);
            if (url.protocol === 'https:' || url.protocol === 'http:') return url.href;
        } catch (ignored) {
            return '/images/default.gif';
        }
        return '/images/default.gif';
    }

    function submittedCover(value) {
        var cover = String(value == null ? '' : value);
        return /^\/localPic\/\d{4}\/\d{2}\/\d{2}\/[A-Za-z0-9]+\.(?:jpg|jpeg|gif|png)$/i.test(cover)
            ? cover : null;
    }

    function count(value) {
        var number = Number(value);
        return Number.isFinite(number) && number >= 0 ? new Intl.NumberFormat('vi-VN').format(number) : '0';
    }

    function formatDate(value) {
        var date = new Date(value || '');
        if (Number.isNaN(date.getTime())) return '';
        return new Intl.DateTimeFormat('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
        }).format(date);
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = String(text);
        return node;
    }

    function actionLink(path, id, label, className) {
        var link = element('a', className || '', label);
        link.href = path + encodeURIComponent(id);
        link.target = '_blank';
        link.rel = 'noopener';
        return link;
    }

    function appendAction(cell, path, id, label, className) {
        if (cell.childNodes.length) cell.appendChild(document.createElement('br'));
        cell.appendChild(actionLink(path, id, label, className));
    }

    function coverControl(book, id) {
        var control = element('label', 'author-cover-control');
        control.title = messages.coverChange;
        var input = element('input', 'author-cover-input');
        input.type = 'file';
        input.name = 'file';
        input.id = 'file-' + id;
        input.accept = 'image/jpeg,image/png,image/gif';
        input.setAttribute('data-book-id', id);
        input.setAttribute('aria-label', messages.coverChange);
        var image = element('img', 'author-cover-image');
        image.id = 'cover-' + id;
        image.src = safeCover(book.picUrl);
        image.alt = String(book.bookName || '');
        control.appendChild(image);
        control.appendChild(input);
        return control;
    }

    function coverImage(book, id) {
        var image = element('img', 'author-cover-image');
        image.id = 'cover-' + id;
        image.src = safeCover(book.picUrl);
        image.alt = String(book.bookName || '');
        return image;
    }

    function renderBook(book) {
        var id = bookId(book.id);
        if (!id) return null;
        var row = element('tr', 'book_list');
        var coverCell = element('td', 'goread author-cover-cell');
        coverCell.appendChild(book.canEditBook === true ? coverControl(book, id) : coverImage(book, id));
        coverCell.appendChild(element('span', 'author-book-name', book.bookName || ''));
        row.appendChild(coverCell);
        row.appendChild(element('td', 'goread', book.catName || ''));
        row.appendChild(element('td', 'goread', count(book.visitCount)));
        row.appendChild(element('td', 'goread', count(book.yesterdayBuy)));
        row.appendChild(element('td', 'goread', messages.updated + ': '
            + formatDate(book.lastIndexUpdateTime || book.updateTime)));
        row.appendChild(element('td', 'goread', count(book.wordCount)));

        var actions = element('td', 'goread');
        actions.id = 'opt-' + id;
        if (book.canManageChapters === true || book.canPublishChapters === true) {
            appendAction(actions, '/author/index_list.html?bookId=', id, messages.chapters, 'redBtn');
        }
        if (book.canManageStory === true) {
            appendAction(actions, '/author/story_bible.html?bookId=', id, messages.story);
        }
        if (book.ownerAccess === true) {
            appendAction(actions, '/author/author_income_detail.html?bookId=', id, messages.income);
            appendAction(actions, '/author/collaborators.html?bookId=', id, messages.collaborators);
        }
        if (book.canViewAnalytics === true) {
            appendAction(actions, '/author/author_analytics.html?bookId=', id, messages.analytics);
        }
        appendAction(actions, '/book/', id + '.html', messages.workInfo);
        row.appendChild(actions);
        return row;
    }

    function stopCoverPolling() {
        window.clearInterval(coverUpdateInterval);
        window.clearTimeout(coverUpdateTimeout);
    }

    function pollGeneratedCover(book) {
        var id = bookId(book && book.id);
        if (!id || safeCover(book.picUrl) !== '/images/default.gif') return;
        coverUpdateInterval = window.setInterval(function () {
            $.ajax({
                type: 'get',
                url: '/author/queryAiGenPic',
                data: {bookId: id},
                dataType: 'json',
                success: function (response) {
                    var image = document.getElementById('cover-' + id);
                    var cover = response && response.code === 200 ? safeCover(response.data) : '/images/default.gif';
                    if (image && cover !== '/images/default.gif') {
                        image.src = cover;
                        stopCoverPolling();
                    }
                }
            });
        }, 3000);
        coverUpdateTimeout = window.setTimeout(stopCoverPolling, 10000);
    }

    function renderPagination(data) {
        if (!window.layui || !window.layui.laypage) return;
        window.layui.laypage.render({
            elem: 'shellPage',
            prev: messages.previous,
            next: messages.next,
            count: Number(data.total) || 0,
            curr: Number(data.pageNum) || 1,
            limit: Number(data.pageSize) || 5,
            jump: function (page, first) {
                if (!first) loadBooks(page.curr, page.limit);
            }
        });
    }

    function loadBooks(page, limit) {
        stopCoverPolling();
        $.ajax({
            type: 'get',
            url: '/author/listBookByPage',
            data: {curr: page, limit: limit},
            dataType: 'json',
            success: function (response) {
                if (response.code === 1001) {
                    redirectToLogin();
                    return;
                }
                if (response.code !== 200 || !response.data) {
                    showAlert(response.msg);
                    return;
                }
                var books = Array.isArray(response.data.list) ? response.data.list : [];
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < books.length; i++) {
                    var row = renderBook(books[i] || {});
                    if (row) fragment.appendChild(row);
                }
                bookList.replaceChildren(fragment);
                document.getElementById('hasContentDiv').style.display = books.length ? 'block' : 'none';
                document.getElementById('noContentDiv').style.display = books.length ? 'none' : 'block';
                if (books.length && Number(page) === 1) pollGeneratedCover(books[0]);
                renderPagination(response.data);
            },
            error: function () {
                showAlert(messages.network);
            }
        });
    }

    function updateCover(id, cover, input) {
        $.ajax({
            type: 'post',
            url: '/author/updateBookPic',
            data: {bookId: id, bookPic: cover},
            dataType: 'json',
            success: function (response) {
                if (response.code === 1001) {
                    redirectToLogin();
                } else if (response.code === 200) {
                    var image = document.getElementById('cover-' + id);
                    if (image) image.src = cover;
                } else {
                    showAlert(response.msg);
                }
            },
            error: function () {
                showAlert(messages.network);
            },
            complete: function () {
                input.disabled = false;
                input.value = '';
            }
        });
    }

    function uploadCover(id, input) {
        if (!input.files || !input.files.length) {
            showAlert(messages.selectUpload);
            return;
        }
        if (typeof window.checkPicUpload === 'function' && !window.checkPicUpload(input)) return;
        input.disabled = true;
        var data = new FormData();
        data.append('file', input.files[0]);
        $.ajax({
            type: 'post',
            url: '/file/picUpload',
            data: data,
            processData: false,
            contentType: false,
            dataType: 'json',
            success: function (response) {
                var cover = response && response.code === 200 ? submittedCover(response.data) : null;
                if (!cover) {
                    showAlert(response && response.msg ? response.msg : messages.network);
                    input.disabled = false;
                    input.value = '';
                    return;
                }
                updateCover(id, cover, input);
            },
            error: function () {
                showAlert(messages.network);
                input.disabled = false;
                input.value = '';
            }
        });
    }

    bookList.addEventListener('change', function (event) {
        var input = event.target;
        if (!input.classList || !input.classList.contains('author-cover-input')) return;
        var id = bookId(input.getAttribute('data-book-id'));
        if (id) uploadCover(id, input);
    });
    window.addEventListener('beforeunload', stopCoverPolling);
    loadBooks(1, 5);
})(window, document, window.jQuery);
