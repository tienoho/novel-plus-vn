(function (window, document) {
    'use strict';

    var actions = document.querySelectorAll('[data-top-action]');
    for (var i = 0; i < actions.length; i++) {
        actions[i].addEventListener('click', function (event) {
            var action = this.getAttribute('data-top-action');
            if (action === 'search') {
                event.preventDefault();
                var inputId = this.getAttribute('data-search-input') || 'searchKey';
                var input = document.getElementById(inputId);
                var keyword = input ? input.value : '';
                window.location.href = '/book/bookclass.html?k=' + encodeURIComponent(keyword);
            } else if (action === 'toggle-theme') {
                event.preventDefault();
                if (typeof window.toggleTheme === 'function') window.toggleTheme();
            }
        });
    }
})(window, document);
