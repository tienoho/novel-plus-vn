(function (window, document, $) {
    'use strict';

    var root = document.getElementById('bookCommentPage');
    if (!root || !$) return;

    var mode = root.getAttribute('data-comment-mode') === 'reply' ? 'reply' : 'comment';
    var messages = {
        item: root.getAttribute('data-comment-unit') || '',
        trainee: root.getAttribute('data-comment-trainee') || '',
        reader: root.getAttribute('data-comment-reader') || '',
        floor: root.getAttribute('data-comment-floor') || '',
        unlike: root.getAttribute('data-comment-unlike') || '',
        like: root.getAttribute('data-comment-like') || '',
        reply: root.getAttribute('data-comment-reply') || '',
        network: root.getAttribute('data-network-error') || ''
    };
    var config = mode === 'reply' ? {
        inputId: 'commentId',
        requestKey: 'commentId',
        listUrl: '/book/listCommentReplyByPage',
        likeUrl: '/book/toggleReplyLike',
        unlikeUrl: '/book/toggleReplyUnLike',
        toggleKey: 'replyId',
        contentKey: 'replyContent'
    } : {
        inputId: 'bookId',
        requestKey: 'bookId',
        listUrl: '/book/listCommentByPage',
        likeUrl: '/book/toggleCommentLike',
        unlikeUrl: '/book/toggleCommentUnLike',
        toggleKey: 'commentId',
        contentKey: 'commentContent'
    };
    var subjectId = numeric(document.getElementById(config.inputId).value);

    function numeric(value) {
        var result = String(value == null ? '' : value);
        return /^\d+$/.test(result) ? result : null;
    }

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

    function countText(value) {
        return '(' + safeCount(value) + ')';
    }

    function showAlert(message) {
        if (typeof window.novelAlertText === 'function') window.novelAlertText(String(message || ''));
        else if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(String(message || ''));
    }

    function networkError() {
        if (window.layer && typeof window.layer.alert === 'function') window.layer.alert(messages.network);
        else showAlert(messages.network);
    }

    function actionLink(action, id, label, count, counterPrefix) {
        var wrapper = element('span', 'fr');
        var link = element('a', 'zan', label);
        link.href = '#comment-' + action;
        link.style.paddingLeft = '10px';
        link.setAttribute('data-book-detail-action', 'comment-' + action);
        link.setAttribute('data-comment-id', id);
        var counter = element('i', 'num', countText(count));
        counter.id = counterPrefix + id;
        link.appendChild(counter);
        wrapper.appendChild(link);
        return wrapper;
    }

    function renderEntry(entry, total, page, limit, index) {
        var id = numeric(entry.id);
        if (!id) return null;

        var container = element('div', 'comment_list cf');
        var head = element('div', 'user_heads fl');
        var photo = element('img', 'user_head');
        photo.alt = '';
        photo.src = safePhoto(entry.createUserPhoto || '/images/man.png');
        head.appendChild(photo);
        var level = element('span', 'user_level1', messages.trainee);
        level.style.display = 'none';
        head.appendChild(level);
        container.appendChild(head);

        var list = element('ul', 'pl_bar fr');
        var name = element('li', 'name', entry.createUserName);
        if (entry.location) {
            var location = element('span', 'other', String(entry.location) + ' ' + messages.reader);
            location.style.paddingLeft = '10px';
            name.appendChild(location);
        }
        list.appendChild(name);
        list.appendChild(element('li', 'dec', entry[config.contentKey]));

        var actions = element('li', 'other cf');
        if (mode === 'reply') {
            var floor = Math.max(0, total - ((page - 1) * limit + index));
            var floorNode = element('span', 'time fl', floor + ' ' + messages.floor);
            floorNode.style.paddingRight = '10px';
            actions.appendChild(floorNode);
        }
        actions.appendChild(element('span', 'time fl', entry.createTime));
        actions.appendChild(actionLink('unlike', id, messages.unlike, entry.unLikesCount, 'unLikeCount'));
        actions.appendChild(actionLink('like', id, messages.like, entry.likesCount, 'likeCount'));

        if (mode === 'comment') {
            var replyWrapper = element('span', 'fr');
            var replyLink = element('a', 'zan', messages.reply);
            replyLink.href = '/book/reply-' + encodeURIComponent(id) + '.html';
            replyLink.style.paddingLeft = '10px';
            replyLink.appendChild(element('i', 'num', countText(entry.replyCount)));
            replyWrapper.appendChild(replyLink);
            actions.appendChild(replyWrapper);
        }

        list.appendChild(actions);
        container.appendChild(list);
        return container;
    }

    function renderPagination(total, page, limit) {
        var pageElement = document.getElementById('commentPage');
        if (!pageElement) return;
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
                    if (!first) window.loadCommentList(obj.curr, obj.limit);
                }
            });
        });
    }

    window.loadCommentList = function (currentPage, pageSize) {
        if (!subjectId) return;
        var page = positiveInteger(currentPage, 1);
        var limit = positiveInteger(pageSize, 20);
        var request = {curr: page, limit: limit};
        request[config.requestKey] = subjectId;

        $.ajax({
            type: 'get',
            url: config.listUrl,
            data: request,
            dataType: 'json',
            success: function (response) {
                if (response.code !== 200) {
                    showAlert(response.msg);
                    return;
                }
                var data = response.data || {};
                var entries = Array.isArray(data.list) ? data.list : [];
                var total = safeCount(data.total);
                var actualPage = positiveInteger(data.pageNum, page);
                var actualLimit = positiveInteger(data.pageSize, limit);
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < entries.length; i++) {
                    var rendered = renderEntry(entries[i] || {}, total, actualPage, actualLimit, i);
                    if (rendered) fragment.appendChild(rendered);
                }
                document.getElementById('commentPanel').replaceChildren(fragment);
                document.getElementById('bookCommentTotal').textContent = '(' + total + ' ' + messages.item + ')';
                $('#noCommentPanel').toggle(total === 0);
                $('#commentPanel').toggle(total > 0);
                renderPagination(total, actualPage, actualLimit);
            },
            error: networkError
        });
    };

    function toggleReaction(endpoint, counterPrefix, entryId) {
        var id = numeric(entryId);
        if (!id) return;
        var request = {};
        request[config.toggleKey] = id;
        $.ajax({
            type: 'post',
            url: endpoint,
            data: request,
            dataType: 'json',
            success: function (response) {
                if (response.code === 200) {
                    var counter = document.getElementById(counterPrefix + id);
                    if (counter) counter.textContent = countText(response.data);
                } else if (response.code === 1001) {
                    window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                } else {
                    showAlert(response.msg);
                }
            },
            error: networkError
        });
    }

    window.toggleCommentLike = function (id) {
        toggleReaction(config.likeUrl, 'likeCount', id);
    };
    window.toggleCommentUnLike = function (id) {
        toggleReaction(config.unlikeUrl, 'unLikeCount', id);
    };

    var authorId = numeric(root.getAttribute('data-author-id'));
    if (mode === 'comment' && !authorId) $('#authorPanel').hide();

    $('#txtComment').on('input propertychange', function () {
        var value = String($(this).val() || '');
        if (value.length > 1000) {
            value = value.substring(0, 1000);
            $(this).val(value);
        }
        $('#emCommentNum').text(value.length + '/1000');
    });

    window.loadCommentList(1, 20);
})(window, document, window.jQuery);
