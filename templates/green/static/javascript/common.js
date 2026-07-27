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


var token = $.cookie('Authorization');
if (!token) {
    if (needLoginPath.indexOf(window.location.pathname) != -1) {
        location.href = '/user/login.html?originUrl=' + encodeURIComponent(location.href);
    }

    $(".user_link").html("<i class=\"line mr20\">|</i><a href=\"/user/login.html\" class=\"mr15\">" + novelMessage('login', 'Đăng nhập') + "</a><a href=\"/user/register.html\">" + novelMessage('register', 'Đăng ký') + "</a>");
} else {
    $.ajax({
        type: "POST",
        url: "/user/refreshToken",
        data: {},
        dataType: "json",
        success: function (data) {
            if (data.code == 200) {
                $(".user_link").html("<i class=\"line mr20\">|</i>" +
                    "<a href=\"/user/userinfo.html\"  class=\"mr15\">" + data.data.nickName + "</a>" +
                    "<a href=\"javascript:logout()\">" + novelMessage('logout', 'Đăng xuất') + "</a>");
                ;
                if ("/user/login.html" == window.location.pathname) {
                    var orginUrl = getSearchString("originUrl");
                    window.location.href = orginUrl == undefined || orginUrl.isBlank() ? "/" : orginUrl;
                    return;
                }
                isLogin = true;
                if (localStorage.getItem("autoLogin") == 1) {
                    $.cookie('Authorization', data.data.token, {expires: 7, path: '/'});
                } else {
                    $.cookie('Authorization', data.data.token, {path: '/'});
                }
            } else {
                if (needLoginPath.indexOf(window.location.pathname) != -1) {
                    location.href = '/user/login.html';
                }
                $(".user_link").html("<i class=\"line mr20\">|</i><a href=\"/user/login.html\" class=\"mr15\">" + novelMessage('login', 'Đăng nhập') + "</a><a href=\"/user/register.html\">" + novelMessage('register', 'Đăng ký') + "</a>");
            }
        },
        error: function () {
            layer.alert(novelMessage('networkError', 'Không thể kết nối mạng'));
        }

    });
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
    $.cookie('Authorization', null, {path: '/'});
    location.reload();
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

