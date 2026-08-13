layui.use(['layer', 'jquery'], function () {
    var layer = layui.layer;
    var $ = layui.jquery;

    function LastRead() {
        this.bookList = "bookcms_book_list"
    }

    LastRead.prototype = {
        set: function (title,article_id, chapter_title,chapter_id, author, category,source_id) {
           if (!(article_id && title && chapter_title && chapter_id && author && category && source_id)) return false;
            var value =  title + '#' + article_id + '#' + chapter_title + '#' + chapter_id + '#' + author + '#' + category + '#' + source_id;

            this.setItem(article_id, value);
            this.setBook(article_id);
            return true;
        },
        get: function (k) {
            return this.getItem(k) ? this.getItem(k).split("#") : "";
        },
        remove: function (k) {
            this.removeItem(k);
            this.removeBook(k)
        },
        setBook: function (v) { // Lưu truyện
            var reg = new RegExp("(^|#)" + v);
            var books = this.getItem(this.bookList);
            if (books == "") {
                books = v
            }
            else {
                if (books.search(reg) == -1) {
                    books += "#" + v
                }
                else {
                    books.replace(reg, "#" + v)
                }
            }
            this.setItem(this.bookList, books)
        },
        getBook: function () {
            var v = this.getItem(this.bookList) ? this.getItem(this.bookList).split("#") : Array();
            var books = Array();
            if (v.length) {
                for (var i = 0; i < v.length; i++) {
                    var tem = this.getItem(v[i]).split('#');
                    if (tem.length > 3) books.push(tem);
                }
            }
            return books
        },
        removeBook: function (v) {
            var reg = new RegExp("(^|#)" + v);
            var books = this.getItem(this.bookList);
            if (!books) {
                books = ""
            }
            else {
                if (books.search(reg) != -1) {
                    books = books.replace(reg, "")
                }
            }
            this.setItem(this.bookList, books)
        },
        setItem: function (k, v) { // Lưu chương
            if (!!window.localStorage) {
                localStorage.setItem(k, v);
            } else {
                var expireDate = new Date();
                var EXPIR_MONTH = 30 * 24 * 3600 * 1000;
                expireDate.setTime(expireDate.getTime() + 12 * EXPIR_MONTH)
                document.cookie = k + "=" + encodeURIComponent(v) + ";expires=" + expireDate.toGMTString() + "; path=/";
            }
        },
        getItem: function (k) {
            var value = ""
            var result = ""
            if (!!window.localStorage) {
                result = window.localStorage.getItem(k);
                value = result || "";
            }
            else {
                var reg = new RegExp("(^| )" + k + "=([^;]*)(;|\x24)");
                var result = reg.exec(document.cookie);
                if (result) {
                    value = decodeURIComponent(result[2]) || ""
                }
            }
            return value
        },
        removeItem: function (k) {
            if (!!window.localStorage) {
                window.localStorage.removeItem(k);
            } else {
                var expireDate = new Date();
                expireDate.setTime(expireDate.getTime() - 1000)
                document.cookie = k + "= " + ";expires=" + expireDate.toGMTString()
            }
        },
        removeAll: function () {
            if (!!window.localStorage) {
                window.localStorage.clear();
            }
            else {
                var v = this.getItem(this.bookList) ? this.getItem(this.bookList).split("#") : Array();
                var books = Array();
                if (v.length) {
                    for (i in v) {
                        var tem = this.removeItem(v[k])
                    }
                }
                this.removeItem(this.bookList)
            }
        }
    };

    function zzleft(mainStr, lngLen) {
        if (lngLen > 0) {
            return mainStr.substring(0, lngLen)
        }
        else {
            return null
        }
    }

    window.lastread = new LastRead();

    function appendShelfCell(row, className, text, bold) {
        var cell = $('<span>').addClass(className);
        var content = bold ? $('<b>') : cell;
        content.text(String(text == null ? '' : text));
        if (bold) {
            cell.append(content);
        }
        row.append(cell);
        return cell;
    }

    function safeShelfId(value) {
        var id = String(value == null ? '' : value);
        return /^\d+$/.test(id) ? id : null;
    }

    function safeShelfUrl(rule, replacements) {
        var value = String(rule == null ? '' : rule);
        for (var key in replacements) {
            if (Object.prototype.hasOwnProperty.call(replacements, key)) {
                value = value.replace('{' + key + '}', replacements[key]);
            }
        }
        try {
            var parsed = new URL(value, window.location.origin);
            if (parsed.origin !== window.location.origin || !/^https?:$/.test(parsed.protocol)) {
                return null;
            }
            return parsed.pathname + parsed.search + parsed.hash;
        } catch (error) {
            return null;
        }
    }

    function appendShelfHeader(list) {
        var row = $('<li>');
        appendShelfCell(row, 's1', novelMessage('bookCategory', 'Danh mục truyện'), true);
        appendShelfCell(row, 's2', novelMessage('bookName', 'Tên truyện'), true);
        appendShelfCell(row, 's3', novelMessage('blueLastChapter', 'Chương đọc gần nhất'), true);
        appendShelfCell(row, 's4', novelMessage('author', 'Tác giả'), true);
        appendShelfCell(row, 's5', novelMessage('actions', 'Thao tác'), true);
        appendShelfCell(row, 's6', '\u00a0', true);
        appendShelfCell(row, 's7', '\u00a0', true);
        list.append(row);
    }

    function appendShelfBook(list, book) {
        var articleId = safeShelfId(book[1]);
        var chapterId = safeShelfId(book[3]);
        var sourceId = safeShelfId(book[6]);
        if (!articleId || !chapterId || !sourceId) {
            return;
        }
        var articleUrl = safeShelfUrl(article_rule, {article_id: articleId});
        var chapterUrl = safeShelfUrl(chapter_rule, {article_id: sourceId, chapter_id: chapterId});
        if (!articleUrl || !chapterUrl) {
            return;
        }

        var row = $('<li>');
        appendShelfCell(row, 's1', book[5], false);
        var titleCell = appendShelfCell(row, 's2', '', false);
        titleCell.append($('<a>').attr({href: articleUrl, target: '_blank', rel: 'noopener noreferrer'}).text(String(book[0] == null ? '' : book[0])));
        var chapterCell = appendShelfCell(row, 's3', '', false);
        chapterCell.append($('<a>').attr({href: chapterUrl, target: '_blank', rel: 'noopener noreferrer'}).text(String(book[2] == null ? '' : book[2])));
        appendShelfCell(row, 's4', book[4], false);
        var actionCell = appendShelfCell(row, 's5', '', false);
        actionCell.append($('<a>')
            .attr('href', '#remove-book')
            .attr('data-id', articleId)
            .attr('title', novelMessage('blueRemoveTitle', 'Xóa “{0}”?').replace('{0}', String(book[0] == null ? '' : book[0])))
            .addClass('remove-book')
            .text(novelMessage('blueRemove', 'Xóa')));
        appendShelfCell(row, 's6', '\u00a0', false);
        appendShelfCell(row, 's7', '\u00a0', false);
        list.append(row);
    }

    $(function () {

        $(".link-bookshelf").on("click",function() {

            var books = lastread.getBook().reverse();

            if (($(".bookshelf-list").length - 1) == books.length)  {
                $(".bookshelf-mask").show();
                $(".bookshelf-panel").show();
            } else {

                var html = "";

                html += "<div class=\"bookshelf-mask\"></div>";
                html += "<div class=\"bookshelf-panel\">";
                html += "<div class=\"bookshelf-head\"><h4>" + novelMessage('blueShelfTitle', 'Tủ sách của tôi ({0} truyện)').replace('{0}', books.length) + "</h4><a class=\"close\" target=\"_self\">" + novelMessage('close', 'Đóng') + "</a></div>";
                html += "<div class=\"bookshelf-list\">";
                html += "<div class=\"clearfix title\"><span class=\"label\"></span><em>" + novelMessage('blueShelfNote', 'Truyện đã đọc được tự động lưu trên thiết bị này.') + "</em></div>";
                html += "</div>";
                html += "</div>";
                $("body").append(html);

                var bookList = $(".bookshelf-list");
                appendShelfHeader(bookList);

                if (books.length) {
                    for (var i = 0; i < books.length; i++) {
                        if (i <= 100) {
                            appendShelfBook(bookList, books[i]);
                        }
                    }
                } else {
                    bookList.append($('<div>').css({height: '100px', lineHeight: '100px', textAlign: 'center'})
                        .text(novelMessage('blueShelfEmpty', 'Chưa có truyện nào trong tủ sách.')));
                }

            }
        });
        $(".bookshelf-head .close").live("click", function() {
            $(".bookshelf-mask").hide();
            $(".bookshelf-panel").hide();
        });

        $(".bookshelf-list li a.remove-book").live("click", function() {
            var _this =  $(this);
            layer.confirm(novelMessage('blueRemoveConfirm', 'Bạn có chắc muốn xóa truyện khỏi tủ sách?'), {title: novelMessage('notice', 'Thông báo')}, function(index){

                lastread.remove(_this.data('id'));
                _this.parent().parent().slideUp(300, function(){
                    $(this).remove();
                    var books = lastread.getBook().reverse();
                    $(".bookshelf-head h4").text(novelMessage('blueShelfTitle', 'Tủ sách của tôi ({0} truyện)').replace('{0}', books.length));
                });

                layer.close(index);
            });
        });

    });
});
