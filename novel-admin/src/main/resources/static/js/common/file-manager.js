(function (window, document, $) {
    'use strict';

    var root = document.getElementById('file-manager-app');
    if (!root) {
        return;
    }

    var state = {limit: 12, offset: 0, total: 0, type: ''};
    var messages = {
        first: root.getAttribute('data-message-first') || '',
        previous: root.getAttribute('data-message-previous') || '',
        next: root.getAttribute('data-message-next') || '',
        last: root.getAttribute('data-message-last') || '',
        deleteConfirm: root.getAttribute('data-message-delete-confirm') || '',
        confirm: root.getAttribute('data-message-confirm') || '',
        cancel: root.getAttribute('data-message-cancel') || '',
        copied: root.getAttribute('data-message-copied') || '',
        copy: root.getAttribute('data-label-copy') || '',
        deleteLabel: root.getAttribute('data-label-delete') || ''
    };
    var clipboard = null;

    function safeFileUrl(value) {
        var raw = String(value == null ? '' : value);
        return /^\/files\/[A-Za-z0-9._~!$&'()*+,;=:@%\/-]+$/.test(raw) ? raw : '';
    }

    function createButton(className, label) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = label;
        return button;
    }

    function createFileCard(row) {
        var box = document.createElement('div');
        box.className = 'file-box';
        var file = document.createElement('div');
        file.className = 'file';
        box.appendChild(file);

        var corner = document.createElement('span');
        corner.className = 'corner';
        file.appendChild(corner);

        var imageContainer = document.createElement('div');
        imageContainer.className = 'image';
        var image = document.createElement('img');
        image.alt = '';
        image.className = 'img-responsive';
        var url = safeFileUrl(row && row.url);
        if (url) {
            image.src = url;
        }
        imageContainer.appendChild(image);
        file.appendChild(imageContainer);

        var fileName = document.createElement('div');
        fileName.className = 'file-name';
        var date = document.createElement('small');
        date.textContent = String(row && row.createDate != null ? row.createDate : '');
        fileName.appendChild(date);
        file.appendChild(fileName);

        var actions = document.createElement('div');
        actions.className = 'file-actions';
        var copyButton = createButton('btn btn-warning btn-xs copy', messages.copy);
        copyButton.setAttribute('data-file-url', url);
        actions.appendChild(copyButton);

        var deleteButton = createButton('btn btn-danger btn-xs file-delete', messages.deleteLabel);
        var id = String(row && row.id != null ? row.id : '');
        if (/^\d+$/.test(id)) {
            deleteButton.setAttribute('data-file-id', id);
        } else {
            deleteButton.disabled = true;
        }
        actions.appendChild(deleteButton);
        file.appendChild(actions);
        return box;
    }

    function initializeClipboard() {
        if (clipboard && typeof clipboard.destroy === 'function') {
            clipboard.destroy();
        }
        clipboard = new window.Clipboard(document.querySelectorAll('#file-list button.copy'), {
            text: function (trigger) {
                window.layer.msg(messages.copied);
                return trigger.getAttribute('data-file-url') || '';
            }
        });
    }

    function renderRows(rows) {
        var list = document.getElementById('file-list');
        var fragment = document.createDocumentFragment();
        var safeRows = Array.isArray(rows) ? rows : [];
        for (var i = 0; i < safeRows.length; i++) {
            fragment.appendChild(createFileCard(safeRows[i]));
        }
        list.replaceChildren(fragment);
        initializeClipboard();
    }

    function renderPaginator() {
        var totalPages = Math.ceil(state.total / state.limit);
        var page = $('#page');
        if (totalPages < 1) {
            page.empty();
            return;
        }
        page.bootstrapPaginator({
            currentPage: state.offset / state.limit + 1,
            totalPages: totalPages,
            numberOfPages: 4,
            bootstrapMajorVersion: 3,
            alignment: 'center',
            size: 'large',
            shouldShowPage: true,
            itemTexts: function (type, pageNumber) {
                var labels = {first: messages.first, prev: messages.previous, next: messages.next, last: messages.last};
                return type === 'page' ? pageNumber : labels[type];
            },
            onPageClicked: function (event, originalEvent, type, pageNumber) {
                state.offset = (pageNumber - 1) * state.limit;
                loadFiles();
            }
        });
    }

    function loadFiles() {
        $.getJSON('/common/sysFile/list', {
            limit: state.limit,
            offset: state.offset,
            type: state.type
        }, function (response) {
            state.total = Number(response && response.total) || 0;
            renderRows(response && response.rows);
            renderPaginator();
        });
    }

    function removeFile(id) {
        window.layer.confirm(messages.deleteConfirm, {
            btn: [messages.confirm, messages.cancel]
        }, function () {
            $.ajax({
                url: '/common/sysFile/remove',
                type: 'post',
                data: {id: id},
                success: function (response) {
                    window.layer.msg(response.msg);
                    loadFiles();
                }
            });
        });
    }

    root.addEventListener('click', function (event) {
        var typeLink = event.target.closest('[data-file-type]');
        if (typeLink && root.contains(typeLink)) {
            event.preventDefault();
            var links = root.querySelectorAll('[data-file-type]');
            for (var i = 0; i < links.length; i++) {
                links[i].classList.toggle('aactive', links[i] === typeLink);
            }
            state.type = typeLink.getAttribute('data-file-type') || '';
            state.offset = 0;
            loadFiles();
            return;
        }

        var deleteButton = event.target.closest('.file-delete[data-file-id]');
        if (deleteButton && root.contains(deleteButton)) {
            event.preventDefault();
            removeFile(deleteButton.getAttribute('data-file-id'));
        }
    });

    window.layui.use('upload', function () {
        window.layui.upload.render({
            elem: '#test1',
            url: '/common/sysFile/upload',
            size: 1000,
            accept: 'file',
            done: function (response) {
                window.layer.msg(response.msg);
                loadFiles();
            },
            error: function (response) {
                window.layer.msg(response.msg);
            }
        });
    });

    loadFiles();
})(window, document, window.jQuery);
