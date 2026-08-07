(function (window, document) {
    'use strict';

    document.addEventListener('click', function (event) {
        var target = event.target.closest('[data-csp-action]');
        if (!target) {
            return;
        }

        var action = target.getAttribute('data-csp-action');
        var handlers = {
            'history-back': function () { window.history.back(); },
            'logout': function () { if (typeof window.logout === 'function') window.logout(); },
            'read-history': function () { if (typeof window.readHistory === 'function') window.readHistory(); },
            'open-bookshelf': function () { if (typeof window.toMyCollect === 'function') window.toMyCollect(); },
            'remove-from-bookshelf': function () {
                if (typeof window.removeFromBookShelf === 'function') {
                    window.removeFromBookShelf(target.getAttribute('data-book-id'));
                }
            },
            'continue-draft': function () {
                if (typeof window.continueDraft === 'function') {
                    window.continueDraft(Number(target.getAttribute('data-draft-id')));
                }
            }
        };

        if (handlers[action]) {
            event.preventDefault();
            handlers[action]();
        }
    });
})(window, document);
