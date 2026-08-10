(function (window, document, navigator) {
    'use strict';

    var DATA_SAVER_KEY = 'novel:data-saver:v1';

    function storedDataSaverPreference() {
        try {
            return window.localStorage.getItem(DATA_SAVER_KEY) === 'true';
        } catch (ignored) {
            return false;
        }
    }

    function connectionRequestsDataSaver() {
        return !!(navigator.connection && navigator.connection.saveData);
    }

    function isDataSaverEnabled() {
        return storedDataSaverPreference() || connectionRequestsDataSaver();
    }

    function optimizeImages() {
        if (!isDataSaverEnabled()) {
            return;
        }
        var images = document.querySelectorAll('img');
        for (var index = 0; index < images.length; index++) {
            var image = images[index];
            image.decoding = 'async';
            if (index > 1 && !image.closest('header, .header')) {
                image.loading = 'lazy';
                image.setAttribute('fetchpriority', 'low');
            }
        }
    }

    function applyDataSaver() {
        var enabled = isDataSaverEnabled();
        document.documentElement.setAttribute('data-novel-data-saver', enabled ? 'true' : 'false');
        optimizeImages();
        window.dispatchEvent(new CustomEvent('novel:data-saver-changed', {detail: {enabled: enabled}}));
        return enabled;
    }

    function setDataSaverEnabled(enabled) {
        try {
            window.localStorage.setItem(DATA_SAVER_KEY, enabled ? 'true' : 'false');
        } catch (ignored) {
            // Chế độ riêng tư có thể chặn localStorage; trạng thái saveData của trình duyệt vẫn được giữ.
        }
        return applyDataSaver();
    }

    function ensureManifest() {
        if (!document.querySelector('link[rel="manifest"]')) {
            var manifest = document.createElement('link');
            manifest.rel = 'manifest';
            manifest.href = '/manifest.json';
            document.head.appendChild(manifest);
        }
        if (!document.querySelector('meta[name="theme-color"]')) {
            var themeColor = document.createElement('meta');
            themeColor.name = 'theme-color';
            themeColor.content = '#159957';
            document.head.appendChild(themeColor);
        }
    }

    function registerServiceWorker() {
        if (!('serviceWorker' in navigator)) {
            return;
        }
        var localHost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
        if (window.location.protocol !== 'https:' && !localHost) {
            return;
        }
        navigator.serviceWorker.register('/service-worker.js', {scope: '/'}).catch(function (error) {
            if (window.console && window.console.warn) {
                window.console.warn('Không thể đăng ký chế độ offline.', error);
            }
        });
    }

    window.NovelPwa = {
        isDataSaverEnabled: isDataSaverEnabled,
        setDataSaverEnabled: setDataSaverEnabled,
        applyDataSaver: applyDataSaver
    };

    ensureManifest();
    applyDataSaver();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', optimizeImages);
    } else {
        optimizeImages();
    }
    window.addEventListener('load', registerServiceWorker);
    if (navigator.connection && navigator.connection.addEventListener) {
        navigator.connection.addEventListener('change', applyDataSaver);
    }
}(window, document, navigator));
