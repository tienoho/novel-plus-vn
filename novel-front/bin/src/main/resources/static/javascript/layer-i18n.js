(function () {
    'use strict';

    var layerApi = window.layer || (window.layui && window.layui.layer);
    if (!layerApi || layerApi.__novelI18nWrapped) return;
    var messages = window.NovelI18n || {};

    function localizedOptions(options, includeCancel) {
        var localized = Object.assign({}, options || {});
        if (localized.title == null) localized.title = messages.notice || 'Thông báo';
        if (localized.btn == null) {
            localized.btn = includeCancel
                ? [messages.confirm || 'Đồng ý', messages.cancel || 'Hủy']
                : [messages.confirm || 'Đồng ý'];
        }
        return localized;
    }

    var originalAlert = layerApi.alert;
    layerApi.alert = function (content, options, yes) {
        if (typeof options === 'function') {
            yes = options;
            options = {};
        }
        return originalAlert.call(layerApi, content, localizedOptions(options, false), yes);
    };

    var originalConfirm = layerApi.confirm;
    layerApi.confirm = function (content, options, yes, cancel) {
        if (typeof options === 'function') {
            cancel = yes;
            yes = options;
            options = {};
        }
        return originalConfirm.call(layerApi, content, localizedOptions(options, true), yes, cancel);
    };

    layerApi.__novelI18nWrapped = true;
})();
