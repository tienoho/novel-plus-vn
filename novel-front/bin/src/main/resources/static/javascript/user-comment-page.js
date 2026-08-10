(function (window, document, $) {
    'use strict';

    var root = document.getElementById('userCommentPage');
    if (!root || !$) return;

    var traineeText = root.getAttribute('data-comment-trainee') || '';
    var networkErrorText = root.getAttribute('data-network-error') || '';

    function positiveInteger(value, fallback) {
        var parsed = Number(value);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
    }

    function safeCount(value) {
        var parsed = Number(value);
        return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : 0;
    }

    function safePhoto(value) {
        var photo = String(value == null ? '' : value);
        return /^(?:\/files\/[A-Za-z0-9._~!$&'()*+,;=:@%\/-]+|\/images\/man\.png)$/.test(photo)
            ? photo : '/images/man.png';
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = String(text);
        return node;
    }

    function maskedName(value) {
        var name = String(value == null ? '' : value);
        if (!name) return '';
        return name.slice(0, 4) + '****' + name.slice(Math.max(0, name.length - 3));
    }

    function renderComment(comment) {
        var container = element('div', 'comment_list cf');
        var head = element('div', 'user_heads fl');
        var photo = element('img', 'user_head');
        photo.alt = '';
        photo.src = safePhoto(comment.createUserPhoto || '/images/man.png');
        head.appendChild(photo);
        var level = element('span', 'user_level1', traineeText);
        level.style.display = 'none';
        head.appendChild(level);
        container.appendChild(head);

        var list = element('ul', 'pl_bar fr');
        list.appendChild(element('li', 'name', maskedName(comment.createUserName)));
        list.appendChild(element('li', 'dec', comment.commentContent));
        var meta = element('li', 'other cf');
        meta.appendChild(element('span', 'time fl', comment.createTime));
        list.appendChild(meta);
        container.appendChild(list);
        return container;
    }

    function renderPagination(total, page, limit) {
        var pageElement = document.getElementById('commentPage');
        if (total === 0) {
            pageElement.replaceChildren();
            return;
        }
        if (!window.layui || typeof window.layui.use !== 'function') return;
        window.layui.use('laypage', function () {
            window.layui.laypage.render({
                elem: 'commentPage',
                count: total,
                curr: page,
                limit: limit,
                jump: function (obj, first) {
                    if (!first) window.loadUserComments(obj.curr, obj.limit);
                }
            });
        });
    }

    window.loadUserComments = function (currentPage, pageSize) {
        var page = positiveInteger(currentPage, 1);
        var limit = positiveInteger(pageSize, 5);
        $.ajax({
            type: 'get',
            url: '/user/listCommentByPage',
            data: {curr: page, limit: limit},
            dataType: 'json',
            success: function (response) {
                if (response.code === 1001) {
                    window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                    return;
                }
                if (response.code !== 200) {
                    if (typeof window.novelAlertText === 'function') window.novelAlertText(response.msg);
                    return;
                }
                var data = response.data || {};
                var comments = Array.isArray(data.list) ? data.list : [];
                var total = safeCount(data.total);
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < comments.length; i++) fragment.appendChild(renderComment(comments[i] || {}));
                document.getElementById('commentBar').replaceChildren(fragment);
                $('.no_comment').toggle(total === 0);
                $('#commentBar').toggle(total > 0);
                renderPagination(total, positiveInteger(data.pageNum, page), positiveInteger(data.pageSize, limit));
            },
            error: function () {
                if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(networkErrorText);
            }
        });
    };

    window.loadUserComments(1, 5);
})(window, document, window.jQuery);
