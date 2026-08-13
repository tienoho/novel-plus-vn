var UserUtil = {
    msgStyle: 'background-color:#333; color:#fff; text-align:center; border:none; font-size:20px; padding:10px;',
    GetFavoritesNew: function () {
        var bIdList = "";
        $(".book_list").each(function () {
            bIdList += "," + $(this).attr("vals");
        });
        if (bIdList != "") {
        }
    },
    GetHistory: function () {
        var bIdList = "";
        $(".book_list").each(function () {
            bIdList += "," + $(this).attr("vals");
        });
        if (bIdList != "") {
        }
    },
    GetChapterInfo: function () {
        var cIdList = "";
        $(".showCName").each(function () {
            cIdList += "," + $(this).attr("vals");
        });
        if (cIdList != "") {
        }
    },
    SignDay: function () {
        if (!signed) {
            signed = true;
        }
    },
    SignDayStatus: function () {
    },
    RegSendSms: function () {
        var mob = $("#txtUName").val();
        var cCode = $("#TxtChkCode").val();
        if (mob != "" && cCode != "") {
            $("#btnSendSms").attr("disabled", "disabled");
            $("#txtUName").attr("readonly", "true");
        }
        else {
            layer.open({
                content: novelMessage('phoneCodeRequired', 'Vui lòng nhập số điện thoại và mã xác minh.'),
                style: UserUtil.msgStyle,
                time: 2
            });
        }
    },
    GetPassSendSms: function () {
        var mob = $("#txtMobile").val();
        var cCode = $("#TxtChkCode").val();
        if (mob != "" && cCode != "") {
            $("#btnSendSms").attr("disabled", "disabled");
            $("#txtMobile").attr("readonly", "true");
        }
        else {
            layer.open({
                content: novelMessage('phoneCodeRequired', 'Vui lòng nhập số điện thoại và mã xác minh.'),
                style: UserUtil.msgStyle,
                time: 2
            });
        }
    },
    RegSmsWait: function () {
        if (secondStep > 0) {
            $("#btnSendSms").val(novelMessage('smsResend', 'Gửi lại ({0})').replace('{0}', secondStep));
            secondStep--;
            setTimeout(UserUtil.RegSmsWait, 1000);
        }
        else {
            secondStep = 180;
            $("#btnSendSms").val(novelMessage('smsGetAgain', 'Lấy lại mã xác minh'));
            $("#btnSendSms").removeAttr("disabled");
            $("#txtUName").removeAttr("readonly");
        }
    }
};

function novelCreateBookTableRow(book, options) {
    var dom = window.NovelSafeDom;
    var bookId = dom.positiveId(book && book.bookId);
    if (!bookId) {
        return null;
    }
    options = options || {};
    var row = dom.element("tr", "book_list");
    row.setAttribute("vals", bookId);
    if (options.removable) {
        row.id = "shelf" + bookId;
    }

    var categoryCell = dom.element("td", "style bookclass");
    var categoryId = dom.positiveId(book.catId);
    var categoryText = "[" + dom.text(book.catName) + "]";
    categoryCell.appendChild(categoryId
        ? dom.link("/book/bookclass.html?c=" + categoryId, null, categoryText)
        : dom.element("span", null, categoryText));
    row.appendChild(categoryCell);

    var nameCell = dom.element("td", "name");
    nameCell.appendChild(dom.link("/book/" + bookId + ".html", null, book.bookName));
    row.appendChild(nameCell);

    var chapterCell = dom.element("td", "chapter");
    var lastIndexId = dom.positiveId(book.lastIndexId);
    chapterCell.appendChild(lastIndexId
        ? dom.link("/book/" + bookId + "/" + lastIndexId + ".html", null, book.lastIndexName)
        : dom.element("span", null, book.lastIndexName));
    row.appendChild(chapterCell);

    row.appendChild(dom.element("td", "time", book.lastIndexUpdateTime));

    var actionCell = dom.element("td", "goread");
    var previousContentId = dom.positiveId(book.preContentId);
    if (previousContentId) {
        var continueContainer = dom.element("div");
        continueContainer.appendChild(dom.link("/book/" + bookId + "/" + previousContentId + ".html",
            null, options.continueText));
        actionCell.appendChild(continueContainer);
    }
    if (options.removable) {
        var removeContainer = dom.element("div");
        removeContainer.style.lineHeight = "8px";
        removeContainer.style.paddingBottom = "13px";
        var removeLink = dom.link("#", null, options.removeText);
        removeLink.dataset.cspAction = "remove-from-bookshelf";
        removeLink.dataset.bookId = bookId;
        removeContainer.appendChild(removeLink);
        actionCell.appendChild(removeContainer);
    }
    row.appendChild(actionCell);
    return row;
}

function novelRenderBookTable(targetId, books, options) {
    var target = document.getElementById(targetId);
    var fragment = document.createDocumentFragment();
    (Array.isArray(books) ? books : []).forEach(function (book) {
        var row = novelCreateBookTableRow(book, options);
        if (row) {
            fragment.appendChild(row);
        }
    });
    target.replaceChildren(fragment);
}

function novelRenderMobileBookHistory(targetId, books, labels) {
    var dom = window.NovelSafeDom;
    var fragment = document.createDocumentFragment();
    (Array.isArray(books) ? books : []).forEach(function (book) {
        var bookId = dom.positiveId(book && book.bookId);
        var contentId = dom.positiveId(book && book.preContentId);
        if (!bookId || !contentId) {
            return;
        }
        var href = "/book/" + bookId + "/" + contentId + ".html";
        var row = dom.element("div", "layui-row");
        row.style.marginBottom = "10px";
        row.style.padding = "10px";
        row.style.background = "#f2f2f2";

        var coverLink = dom.link(href);
        var coverColumn = dom.element("div", "layui-col-xs6 layui-col-sm3 layui-col-md2 layui-col-lg2");
        coverColumn.style.textAlign = "center";
        var cover = dom.image(book.picUrl, book.bookName, "/images/default.gif");
        cover.style.width = "130px";
        cover.style.height = "180px";
        coverColumn.appendChild(cover);
        coverLink.appendChild(coverColumn);
        row.appendChild(coverLink);

        var details = dom.element("div", "layui-col-xs6 layui-col-sm8 layui-col-md8 layui-col-lg8");
        details.style.padding = "10px";
        var titleLink = dom.link(href);
        var title = dom.element("div", "line-limit-length", book.bookName);
        title.style.color = "#000";
        title.style.fontSize = "15px";
        titleLink.appendChild(title);
        details.appendChild(titleLink);

        var authorLink = dom.link(href);
        var author = dom.element("div", "line-limit-length",
            labels.author + ": " + dom.text(book.authorName));
        author.style.color = "#a6a6a6";
        authorLink.appendChild(author);
        details.appendChild(authorLink);
        details.appendChild(novelMobileHistoryLine(labels.category + ": " + dom.text(book.catName)));
        details.appendChild(novelMobileHistoryLine(labels.status + ": "
            + (Number(book.bookStatus) === 0 ? labels.serializing : labels.completed)));

        var updated = novelMobileHistoryLine(labels.updated + ": ");
        var updatedValue = dom.element("i", null, dom.text(book.lastIndexUpdateTime).slice(0, 11));
        updatedValue.style.color = "red";
        updated.appendChild(updatedValue);
        details.appendChild(updated);

        var description = dom.text(book.bookDesc)
            .replace(/<[^>]*>/g, " ").replace(/&nbsp;/gi, " ").replace(/\s+/g, " ").trim();
        if (description.length > 15) {
            description = description.slice(0, 15) + "...";
        }
        details.appendChild(novelMobileHistoryLine(labels.description + ": " + description));
        row.appendChild(details);
        fragment.appendChild(row);
    });
    document.getElementById(targetId).replaceChildren(fragment);
}

function novelMobileHistoryLine(value) {
    var line = window.NovelSafeDom.element("div", null, value);
    line.style.marginTop = "5px";
    line.style.color = "#a6a6a6";
    return line;
}
