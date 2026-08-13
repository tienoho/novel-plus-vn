(function (window, document, $) {
    'use strict';
    var root = document.getElementById('bookDetailPage');
    if (!root) return;

    var messages = {
        chapter: root.getAttribute('data-chapter-unit') || '',
        network: root.getAttribute('data-network-error') || '',
        comment: root.getAttribute('data-comment-unit') || '',
        trainee: root.getAttribute('data-comment-trainee') || '',
        reader: root.getAttribute('data-comment-reader') || '',
        unlike: root.getAttribute('data-comment-unlike') || '',
        like: root.getAttribute('data-comment-like') || '',
        reply: root.getAttribute('data-comment-reply') || ''
    };
    var bookId = String($('#bookId').val() || '');
    window.currentBId = /^\d+$/.test(bookId) ? Number(bookId) : 0;
    window.spmymoney = 0;

    function apiError() { window.layer.alert(messages.network); }
    function numeric(value) {
        var result = String(value == null ? '' : value);
        return /^\d+$/.test(result) ? result : null;
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
    function countText(value) { return '(' + (Number(value) || 0) + ')'; }

    function commentAction(action, id, label, count, countId) {
        var wrapper = element('span', 'fr');
        var link = element('a', 'zan');
        link.href = '#comment-' + action;
        link.style.paddingLeft = '10px';
        link.setAttribute('data-book-detail-action', 'comment-' + action);
        link.setAttribute('data-comment-id', id);
        link.appendChild(document.createTextNode(label));
        var counter = element('i', 'num', countText(count));
        if (countId) counter.id = countId + id;
        link.appendChild(counter);
        wrapper.appendChild(link);
        return wrapper;
    }

    function renderComment(comment) {
        var id = numeric(comment.id);
        if (!id) return null;
        var container = element('div', 'comment_list cf');
        var head = element('div', 'user_heads fl');
        var photo = element('img', 'user_head');
        photo.alt = '';
        photo.src = safePhoto(comment.createUserPhoto || '/images/man.png');
        head.appendChild(photo);
        var level = element('span', 'user_level1', messages.trainee);
        level.style.display = 'none';
        head.appendChild(level);
        container.appendChild(head);

        var list = element('ul', 'pl_bar fr');
        var name = element('li', 'name', comment.createUserName);
        if (comment.location) {
            var location = element('span', 'other', String(comment.location) + ' ' + messages.reader);
            location.style.paddingLeft = '10px';
            name.appendChild(location);
        }
        list.appendChild(name);
        list.appendChild(element('li', 'dec', comment.commentContent));
        var actions = element('li', 'other cf');
        actions.appendChild(element('span', 'time fl', comment.createTime));
        actions.appendChild(commentAction('unlike', id, messages.unlike, comment.unLikesCount, 'unLikeCount'));
        actions.appendChild(commentAction('like', id, messages.like, comment.likesCount, 'likeCount'));
        var reply = element('span', 'fr');
        var replyLink = element('a', 'zan', messages.reply);
        replyLink.href = '/book/reply-' + encodeURIComponent(id) + '.html';
        replyLink.style.paddingLeft = '10px';
        replyLink.appendChild(element('i', 'num', countText(comment.replyCount)));
        reply.appendChild(replyLink);
        actions.appendChild(reply);
        list.appendChild(actions);
        container.appendChild(list);
        return container;
    }

    window.loadCommentList = function () {
        $.ajax({
            type: 'get', url: '/book/listCommentByPage', data: {bookId: bookId}, dataType: 'json',
            success: function (response) {
                if (response.code !== 200) { window.novelAlertText(response.msg); return; }
                var comments = response.data && Array.isArray(response.data.list) ? response.data.list : [];
                var panel = document.getElementById('commentPanel');
                var fragment = document.createDocumentFragment();
                for (var i = 0; i < comments.length; i++) {
                    var rendered = renderComment(comments[i] || {});
                    if (rendered) fragment.appendChild(rendered);
                }
                panel.replaceChildren(fragment);
                $('#bookCommentTotal').text('(' + (Number(response.data.total) || 0) + ' ' + messages.comment + ')');
                var hasComments = panel.children.length > 0;
                $('#commentPanel,#moreCommentPanel').toggle(hasComments);
                $('#noCommentPanel').toggle(!hasComments);
            }, error: apiError
        });
    };

    function toggleComment(endpoint, prefix, commentId) {
        var id = numeric(commentId);
        if (!id) return;
        $.ajax({
            type: 'post', url: endpoint, data: {commentId: id}, dataType: 'json',
            success: function (response) {
                if (response.code === 200) $('#' + prefix + id).text(countText(response.data));
                else if (response.code === 1001) window.location.href = '/user/login.html?originUrl=' + encodeURIComponent(window.location.href);
                else window.novelAlertText(response.msg);
            }, error: apiError
        });
    }
    window.toggleCommentLike = function (id) { toggleComment('/book/toggleCommentLike', 'likeCount', id); };
    window.toggleCommentUnLike = function (id) { toggleComment('/book/toggleCommentUnLike', 'unLikeCount', id); };

    var authorId = numeric(root.getAttribute('data-author-id'));
    if (!authorId) $('#authorPanel').hide();
    var lastIndexId = String($('#lastBookIndexId').val() || '');
    if (numeric(bookId) && numeric(lastIndexId)) {
        $.ajax({
            type: 'get', url: '/book/queryBookIndexAbout',
            data: {bookId: bookId, lastBookIndexId: lastIndexId}, dataType: 'json',
            success: function (response) {
                if (response.code !== 200) { window.novelAlertText(response.msg); return; }
                $('#bookIndexCount').text('(' + (Number(response.data.bookIndexCount) || 0) + ' ' + messages.chapter + ')');
                $('#lastBookContent').text(String(response.data.lastBookContent == null ? '' : response.data.lastBookContent) + '...');
            }, error: apiError
        });
    } else $('#optBtn').remove();

    $.ajax({
        type: 'get', url: '/user/queryIsInShelf', data: {bookId: bookId}, dataType: 'json',
        success: function (response) {
            if (response.code === 200 && response.data) window.BookDetail.renderFavoriteSaved(0);
            else if (response.code !== 200 && response.code !== 1001) window.novelAlertText(response.msg);
        }, error: apiError
    });

    $(function () {
        $('.icon_show').click(function () { $(this).hide(); $('.icon_hide').show(); $('.intro_txt').innerHeight('auto'); });
        $('.icon_hide').click(function () { $(this).hide(); $('.icon_show').show(); $('.intro_txt').innerHeight(''); });
        $('#AuthorOtherNovel li').unbind('mouseover');
        $('#txtComment').on('input propertychange', function () {
            if ($(this).val().length > 1000) $(this).val($(this).val().substring(0, 1000));
            $('#emCommentNum').text($(this).val().length + '/1000');
        });
    });
    if (numeric(bookId)) $.post('/book/addVisitCount', {bookId: bookId}, function () {});
})(window, document, window.jQuery);
