(function (window, document, $) {
    'use strict';

    var root = document.getElementById('bookclassApp');
    if (!root) {
        return;
    }
    var messages = {
        tenThousand: root.getAttribute('data-ten-thousand-unit') || '',
        unlimited: root.getAttribute('data-unlimited') || '',
        networkError: root.getAttribute('data-network-error') || ''
    };

    function numericId(value) {
        var id = String(value == null ? '' : value);
        return /^\d+$/.test(id) ? id : null;
    }

    function createLink(href, text) {
        var link = document.createElement('a');
        link.href = href;
        link.textContent = String(text == null ? '' : text);
        return link;
    }

    function activeValue(selector, attribute) {
        var element = root.querySelector(selector + ' > .on');
        return element ? element.getAttribute(attribute) : null;
    }

    function currentDirection() {
        return activeValue('.so_pd', 'filter-value') || '0';
    }

    function renderBooks(response) {
        var body = document.getElementById('bookList');
        var fragment = document.createDocumentFragment();
        var books = response && response.data && Array.isArray(response.data.list) ? response.data.list : [];
        for (var i = 0; i < books.length; i++) {
            var book = books[i] || {};
            var bookId = numericId(book.id);
            var categoryId = numericId(book.catId);
            if (!bookId || !categoryId) {
                continue;
            }
            var row = document.createElement('tr');
            var rank = document.createElement('td');
            rank.className = 'rank';
            var rankText = document.createElement('i');
            rankText.textContent = String(i + 1);
            rank.appendChild(rankText);
            row.appendChild(rank);

            var category = document.createElement('td');
            category.className = 'style';
            category.appendChild(createLink('/book/bookclass.html?c=' + encodeURIComponent(categoryId), '[' + String(book.catName == null ? '' : book.catName) + ']'));
            row.appendChild(category);

            var name = document.createElement('td');
            name.className = 'name';
            name.appendChild(createLink('/book/' + encodeURIComponent(bookId) + '.html', book.bookName));
            row.appendChild(name);

            var chapter = document.createElement('td');
            chapter.className = 'chapter';
            chapter.appendChild(createLink('/book/' + encodeURIComponent(bookId) + '.html', book.lastIndexName));
            row.appendChild(chapter);

            var author = document.createElement('td');
            author.className = 'author';
            author.appendChild(createLink('/book/bookclass.html?k=' + encodeURIComponent(String(book.authorName == null ? '' : book.authorName)), book.authorName));
            row.appendChild(author);

            var words = document.createElement('td');
            words.className = 'word';
            var count = Number(book.wordCount);
            words.textContent = (Number.isFinite(count) ? (count / 10000).toFixed(2) : '0.00') + ' ' + messages.tenThousand;
            row.appendChild(words);
            fragment.appendChild(row);
        }
        body.replaceChildren(fragment);
    }

    function search(pageNumber, limit) {
        var direction = currentDirection();
        var categorySelector = direction === '1' ? '.so_girl' : '.so_boy';
        var data = {
            curr: pageNumber,
            limit: limit,
            keyword: $('#searchKey').val()
        };
        var values = {
            workDirection: direction,
            catId: activeValue(categorySelector, 'filter-value'),
            bookStatus: activeValue('.so_progress', 'filter-value'),
            wordCountMin: activeValue('.so_words', 'filter-value-min'),
            wordCountMax: activeValue('.so_words', 'filter-value-max'),
            updatePeriod: activeValue('.so_update', 'filter-value'),
            sort: activeValue('.so_sort', 'filter-value')
        };
        Object.keys(values).forEach(function (key) {
            if (values[key] != null && values[key] !== '') {
                data[key] = values[key];
            }
        });

        $.ajax({
            type: 'get',
            url: '/book/searchByPage',
            data: data,
            dataType: 'json',
            success: function (response) {
                if (response.code !== 200) {
                    window.novelAlertText(response.msg);
                    return;
                }
                renderBooks(response);
                window.layui.use('laypage', function () {
                    window.layui.laypage.render({
                        elem: 'books',
                        count: response.data.total,
                        curr: response.data.pageNum,
                        limit: response.data.pageSize,
                        jump: function (obj, first) {
                            if (!first) {
                                search(obj.curr, obj.limit);
                            }
                        }
                    });
                });
            },
            error: function () {
                window.layer.alert(messages.networkError);
            }
        });
    }

    function appendCategory(container, category, selectedId) {
        var id = numericId(category.id);
        if (!id) {
            return;
        }
        var link = createLink('#filter-category-' + id, category.name);
        link.setAttribute('data-book-filter', 'category');
        link.setAttribute('filter-value', id);
        if (id === selectedId) {
            link.className = 'on';
        }
        container.appendChild(link);
    }

    function renderCategories(categories, selectedId) {
        var boy = document.getElementById('boyCategoryList');
        var girl = document.getElementById('girlCategoryList');
        boy.replaceChildren();
        girl.replaceChildren();
        [boy, girl].forEach(function (container) {
            var unlimited = createLink('#filter-category-all', messages.unlimited);
            unlimited.setAttribute('data-book-filter', 'category');
            if (!selectedId) {
                unlimited.className = 'on';
            }
            container.appendChild(unlimited);
        });

        var selectedDirection = '0';
        for (var i = 0; i < categories.length; i++) {
            var category = categories[i] || {};
            if (String(category.id) === selectedId) {
                selectedDirection = String(category.workDirection) === '1' ? '1' : '0';
            }
            appendCategory(String(category.workDirection) === '1' ? girl : boy, category, selectedId);
        }
        var directionLinks = root.querySelectorAll('.so_pd > a');
        for (var j = 0; j < directionLinks.length; j++) {
            directionLinks[j].classList.toggle('on', directionLinks[j].getAttribute('filter-value') === selectedDirection);
        }
        girl.style.display = selectedDirection === '1' ? '' : 'none';
        boy.style.display = selectedDirection === '1' ? 'none' : '';
    }

    function loadCategories(selectedId) {
        $.ajax({
            type: 'get',
            url: '/book/listBookCategory',
            data: {},
            dataType: 'json',
            success: function (response) {
                if (response.code !== 200) {
                    window.novelAlertText(response.msg);
                    return;
                }
                renderCategories(Array.isArray(response.data) ? response.data : [], selectedId);
                search(1, 20);
            },
            error: function () {
                window.layer.alert(messages.networkError);
            }
        });
    }

    root.addEventListener('click', function (event) {
        var link = event.target.closest('[data-book-filter]');
        if (!link || !root.contains(link)) {
            return;
        }
        event.preventDefault();
        var group = link.closest('li, span.so_girl, span.so_boy');
        if (group) {
            var links = group.querySelectorAll('a[data-book-filter]');
            for (var i = 0; i < links.length; i++) {
                links[i].classList.toggle('on', links[i] === link);
            }
        }
        if (link.getAttribute('data-book-filter') === 'direction') {
            var female = link.getAttribute('filter-value') === '1';
            document.getElementById('girlCategoryList').style.display = female ? '' : 'none';
            document.getElementById('boyCategoryList').style.display = female ? 'none' : '';
        }
        search(1, 20);
    });

    loadCategories(numericId(window.getSearchString('c')));
})(window, document, window.jQuery);
