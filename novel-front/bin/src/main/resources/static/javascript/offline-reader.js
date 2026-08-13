(function (window, document) {
    'use strict';

    var DB_NAME = 'novel-reader-offline-v1';
    var STORE_NAME = 'chapters';
    var DB_VERSION = 1;
    var list = document.getElementById('offlineChapterList');
    var empty = document.getElementById('offlineEmpty');
    var viewer = document.getElementById('offlineViewer');
    var viewerTitle = document.getElementById('offlineViewerTitle');
    var viewerBook = document.getElementById('offlineViewerBook');
    var viewerContent = document.getElementById('offlineViewerContent');
    var status = document.getElementById('offlineStatus');

    function openDatabase() {
        return new Promise(function (resolve, reject) {
            if (!window.indexedDB) {
                reject(new Error('Trình duyệt không hỗ trợ kho đọc offline.'));
                return;
            }
            var request = window.indexedDB.open(DB_NAME, DB_VERSION);
            request.onupgradeneeded = function () {
                var database = request.result;
                if (!database.objectStoreNames.contains(STORE_NAME)) {
                    database.createObjectStore(STORE_NAME, {keyPath: 'path'});
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
        });
    }

    function getAll() {
        return openDatabase().then(function (database) {
            return new Promise(function (resolve, reject) {
                var request = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).getAll();
                request.onsuccess = function () {
                    database.close();
                    resolve(request.result || []);
                };
                request.onerror = function () {
                    database.close();
                    reject(request.error);
                };
            });
        });
    }

    function remove(path) {
        return openDatabase().then(function (database) {
            return new Promise(function (resolve, reject) {
                var transaction = database.transaction(STORE_NAME, 'readwrite');
                transaction.objectStore(STORE_NAME).delete(path);
                transaction.oncomplete = function () { database.close(); resolve(); };
                transaction.onerror = function () { database.close(); reject(transaction.error); };
            });
        });
    }

    function showChapter(chapter) {
        viewerTitle.textContent = chapter.chapterName || 'Chương đã lưu';
        viewerBook.textContent = chapter.bookName || '';
        viewerContent.textContent = chapter.contentText || '';
        viewer.hidden = false;
        list.parentNode.hidden = true;
        viewerTitle.focus();
    }

    function render(chapters) {
        list.textContent = '';
        chapters.sort(function (left, right) { return String(right.savedAt).localeCompare(String(left.savedAt)); });
        empty.hidden = chapters.length !== 0;
        chapters.forEach(function (chapter) {
            var item = document.createElement('li');
            var open = document.createElement('button');
            open.type = 'button';
            open.className = 'offline-open';
            open.textContent = (chapter.bookName ? chapter.bookName + ' — ' : '') + (chapter.chapterName || 'Chương đã lưu');
            open.addEventListener('click', function () { showChapter(chapter); });
            var savedAt = document.createElement('time');
            savedAt.textContent = chapter.savedAt ? new Date(chapter.savedAt).toLocaleString('vi-VN') : '';
            var removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.textContent = 'Xóa';
            removeButton.setAttribute('aria-label', 'Xóa ' + (chapter.chapterName || 'chương đã lưu'));
            removeButton.addEventListener('click', function () {
                remove(chapter.path).then(load).catch(showError);
            });
            item.appendChild(open);
            item.appendChild(savedAt);
            item.appendChild(removeButton);
            list.appendChild(item);
        });
    }

    function showError(error) {
        status.textContent = error && error.message ? error.message : 'Không thể mở kho đọc offline.';
    }

    function load() {
        status.textContent = '';
        return getAll().then(render).catch(showError);
    }

    document.getElementById('offlineBackToList').addEventListener('click', function () {
        viewer.hidden = true;
        list.parentNode.hidden = false;
        var first = list.querySelector('button');
        if (first) { first.focus(); }
    });
    load();
}(window, document));
