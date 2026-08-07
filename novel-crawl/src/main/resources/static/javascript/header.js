var $C = function (objName) {
    if (typeof (document.getElementById(objName)) != "object")
    { return null; }
    else
    { return document.getElementById(objName); }
}
jQuery.cookie = function (name, value, options) {
    if (typeof value != 'undefined') {
        options = options || {};
        if (value === null) {
            value = '';
            options.expires = -1;
        }
        var expires = '';
        if (options.expires && (typeof options.expires == 'number' || options.expires.toUTCString)) {
            var date;
            if (typeof options.expires == 'number') {
                date = new Date();
                date.setTime(date.getTime() + (options.expires * 24 * 60 * 60 * 1000));
            } else {
                date = options.expires;
            }
            expires = '; expires=' + date.toUTCString();
        }
        var path = options.path ? '; path=' + options.path : '';
        var domain = options.domain ? '; domain=' + options.domain : '';
        var secure = options.secure ? '; secure' : '';
        document.cookie = [name, '=', encodeURIComponent(value), expires, path, domain, secure].join('');
    } else {
        var cookieValue = null;
        if (document.cookie && document.cookie != '') {
            var cookies = document.cookie.split(';');
            for (var i = 0; i < cookies.length; i++) {
                var cookie = jQuery.trim(cookies[i]);
                if (cookie.substring(0, name.length + 1) == (name + '=')) {
                    cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                    break;
                }
            }
        }
        return cookieValue;
    }
};

function crawlCsrfToken() {
    return jQuery.cookie("XSRF-TOKEN");
}

jQuery.ajaxPrefilter(function (options, originalOptions, jqXHR) {
    var method = String(options.type || options.method || "GET").toUpperCase();
    if (/^(GET|HEAD|OPTIONS|TRACE)$/.test(method)) {
        return;
    }

    var target;
    try {
        target = new URL(options.url || window.location.href, window.location.href);
    } catch (ignored) {
        return;
    }
    if (target.origin !== window.location.origin) {
        return;
    }

    var token = crawlCsrfToken();
    if (token) {
        jqXHR.setRequestHeader("X-XSRF-TOKEN", token);
    }
});

$(function () {


    $(".rightList li").mouseover(function () {
        //$($(this).parent()).children().each(function () {
        //    $(this).removeClass("on");
        //});
        //$(this).addClass("on");
    });
    $(".rightList_nobor li").mouseover(function () {
        $($(this).parent()).children().each(function () {
            $(this).addClass("on");
        });
    });

    $("#headerUserHistoryBtn").mouseover(function () {
        HeaderShowUtil.headerShowHistory();
    });
    $("#headerUserHistory").mouseleave(function () {
        HeaderShowUtil.headerHideHistory();
    });
});
function getNote() {
}
function goPage(cpage) {
    location.href = '?page=' + cpage;
}



function isWeiXin() {
    var ua = window.navigator.userAgent.toLowerCase();
    if (ua.indexOf("micromessenger") > 0) {
        return true;
    } else {
        return false;
    }
}

var HeaderShowUtil = {
    headerShowHistory: function (obj) {
        if ($("#headerUserHistory").html().length < 10) {
            var messages = window.crawlCommonMessages || {};
            var recordBox = $("<div>", {"class": "record_box"});
            var title = $("<div>", {"class": "record_title", "id": "hdShowTitle"});
            $("<a>", {"href": "#", "class": "record_tit1 on"})
                .text(messages.recentReading || "")
                .on("click", function (event) {
                    event.preventDefault();
                    HeaderShowUtil.headerShowHistoryLog(this);
                })
                .appendTo(title);
            $("<a>", {"href": "#", "class": "record_tit2"})
                .text(messages.bookshelf || "")
                .on("click", function (event) {
                    event.preventDefault();
                    HeaderShowUtil.headerShowFavLog(this);
                })
                .appendTo(title);
            title.appendTo(recordBox);
            HeaderShowUtil.createRecordList("hdShowHistory", "record_list record_list1", messages.viewAll, false)
                .appendTo(recordBox);
            HeaderShowUtil.createRecordList("hsShowFav", "record_list record_list2", messages.viewAll, true)
                .appendTo(recordBox);
            $("<p>", {"class": "sp"}).appendTo(recordBox);
            $("#headerUserHistory").empty().append(recordBox);
        }
        $("#headerUserHistory").show();
        $("#headerUserHistoryBtn").addClass("on");
        HeaderShowUtil.headerShowHistoryLog();
    },
    headerHideHistory: function () {
        $("#headerUserHistory").hide();
        $("#headerUserHistoryBtn").removeClass("on");
    },
    createRecordList: function (id, className, viewAllText, hidden) {
        var list = $("<div>", {"id": id, "class": className});
        if (hidden) {
            list.hide();
        }
        $("<ul>").appendTo(list);
        $("<a>", {"class": "all", "href": "/"}).text(viewAllText || "").appendTo(list);
        return list;
    },
    headerShowHistoryLog: function (obj) {
        if (obj != undefined) {
            $("#hdShowTitle a").removeClass("on");
            $(obj).addClass("on");
            $("#hdShowHistory").show();
            $("#hsShowFav").hide();
        }
        var cookieHistory = jQuery.cookie("wapviewhistory");
        if (cookieHistory != undefined && cookieHistory.length > 0) {
            var bList, bIdList;
            var bIdArray = new Array();
            var cookieList = cookieHistory.split(',');
            for (var i = 0; i < cookieList.length && i < 3; i++) {
                var str = cookieList[i];
                if (str.indexOf('|') > 0) {
                    bList = str.split('|');
                    if (bList.length == 3) {
                        bIdList += ',' + bList[0].replace("b", "");
                        bIdArray[bList[0].replace("b", "")] = bList[1];
                    }
                }
            }

        }
        else {
            var emptyItem = $("<li>").text((window.crawlCommonMessages || {}).emptyHistory || "");
            $("#hdShowHistory ul").empty().append(emptyItem);
        }
    },
    headerShowFavLog: function (obj) {
        $("#hdShowTitle a").removeClass("on");
        $(obj).addClass("on");
        $("#hsShowFav").show();
        $("#hdShowHistory").hide();
        var uname = jQuery.cookie("waplogname");
        if (uname != undefined && uname != "") {
        }
        else {
            var loginLink = $("<a>", {"href": "/user/login.html"})
                .text((window.crawlCommonMessages || {}).loginRequired || "");
            $("#hsShowFav ul").empty().append($("<li>").append(loginLink));
        }

    }
}

function crawlEscapeHtml(value) {
    var container = document.createElement("div");
    container.textContent = value == null ? "" : String(value);
    return container.innerHTML;
}
