(function (window, document, $) {
    'use strict';

    var root = document.getElementById('userSettingsPage');
    if (!root || !$) return;
    var mode = root.getAttribute('data-settings-mode') || '';
    var messages = {
        edit: root.getAttribute('data-edit-text') || '',
        male: root.getAttribute('data-male-text') || '',
        female: root.getAttribute('data-female-text') || '',
        select: root.getAttribute('data-select-text') || '',
        network: root.getAttribute('data-network-error') || '',
        selectUpload: root.getAttribute('data-select-upload') || '',
        nicknameRequired: root.getAttribute('data-nickname-required') || '',
        nicknameInvalid: root.getAttribute('data-nickname-invalid') || '',
        oldRequired: root.getAttribute('data-old-required') || '',
        newRequired: root.getAttribute('data-new-required') || '',
        confirmRequired: root.getAttribute('data-confirm-required') || '',
        mismatch: root.getAttribute('data-password-mismatch') || ''
    };

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(String(message || ''));
    }

    function redirectToLogin() {
        window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
    }

    function handleFailure(response, target) {
        if (response.code === 1001) redirectToLogin();
        else if (target) target.textContent = String(response.msg || '');
        else showAlert(response.msg);
    }

    function safePhoto(value) {
        var photo = String(value == null ? '' : value);
        return /^(?:\/localPic\/\d{4}\/\d{2}\/\d{2}\/[A-Za-z0-9]+\.(?:jpg|jpeg|gif|png|JPG|JPEG|GIF|PNG)|\/images\/man\.png)$/.test(photo)
            ? photo : '/images/man.png';
    }

    function appendEditLabel(target, value) {
        target.replaceChildren(document.createTextNode(String(value == null ? '' : value)));
        var edit = document.createElement('em');
        edit.className = 'ml10';
        edit.textContent = '[' + messages.edit + ']';
        target.appendChild(edit);
    }

    function loadUserInfo(callback) {
        $.ajax({
            type: 'get',
            url: '/user/userInfo',
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) callback(response.data || {});
                else handleFailure(response);
            },
            error: function () { window.layer.alert(messages.network); }
        });
    }

    function postUserInfo(data, errorTarget) {
        $.ajax({
            type: 'post',
            url: '/user/updateUserInfo',
            data: data,
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) window.location.href = '/user/setup.html';
                else handleFailure(response, errorTarget);
            },
            error: function () { window.layer.alert(messages.network); }
        });
    }

    function initializeSetup() {
        loadUserInfo(function (user) {
            document.getElementById('imgLogo').src = safePhoto(user.userPhoto || '/images/man.png');
            appendEditLabel(document.getElementById('my_name'), user.nickName || user.username || '');
            var gender = user.userSex === '0' ? messages.male : user.userSex === '1' ? messages.female : messages.select;
            appendEditLabel(document.getElementById('my_sex'), gender);
        });
        document.getElementById('file0').addEventListener('change', function () {
            var input = this;
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
                    if (photo === '/images/man.png' && response.data !== '/images/man.png') {
                        showAlert(response.msg || messages.network);
                        return;
                    }
                    postUserInfo({userPhoto: photo});
                }
            });
        });
    }

    function initializeName() {
        var input = document.getElementById('txtNiceName');
        var error = document.getElementById('LabErr');
        loadUserInfo(function (user) { input.value = String(user.nickName || user.username || ''); });
        root.addEventListener('click', function (event) {
            if (!event.target.closest('[data-settings-action="update-name"]')) return;
            event.preventDefault();
            var nickname = String(input.value || '').trim();
            if (!nickname) {
                error.textContent = messages.nicknameRequired;
                return;
            }
            if (!/^[\u4E00-\u9FA5A-Za-z0-9_]{1,11}$/.test(nickname)) {
                error.textContent = messages.nicknameInvalid;
                return;
            }
            error.textContent = '';
            postUserInfo({nickName: nickname}, error);
        });
    }

    function initializeSex() {
        loadUserInfo(function (user) {
            var value = user.userSex === '0' ? '0' : '1';
            var input = document.querySelector('input[name="sex"][value="' + value + '"]');
            if (input) input.checked = true;
        });
        root.addEventListener('click', function (event) {
            if (!event.target.closest('[data-settings-action="update-sex"]')) return;
            event.preventDefault();
            var selected = document.querySelector('input[name="sex"]:checked');
            var value = selected && selected.value === '0' ? '0' : '1';
            postUserInfo({userSex: value});
        });
    }

    function initializePassword() {
        var error = document.getElementById('LabErr');
        root.addEventListener('submit', function (event) {
            if (event.target.id !== 'passwordForm') return;
            event.preventDefault();
            var oldPassword = document.getElementById('txtOldPass').value;
            var newPassword1 = document.getElementById('txtNewPass1').value;
            var newPassword2 = document.getElementById('txtNewPass2').value;
            if (!oldPassword.trim()) error.textContent = messages.oldRequired;
            else if (!newPassword1.trim()) error.textContent = messages.newRequired;
            else if (!newPassword2.trim()) error.textContent = messages.confirmRequired;
            else if (newPassword1 !== newPassword2) error.textContent = messages.mismatch;
            else {
                error.textContent = '';
                $.ajax({
                    type: 'post',
                    url: '/user/updatePassword',
                    data: {oldPassword: oldPassword, newPassword1: newPassword1, newPassword2: newPassword2},
                    dataType: 'json',
                    success: function (response) {
                        if (response.code === 200) window.location.href = '/user/setup.html';
                        else handleFailure(response, error);
                    },
                    error: function () { window.layer.alert(messages.network); }
                });
            }
        });
    }

    if (mode === 'setup') initializeSetup();
    else if (mode === 'name') initializeName();
    else if (mode === 'sex') initializeSex();
    else if (mode === 'password') initializePassword();
})(window, document, window.jQuery);
