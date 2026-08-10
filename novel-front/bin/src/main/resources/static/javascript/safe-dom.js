(function (global) {
    "use strict";

    function text(value) {
        return value == null ? "" : String(value);
    }

    function positiveId(value) {
        var normalized = text(value).trim();
        return /^[1-9]\d{0,18}$/.test(normalized) ? normalized : null;
    }

    function safeUrl(value, fallback) {
        try {
            var parsed = new URL(text(value), global.location.origin);
            if (parsed.protocol === "http:" || parsed.protocol === "https:") {
                return parsed.href;
            }
        } catch (ignored) {
            // Trả về fallback an toàn khi URL không hợp lệ.
        }
        return fallback == null ? null : String(fallback);
    }

    function element(tagName, className, value) {
        if (!/^[a-z][a-z0-9-]*$/i.test(tagName)) {
            throw new TypeError("Tên phần tử DOM không hợp lệ");
        }
        var node = document.createElement(tagName);
        if (className) {
            node.className = className;
        }
        if (value != null) {
            node.textContent = text(value);
        }
        return node;
    }

    function link(href, className, value) {
        var safeHref = safeUrl(href, null);
        var node = element("a", className, value);
        if (safeHref) {
            node.href = safeHref;
        }
        return node;
    }

    function image(src, alt, fallback) {
        var node = element("img");
        node.src = safeUrl(src, fallback || "/images/default.gif");
        node.alt = text(alt);
        return node;
    }

    global.NovelSafeDom = Object.freeze({
        text: text,
        positiveId: positiveId,
        safeUrl: safeUrl,
        element: element,
        link: link,
        image: image
    });
})(window);
