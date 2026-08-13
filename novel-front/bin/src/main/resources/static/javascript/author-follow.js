(function () {
    'use strict';

    function start() {
        var button = document.querySelector('[data-author-follow]');
        if (!button || !window.fetch) return;
        var authorId = button.getAttribute('data-author-id');
        var endpoint = '/user/follows/authors/' + encodeURIComponent(authorId);
        var following = false;

        function render(value) {
            following = value === true;
            button.textContent = following ? button.getAttribute('data-text-following') : button.getAttribute('data-text-follow');
            button.title = following ? button.getAttribute('data-text-unfollow') : button.getAttribute('data-text-follow');
            button.setAttribute('aria-pressed', String(following));
        }

        function handle(result) {
            if (result.code === 1001) {
                window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                return false;
            }
            if (result.code !== 200) {
                if (window.layer && window.layer.alert) window.layer.alert(result.msg);
                else window.alert(result.msg);
                return false;
            }
            return true;
        }

        fetch(endpoint, {credentials: 'same-origin'})
            .then(function (response) { return response.json(); })
            .then(function (result) { if (result.code === 200) render(result.data === true); })
            .catch(function () {});

        button.addEventListener('click', function () {
            button.disabled = true;
            fetch(endpoint, {method: following ? 'DELETE' : 'POST', credentials: 'same-origin'})
                .then(function (response) { return response.json(); })
                .then(function (result) { if (handle(result)) render(!following); })
                .catch(function () {
                    var message = button.getAttribute('data-network-error');
                    if (window.layer && window.layer.alert) window.layer.alert(message);
                    else window.alert(message);
                })
                .finally(function () { button.disabled = false; });
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
    else start();
})();

