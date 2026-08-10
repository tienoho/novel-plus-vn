var needLoginPath = ['/user/favorites.html', '/user/comment.html', '/user/feedback.html',
    '/user/feedback_list.html', '/user/read_history.html', '/user/set_name.html',
    '/user/set_password.html', '/user/set_sex.html', '/user/setup.html', '/user/userinfo.html',
    "/pay/index.html",
    "/author/register.html", "/author/index.html"];
var isLogin = false;
var url = window.location.search;

function novelMessage(key, fallback) {
    return window.NovelI18n && window.NovelI18n[key] ? window.NovelI18n[key] : fallback;
}

// Lấy giá trị query theo tên key.
function getSearchString(key) {
    var str = url;
    str = str.substring(1, str.length); // Bỏ dấu ? ở đầu query.
    // Tách query thành các cặp tên/giá trị.
    var arr = str.split("&");

    for (var i = 0; i < arr.length; i++) {
        var tmp_arr = arr[i].split("=");
        if (tmp_arr[0] == key) {
            return decodeURIComponent(tmp_arr[1]);
        }
    }
    return undefined;
}

var keyword = getSearchString("k");
if (keyword != undefined) {
    $("#searchKey").val(keyword);
    $("#workDirection").remove();
    $("#idGirl").remove();
}

function searchByK(k) {
    if (!k) {
        window.location.href = '/book/bookclass.html?k=' + encodeURIComponent(document.getElementById("searchKey").value)
    } else {
        window.location.href = '/book/bookclass.html?k=' + encodeURIComponent(k)
    }
}

$("#searchKey").keypress(function (even) {
    if (even.which == 13) {
        even.stopPropagation();
        // Gửi tìm kiếm khi nhấn Enter.
        searchByK();
    }
});
Array.prototype.indexOf = function (val) {
    for (var i = 0; i < this.length; i++) {
        if (this[i] == val) return i;
    }
    return -1;
};


function novelReadCookie(name) {
    var prefix = name + '=';
    var cookies = document.cookie ? document.cookie.split(';') : [];
    for (var i = 0; i < cookies.length; i++) {
        var cookie = cookies[i].trim();
        if (cookie.indexOf(prefix) === 0) {
            var value = cookie.substring(prefix.length);
            try {
                return decodeURIComponent(value);
            } catch (ignored) {
                return value;
            }
        }
    }
    return null;
}

function novelCsrfToken() {
    return novelReadCookie('XSRF-TOKEN');
}

$(document).ajaxSend(function (event, xhr, settings) {
    var method = (settings.type || 'GET').toUpperCase();
    if (!/^(GET|HEAD|OPTIONS|TRACE)$/.test(method)) {
        var csrf = novelCsrfToken();
        if (csrf) {
            xhr.setRequestHeader('X-XSRF-TOKEN', csrf);
        }
    }
});

(function (originalFetch) {
    if (!originalFetch) {
        return;
    }
    window.fetch = function (input, options) {
        options = options || {};
        var method = (options.method || 'GET').toUpperCase();
        var target = typeof input === 'string' ? input : input.url;
        var sameOrigin = new URL(target, window.location.href).origin === window.location.origin;
        if (sameOrigin && !/^(GET|HEAD|OPTIONS|TRACE)$/.test(method)) {
            var headers = new Headers(options.headers || {});
            var csrf = novelCsrfToken();
            if (csrf) {
                headers.set('X-XSRF-TOKEN', csrf);
            }
            options.headers = headers;
        }
        return originalFetch(input, options);
    };
})(window.fetch);


function renderAnonymousUserLinks() {
    $('.user_link').each(function () {
        $(this).empty()
            .append($('<i>').addClass('line mr20').text('|'))
            .append($('<a>').attr('href', '/user/login.html').addClass('mr15').text(novelMessage('login', 'Đăng nhập')))
            .append($('<a>').attr('href', '/user/register.html').text(novelMessage('register', 'Đăng ký')));
    });
}

function renderAuthenticatedUserLinks(nickName) {
    $('.user_link').each(function () {
        $(this).empty()
            .append($('<i>').addClass('line mr20').text('|'))
            .append($('<a>').attr('href', '/user/userinfo.html').addClass('mr15').text(String(nickName == null ? '' : nickName)))
            .append($('<a>').attr('href', '#logout').attr('data-user-action', 'logout').text(novelMessage('logout', 'Đăng xuất')));
    });
}

$(document).off('click.userLogout', '[data-user-action="logout"]').on('click.userLogout', '[data-user-action="logout"]', function (event) {
    event.preventDefault();
    logout();
});

function handleAnonymousSession() {
    if (needLoginPath.indexOf(window.location.pathname) != -1) {
        location.href = '/user/login.html?originUrl=' + encodeURIComponent(location.href);
    }
    renderAnonymousUserLinks();
}

function handleAuthenticatedSession(user) {
    renderAuthenticatedUserLinks(user && user.nickName);
    if ("/user/login.html" == window.location.pathname) {
        var orginUrl = getSearchString("originUrl");
        window.location.href = orginUrl == undefined || orginUrl.isBlank() ? "/" : orginUrl;
        return;
    }
    isLogin = true;
}

function refreshSession() {
    $.ajax({
        type: "POST",
        url: "/user/refreshToken",
        data: {},
        dataType: "json",
        success: function (data) {
            if (data.code == 200) {
                handleAuthenticatedSession(data.data);
            } else {
                handleAnonymousSession();
            }
        },
        error: function () {
            layer.alert(novelMessage('networkError', 'Không thể kết nối mạng'));
        }
    });
}

function resolveSession() {
    $.ajax({
        type: "GET",
        url: "/user/userInfo",
        dataType: "json",
        success: function (data) {
            if (data.code == 200) {
                handleAuthenticatedSession(data.data);
            } else {
                refreshSession();
            }
        },
        error: function () {
            layer.alert(novelMessage('networkError', 'Không thể kết nối mạng'));
        }
    });
}

var token = novelReadCookie('NovelSession');
if (!token) {
    handleAnonymousSession();
} else {
    resolveSession();
}


String.prototype.isPhone = function () {
    var strTemp = /^0(?:3|5|7|8|9)[0-9]{8}$/;
    if (strTemp.test(this)) {
        return true;
    }
    return false;
};

String.prototype.isBlank = function () {
    if (this == null || $.trim(this) == "") {
        return true;
    }
    return false;
};
String.prototype.isNickName = function () {
    var strTemp = /^[\u4E00-\u9FA5A-Za-z0-9_]+$/;
    if (strTemp.test(this)) {
        return true;
    }
    return false;
};


function logout() {
    $.post('/user/logout').always(function () {
        location.reload();
    });
}


function isImg(str) {
    return !str.search("[.]+(jpg|jpeg|swf|gif|png|JPG|JPEG|SWF|GIF|PNG)$");
}


// Kiểm tra tệp ảnh trước khi tải lên.
function checkPicUpload(file) {

    if (!isImg(file.value.substr(file.value.lastIndexOf(".")))) {
        layer.alert(novelMessage('imageOnly', 'Chỉ được tải lên tệp hình ảnh.'));
        return false;
    }
    var fileSize = 0;
    var isIE = /msie/i.test(navigator.userAgent) && !window.opera;
    if (isIE && !file.files) {
        var filePath = file.value;
        var fileSystem = new ActiveXObject("Scripting.FileSystemfileect");
        var file = fileSystem.GetFile(filePath);
        fileSize = file.Size;
    } else {
        fileSize = file.files[0].size;
    }
    fileSize = Math.round(fileSize / 1024 * 100) / 100; // Đơn vị KB.
    if (fileSize >= 1024) {
        layer.alert(novelMessage('imageMax1M', 'Kích thước ảnh không được vượt quá 1 MB.'));
        return false;
    }
    return true;
}

function novelEscapeHtml(value) {
    var container = document.createElement("div");
    container.textContent = value == null ? "" : String(value);
    return container.innerHTML;
}

function novelAlertText(value) {
    layer.alert(novelEscapeHtml(value));
}

