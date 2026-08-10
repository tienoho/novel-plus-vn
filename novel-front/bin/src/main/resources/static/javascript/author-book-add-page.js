(function (window, document, $) {
    'use strict';

    var root = document.getElementById('authorBookAddPage');
    if (!root || !$) return;
    var messages = {
        selectUpload: root.getAttribute('data-select-upload') || '',
        nameRequired: root.getAttribute('data-name-required') || '',
        nameTooLong: root.getAttribute('data-name-too-long') || '',
        coverRequired: root.getAttribute('data-cover-required') || '',
        descriptionRequired: root.getAttribute('data-description-required') || '',
        network: root.getAttribute('data-network-error') || ''
    };
    var categories = [];
    var locked = false;

    function numeric(value) {
        var result = String(value == null ? '' : value);
        return /^\d+$/.test(result) ? result : null;
    }

    function direction(value) {
        return String(value) === '1' ? '1' : '0';
    }

    function safePhoto(value) {
        var photo = String(value == null ? '' : value);
        return /^\/localPic\/\d{4}\/\d{2}\/\d{2}\/[A-Za-z0-9]+\.(?:jpg|jpeg|gif|png|JPG|JPEG|GIF|PNG)$/.test(photo)
            ? photo : null;
    }

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else window.layer.alert(String(message || ''));
    }

    function renderCategories(workDirection) {
        var selectedDirection = direction(workDirection);
        var select = document.getElementById('catId');
        var fragment = document.createDocumentFragment();
        for (var i = 0; i < categories.length; i++) {
            var category = categories[i] || {};
            var id = numeric(category.id);
            if (!id || direction(category.workDirection) !== selectedDirection) continue;
            var option = document.createElement('option');
            option.value = id;
            option.textContent = String(category.name == null ? '' : category.name);
            fragment.appendChild(option);
        }
        select.replaceChildren(fragment);
    }

    function loadCategories(workDirection) {
        if (categories.length) {
            renderCategories(workDirection);
            return;
        }
        $.ajax({
            type: 'get',
            url: '/book/listBookCategory',
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) {
                    categories = Array.isArray(response.data) ? response.data : [];
                    renderCategories(workDirection);
                } else showAlert(response.msg);
            },
            error: function () { window.layer.alert(messages.network); }
        });
    }

    function uploadCover(input) {
        if (!input.files || !input.files.length) {
            window.layer.alert(messages.selectUpload);
            return;
        }
        if (typeof window.checkPicUpload === 'function' && !window.checkPicUpload(input)) return;
        $.ajaxFileUpload({
            url: '/file/picUpload',
            secureuri: false,
            fileElementId: 'file0',
            dataType: 'json',
            type: 'post',
            success: function (response) {
                if (response.code !== 200) {
                    showAlert(response.msg);
                    return;
                }
                var photo = safePhoto(response.data);
                if (!photo) {
                    showAlert(response.msg || messages.coverRequired);
                    return;
                }
                document.getElementById('picImage').src = photo;
                document.getElementById('picUrl').value = photo;
            }
        });
    }

    function addBook() {
        if (locked) return;
        locked = true;
        var error = document.getElementById('LabErr');
        var bookName = String(document.getElementById('bookName').value || '').trim();
        var bookDesc = String(document.getElementById('bookDesc').value || '').trim();
        var picUrl = document.getElementById('picUrl').value;
        if (!bookName) error.textContent = messages.nameRequired;
        else if (bookName.length > 20) error.textContent = messages.nameTooLong;
        else if (!picUrl) error.textContent = messages.coverRequired;
        else if (!bookDesc) error.textContent = messages.descriptionRequired;
        else {
            var category = document.getElementById('catId');
            var categoryId = numeric(category.value);
            if (!categoryId || !category.selectedOptions.length) {
                locked = false;
                return;
            }
            error.textContent = '';
            $.ajax({
                type: 'post',
                url: '/author/addBook',
                data: {
                    workDirection: direction(document.getElementById('workDirection').value),
                    catId: categoryId,
                    catName: category.selectedOptions[0].textContent,
                    bookName: bookName,
                    picUrl: picUrl,
                    bookDesc: bookDesc
                },
                dataType: 'json',
                success: function (response) {
                    if (response.code === 200) window.location.href = '/author/index.html';
                    else if (response.code === 1001) {
                        window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                    } else {
                        locked = false;
                        error.textContent = String(response.msg || '');
                    }
                },
                error: function () {
                    locked = false;
                    window.layer.alert(messages.network);
                }
            });
            return;
        }
        locked = false;
    }

    document.getElementById('workDirection').addEventListener('change', function () { loadCategories(this.value); });
    document.getElementById('file0').addEventListener('change', function () { uploadCover(this); });
    document.getElementById('btnRegister').addEventListener('click', addBook);
    loadCategories(document.getElementById('workDirection').value);
})(window, document, window.jQuery);
