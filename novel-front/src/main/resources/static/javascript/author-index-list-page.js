(function (window, document, $) {
    'use strict';

    var root = document.getElementById('authorIndexListPage');
    if (!root || !$) return;

    var messages = {
        updated: root.getAttribute('data-updated-text') || '',
        charge: root.getAttribute('data-charge-text') || '',
        free: root.getAttribute('data-free-text') || '',
        edit: root.getAttribute('data-edit-text') || '',
        remove: root.getAttribute('data-delete-text') || '',
        deleteConfirm: root.getAttribute('data-delete-confirm-text') || '',
        confirm: root.getAttribute('data-confirm-text') || '',
        cancel: root.getAttribute('data-cancel-text') || '',
        network: root.getAttribute('data-network-error') || '',
        fileRequired: root.getAttribute('data-transfer-file-required') || '',
        fileTooLarge: root.getAttribute('data-transfer-file-too-large') || '',
        importing: root.getAttribute('data-transfer-importing') || '',
        importSuccess: root.getAttribute('data-transfer-import-success') || '',
        importLabel: root.getAttribute('data-transfer-import-label') || '',
        exporting: root.getAttribute('data-transfer-exporting') || '',
        transferFailed: root.getAttribute('data-transfer-failed') || ''
    };
    var bookId = numeric(new URLSearchParams(window.location.search).get('bookId'));
    var chapterCount = 0;

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

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = String(text);
        return node;
    }

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(String(message || ''));
    }

    function redirectToLogin() {
        window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
    }

    function formatDate(value) {
        var date = new Date(value);
        if (!Number.isFinite(date.getTime())) return '';
        var pad = function (part) { return String(part).padStart(2, '0'); };
        return pad(date.getDate()) + '/' + pad(date.getMonth() + 1) + '/' + date.getFullYear()
            + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }

    function actionLink(action, id, label, className) {
        var link = element('a', className || '', label);
        link.href = '#chapter-' + action + '-' + id;
        link.setAttribute('data-author-chapter-action', action);
        link.setAttribute('data-index-id', id);
        return link;
    }

    function renderChapter(chapter) {
        var id = numeric(chapter.id);
        if (!id) return null;
        var row = element('tr', 'book_list');
        var name = element('td', 'name', chapter.indexName);
        name.id = 'name' + id;
        row.appendChild(name);

        var updated = element('td', 'goread');
        updated.appendChild(document.createTextNode(formatDate(chapter.updateTime)));
        updated.appendChild(document.createElement('br'));
        updated.appendChild(document.createTextNode(messages.updated));
        row.appendChild(updated);

        var isVip = String(chapter.isVip) === '1' ? '1' : '0';
        row.appendChild(element('td', 'goread', isVip === '1' ? messages.charge : messages.free));
        var actions = element('td', 'goread');
        actions.id = 'opt' + id;
        var edit = actionLink('edit', id, messages.edit, 'redBtn');
        edit.setAttribute('data-index-vip', isVip);
        actions.appendChild(edit);
        actions.appendChild(document.createElement('br'));
        actions.appendChild(actionLink('delete', id, messages.remove));
        row.appendChild(actions);
        return row;
    }

    function renderPagination(total, page, limit) {
        var pagination = document.getElementById('shellPage');
        if (total === 0) {
            pagination.replaceChildren();
            return;
        }
        if (!window.layui || typeof window.layui.use !== 'function') return;
        window.layui.use('laypage', function () {
            window.layui.laypage.render({
                elem: 'shellPage',
                count: total,
                curr: page,
                limit: limit,
                jump: function (obj, first) {
                    if (!first) loadChapters(obj.curr, obj.limit);
                }
            });
        });
    }

    function loadChapters(currentPage, pageSize) {
        if (!bookId) return;
        var page = positiveInteger(currentPage, 1);
        var limit = positiveInteger(pageSize, 5);
        $.ajax({
            type: 'get',
            url: '/book/queryIndexList',
            data: {bookId: bookId, curr: page, limit: limit, orderBy: 'index_num desc'},
            dataType: 'json',
            success: function (response) {
                if (response.code === 1001) {
                    redirectToLogin();
                    return;
                }
                if (response.code !== 200) {
                    showAlert(response.msg);
                    return;
                }
                var data = response.data || {};
                var chapters = Array.isArray(data.list) ? data.list : [];
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < chapters.length; i++) {
                    var rendered = renderChapter(chapters[i] || {});
                    if (rendered) fragment.appendChild(rendered);
                }
                document.getElementById('bookList').replaceChildren(fragment);
                chapterCount = safeCount(data.total);
                $('#hasContentDiv').toggle(chapterCount > 0);
                $('#noContentDiv').toggle(chapterCount === 0);
                renderPagination(chapterCount, positiveInteger(data.pageNum, page), positiveInteger(data.pageSize, limit));
            },
            error: function () {
                if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(messages.network);
            }
        });
    }

    function addChapter() {
        if (!bookId) return;
        window.location.href = '/author/content_add.html?indexCount=' + encodeURIComponent(chapterCount)
            + '&bookId=' + encodeURIComponent(bookId);
    }

    function editChapter(link) {
        var indexId = numeric(link.getAttribute('data-index-id'));
        var isVip = link.getAttribute('data-index-vip') === '1' ? '1' : '0';
        if (!bookId || !indexId) return;
        var name = document.getElementById('name' + indexId);
        var params = new URLSearchParams({
            bookId: bookId,
            indexId: indexId,
            indexName: name ? name.textContent.trim() : '',
            isVip: isVip
        });
        window.location.href = '/author/content_update.html?' + params.toString();
    }

    function deleteChapter(indexId) {
        var id = numeric(indexId);
        if (!id || !window.layer || typeof window.layer.confirm !== 'function') return;
        window.layer.confirm(messages.deleteConfirm, {btn: [messages.confirm, messages.cancel]}, function (dialogIndex) {
            window.layer.close(dialogIndex);
            $.ajax({
                type: 'delete',
                url: '/author/deleteIndex/' + encodeURIComponent(id),
                dataType: 'json',
                success: function (response) {
                    if (response.code === 200) window.location.reload();
                    else if (response.code === 1001) redirectToLogin();
                    else showAlert(response.msg);
                },
                error: function () { window.layer.alert(messages.network); }
            });
        });
    }

    function chooseImport() {
        document.getElementById('bookImportFile').click();
    }

    function importBook(file) {
        var input = document.getElementById('bookImportFile');
        if (!bookId) {
            input.value = '';
            return;
        }
        if (!file || !/\.(?:txt|docx|epub)$/i.test(String(file.name || ''))) {
            window.layer.alert(messages.fileRequired);
            input.value = '';
            return;
        }
        if (file.size > 20 * 1024 * 1024) {
            window.layer.alert(messages.fileTooLarge);
            input.value = '';
            return;
        }
        var form = new FormData();
        form.append('file', file);
        var button = document.getElementById('bookImportButton');
        button.disabled = true;
        button.textContent = messages.importing;
        $.ajax({
            type: 'post',
            url: '/author/books/' + encodeURIComponent(bookId) + '/import',
            data: form,
            processData: false,
            contentType: false,
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) {
                    var count = safeCount(response.data && response.data.chapterCount);
                    window.layer.alert(messages.importSuccess.replace('{count}', count), function () {
                        window.location.href = '/author/draft_list.html';
                    });
                } else if (response.code === 1001) redirectToLogin();
                else showAlert(response.msg || messages.transferFailed);
            },
            error: function () { window.layer.alert(messages.network); },
            complete: function () {
                button.disabled = false;
                button.textContent = messages.importLabel;
                input.value = '';
            }
        });
    }

    function safeFilename(value, format) {
        var fallback = 'tac-pham.' + format.toLowerCase();
        var filename = String(value || fallback).replace(/[\\/:*?"<>|\r\n]/g, '-').trim();
        return filename || fallback;
    }

    async function exportBook() {
        if (!bookId) return;
        var button = document.getElementById('bookExportButton');
        var originalText = button.textContent;
        var format = document.getElementById('bookExportFormat').value;
        if (!/^(?:TXT|DOCX|EPUB)$/.test(format)) return;
        button.disabled = true;
        button.textContent = messages.exporting;
        try {
            var response = await window.fetch('/author/books/' + encodeURIComponent(bookId)
                + '/export?format=' + encodeURIComponent(format), {credentials: 'same-origin'});
            var contentType = response.headers.get('Content-Type') || '';
            if (!response.ok || contentType.indexOf('application/json') >= 0) {
                var payload = contentType.indexOf('application/json') >= 0 ? await response.json() : null;
                throw new Error(payload && payload.msg ? payload.msg : messages.transferFailed);
            }
            var blob = await response.blob();
            var disposition = response.headers.get('Content-Disposition') || '';
            var encodedName = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
            var filename = safeFilename(encodedName ? decodeURIComponent(encodedName[1].replace(/"/g, '')) : '', format);
            var url = URL.createObjectURL(blob);
            var link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (error) {
            window.layer.alert(error && error.message ? error.message : messages.transferFailed);
        } finally {
            button.disabled = false;
            button.textContent = originalText;
        }
    }

    root.addEventListener('click', function (event) {
        var actionElement = event.target.closest('[data-author-chapter-action]');
        if (!actionElement) return;
        event.preventDefault();
        var action = actionElement.getAttribute('data-author-chapter-action');
        if (action === 'add') addChapter();
        else if (action === 'edit') editChapter(actionElement);
        else if (action === 'delete') deleteChapter(actionElement.getAttribute('data-index-id'));
        else if (action === 'choose-import') chooseImport();
        else if (action === 'export') exportBook();
    });
    document.getElementById('bookImportFile').addEventListener('change', function () {
        importBook(this.files && this.files[0]);
    });

    loadChapters(1, 5);
})(window, document, window.jQuery);
