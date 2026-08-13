(function (window, $) {
    'use strict';

    function showMessage(value) {
        if (window.layer) window.layer.msg(value);
    }

    function applyResult(textarea, operation, selectionStart, selectionEnd, output) {
        var current = textarea.val() || '';
        var insertAt = operation === 'continue' ? selectionEnd : selectionStart;
        var removeUntil = selectionEnd;
        var updated = current.substring(0, insertAt) + output + current.substring(removeUntil);
        textarea.val(updated).trigger('input');
        var caret = insertAt + output.length;
        if (textarea[0] && textarea[0].setSelectionRange) {
            textarea[0].setSelectionRange(caret, caret);
        }
        textarea.scrollTop(textarea[0].scrollHeight);
    }

    function init(config) {
        var toolbar = $(config.toolbarSelector || '.ai-toolbar');
        var textarea = $(config.contentSelector || '#bookContent');
        if (!toolbar.length || !textarea.length) return;

        toolbar.on('click', '.ai-link', function () {
            var operation = String($(this).data('operation') || '');
            var field = textarea[0];
            var selectionStart = field.selectionStart || 0;
            var selectionEnd = field.selectionEnd || 0;
            var selectedText = textarea.val().substring(selectionStart, selectionEnd);
            if (!selectedText) {
                showMessage(config.messages.selectText);
                return;
            }

            var payload = {
                bookId: Number(config.bookId),
                draftId: typeof config.draftId === 'function' ? config.draftId() : null,
                text: selectedText
            };
            if (operation === 'expand' || operation === 'condense') {
                window.layer.prompt({
                    title: config.messages.ratioPrompt,
                    value: operation === 'expand' ? 2 : 0.5,
                    btn: [config.messages.confirm, config.messages.cancel]
                }, function (value, index) {
                    var ratio = Number(value);
                    var valid = operation === 'expand' ? ratio > 1 && ratio <= 5 : ratio > 0 && ratio < 1;
                    if (!Number.isFinite(ratio) || !valid) {
                        showMessage(config.messages.ratioInvalid);
                        return;
                    }
                    payload.ratio = ratio * 100;
                    window.layer.close(index);
                    request(operation, payload, textarea, selectionStart, selectionEnd, config.messages);
                });
                return;
            }
            if (operation === 'continue') {
                window.layer.prompt({
                    title: config.messages.lengthPrompt,
                    value: 200,
                    btn: [config.messages.confirm, config.messages.cancel]
                }, function (value, index) {
                    var length = Number(value);
                    if (!Number.isInteger(length) || length < 1 || length > 4000) {
                        showMessage(config.messages.lengthInvalid);
                        return;
                    }
                    payload.length = length;
                    window.layer.close(index);
                    request(operation, payload, textarea, selectionStart, selectionEnd, config.messages);
                });
                return;
            }
            request(operation, payload, textarea, selectionStart, selectionEnd, config.messages);
        });
    }

    function request(operation, payload, textarea, selectionStart, selectionEnd, messages) {
        var loading = window.layer ? window.layer.load(1, {shade: 0.3}) : null;
        $.ajax({
            type: 'POST',
            url: '/author/ai/' + operation,
            contentType: 'application/json; charset=UTF-8',
            dataType: 'json',
            data: JSON.stringify(payload),
            success: function (response) {
                if (loading !== null) window.layer.close(loading);
                if (!response || response.code !== 200 || typeof response.data !== 'string') {
                    showMessage(response && response.msg ? response.msg : messages.failed);
                    return;
                }
                applyResult(textarea, operation, selectionStart, selectionEnd, response.data);
            },
            error: function (xhr) {
                if (loading !== null) window.layer.close(loading);
                var response = xhr && xhr.responseJSON;
                showMessage(response && response.msg ? response.msg : messages.networkError);
            }
        });
    }

    window.NovelAuthorAi = {init: init};
})(window, window.jQuery);
