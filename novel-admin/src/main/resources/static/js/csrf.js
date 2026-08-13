(function (window, document) {
    "use strict";

    var unsafeMethods = {POST: true, PUT: true, PATCH: true, DELETE: true};

    function readToken() {
        var prefix = "XSRF-TOKEN=";
        var cookies = document.cookie ? document.cookie.split(";") : [];
        for (var index = 0; index < cookies.length; index++) {
            var cookie = cookies[index].trim();
            if (cookie.indexOf(prefix) === 0) {
                return decodeURIComponent(cookie.substring(prefix.length));
            }
        }
        return null;
    }

    function isSameOrigin(url) {
        try {
            return new URL(url || window.location.href, window.location.href).origin
                === window.location.origin;
        } catch (error) {
            return false;
        }
    }

    var originalOpen = window.XMLHttpRequest.prototype.open;
    var originalSend = window.XMLHttpRequest.prototype.send;
    window.XMLHttpRequest.prototype.open = function (method, url) {
        this.__novelCsrfProtected = unsafeMethods[String(method).toUpperCase()] === true
            && isSameOrigin(url);
        return originalOpen.apply(this, arguments);
    };
    window.XMLHttpRequest.prototype.send = function () {
        var token = readToken();
        if (this.__novelCsrfProtected && token) {
            this.setRequestHeader("X-XSRF-TOKEN", token);
        }
        return originalSend.apply(this, arguments);
    };

    if (window.fetch) {
        var originalFetch = window.fetch;
        window.fetch = function (input, init) {
            var options = init ? Object.assign({}, init) : {};
            var method = String(options.method || "GET").toUpperCase();
            var url = typeof input === "string" ? input : input.url;
            var token = readToken();
            if (unsafeMethods[method] === true && isSameOrigin(url) && token) {
                var headers = new Headers(options.headers ||
                    (typeof input === "string" ? undefined : input.headers));
                headers.set("X-XSRF-TOKEN", token);
                options.headers = headers;
            }
            return originalFetch.call(window, input, options);
        };
    }
})(window, document);
