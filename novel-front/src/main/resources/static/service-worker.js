'use strict';

var CACHE_PREFIX = 'novel-plus-shell-';
var CACHE_VERSION = 'v4';
var SHELL_CACHE = CACHE_PREFIX + CACHE_VERSION;
var OFFLINE_PAGE = '/offline-reader.htm';
var PRECACHE_URLS = [
    OFFLINE_PAGE,
    '/manifest.json',
    '/favicon.ico',
    '/javascript/pwa-register.js',
    '/javascript/reader-tools.js',
    '/javascript/offline-reader.js'
];
var PUBLIC_ASSET_PREFIXES = [
    '/css/',
    '/images/',
    '/javascript/',
    '/js/',
    '/layui/',
    '/mobile/css/',
    '/mobile/js/',
    '/mobile/layui/'
];

function isPublicAsset(pathname) {
    if (pathname === '/favicon.ico' || pathname === '/manifest.json') {
        return true;
    }
    return PUBLIC_ASSET_PREFIXES.some(function (prefix) {
        return pathname.indexOf(prefix) === 0;
    });
}

function isCacheableResponse(response) {
    if (!response || !response.ok || (response.type !== 'basic' && response.type !== 'cors')) {
        return false;
    }
    var cacheControl = (response.headers.get('Cache-Control') || '').toLowerCase();
    return cacheControl.indexOf('private') === -1 &&
        cacheControl.indexOf('no-store') === -1 &&
        !response.headers.get('Set-Cookie');
}

self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open(SHELL_CACHE)
            .then(function (cache) { return cache.addAll(PRECACHE_URLS); })
            .then(function () { return self.skipWaiting(); })
    );
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys()
            .then(function (keys) {
                return Promise.all(keys.map(function (key) {
                    if (key.indexOf(CACHE_PREFIX) === 0 && key !== SHELL_CACHE) {
                        return caches.delete(key);
                    }
                    return Promise.resolve(false);
                }));
            })
            .then(function () { return self.clients.claim(); })
    );
});

self.addEventListener('fetch', function (event) {
    var request = event.request;
    if (request.method !== 'GET') {
        return;
    }

    var url = new URL(request.url);
    if (url.origin !== self.location.origin) {
        return;
    }

    // Mọi navigation, bao gồm chương VIP và trang theo tài khoản, luôn đi qua mạng.
    // Khi mất mạng chỉ trả shell offline; tuyệt đối không ghi HTML navigation vào Cache Storage.
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request).catch(function () {
                return caches.match(OFFLINE_PAGE);
            })
        );
        return;
    }

    if (!isPublicAsset(url.pathname)) {
        return;
    }

    event.respondWith(
        caches.match(request).then(function (cached) {
            var network = fetch(request).then(function (response) {
                if (isCacheableResponse(response)) {
                    caches.open(SHELL_CACHE).then(function (cache) {
                        cache.put(request, response.clone());
                    });
                }
                return response;
            });
            return cached || network;
        }).catch(function () {
            return caches.match(request);
        })
    );
});
