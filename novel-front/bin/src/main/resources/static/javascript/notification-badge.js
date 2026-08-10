(function () {
    'use strict';

    function updateBadge() {
        var badges = document.querySelectorAll('[data-notification-badge]');
        if (!badges.length || !window.fetch) return;
        fetch('/user/notifications/unread-count', {credentials: 'same-origin'})
            .then(function (response) { return response.json(); })
            .then(function (result) {
                if (result.code !== 200) return;
                var count = Number(result.data && result.data.count || 0);
                badges.forEach(function (badge) {
                    badge.textContent = count > 99 ? '99+' : String(count);
                    badge.style.display = count > 0 ? 'inline-block' : 'none';
                });
            })
            .catch(function () {
                // Badge không được làm gián đoạn điều hướng khi mạng lỗi.
            });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', updateBadge);
    } else {
        updateBadge();
    }
    document.addEventListener('novel:notifications-changed', updateBadge);
})();
