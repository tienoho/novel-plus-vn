(function () {
    'use strict';

    var ACTION_SELECTOR = 'button, a[href], input[type="submit"], input[type="button"], .layui-btn, [role="button"], [data-novel-action]';
    var CONTROL_SELECTOR = 'input:not([type="hidden"]), textarea, select';
    var feedbackSelector = '.layui-layer-msg, .layui-m-layer-msg, .layui-m-layercont';
    var reducedMotionQuery = window.matchMedia
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : null;
    var scheduledRoots = [];
    var schedulePending = false;

    function textMessage(key, fallback) {
        var messages = window.NovelI18n || {};
        return messages[key] || fallback;
    }

    function prefersReducedMotion() {
        return !!(reducedMotionQuery && reducedMotionQuery.matches);
    }

    function closestAction(target) {
        if (!target || target.nodeType !== 1 || !target.closest) return null;
        return target.closest(ACTION_SELECTOR);
    }

    function enhanceFeedback(node) {
        if (!node || node.nodeType !== 1) return;
        var feedback = node.matches(feedbackSelector) ? node : node.querySelector(feedbackSelector);
        if (!feedback) return;
        var container = feedback.closest('.layui-layer, .layui-m-layerchild') || feedback;
        container.classList.add('np-feedback');
        container.setAttribute('role', 'status');
        container.setAttribute('aria-live', 'polite');
        container.setAttribute('aria-atomic', 'true');
    }

    function enhance(root) {
        if (!root || !root.querySelectorAll) return;
        var actions = root.matches && root.matches(ACTION_SELECTOR)
            ? [root]
            : root.querySelectorAll(ACTION_SELECTOR);
        var controls = root.matches && root.matches(CONTROL_SELECTOR)
            ? [root]
            : root.querySelectorAll(CONTROL_SELECTOR);
        var index;
        for (index = 0; index < actions.length; index++) {
            actions[index].classList.add('np-interactive');
        }
        for (index = 0; index < controls.length; index++) {
            controls[index].classList.add('np-control');
        }
        enhanceFeedback(root);
    }

    function scheduleEnhance(root) {
        scheduledRoots.push(root);
        if (schedulePending) return;
        schedulePending = true;
        window.requestAnimationFrame(function () {
            var roots = scheduledRoots;
            scheduledRoots = [];
            schedulePending = false;
            for (var index = 0; index < roots.length; index++) enhance(roots[index]);
        });
    }

    function ensureLiveRegion() {
        var region = document.getElementById('novelLiveRegion');
        if (region || !document.body) return region;
        region = document.createElement('div');
        region.id = 'novelLiveRegion';
        region.className = 'np-live-region';
        region.setAttribute('role', 'status');
        region.setAttribute('aria-live', 'polite');
        region.setAttribute('aria-atomic', 'true');
        document.body.appendChild(region);
        return region;
    }

    function announce(message) {
        var region = ensureLiveRegion();
        if (!region) return;
        region.textContent = '';
        window.setTimeout(function () {
            region.textContent = message == null ? '' : String(message);
        }, 30);
    }

    function spinnerFor(element) {
        if (!element || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName)) return null;
        var spinner = element.querySelector('[data-novel-spinner]');
        if (spinner) return spinner;
        spinner = document.createElement('span');
        spinner.className = 'np-spinner';
        spinner.setAttribute('data-novel-spinner', '');
        spinner.setAttribute('aria-hidden', 'true');
        element.appendChild(spinner);
        return spinner;
    }

    function setBusy(element, active) {
        if (!element) return;
        if (active) {
            if (!element.hasAttribute('data-novel-was-disabled')) {
                element.setAttribute('data-novel-was-disabled', element.disabled ? 'true' : 'false');
            }
            element.classList.add('np-busy');
            element.setAttribute('aria-busy', 'true');
            if ('disabled' in element) element.disabled = true;
            if (element.tagName === 'A' || element.getAttribute('role') === 'button') {
                element.setAttribute('aria-disabled', 'true');
            }
            spinnerFor(element);
            return;
        }

        element.classList.remove('np-busy');
        element.removeAttribute('aria-busy');
        element.removeAttribute('aria-disabled');
        var spinner = element.querySelector && element.querySelector('[data-novel-spinner]');
        if (spinner) spinner.remove();
        if ('disabled' in element && element.getAttribute('data-novel-was-disabled') === 'false') {
            element.disabled = false;
        }
        element.removeAttribute('data-novel-was-disabled');
    }

    function withBusy(element, task) {
        setBusy(element, true);
        announce(textMessage('processing', 'Đang xử lý…'));
        var promise;
        try {
            promise = typeof task === 'function' ? task() : task;
        } catch (error) {
            setBusy(element, false);
            throw error;
        }
        return Promise.resolve(promise).then(function (value) {
            setBusy(element, false);
            return value;
        }, function (error) {
            setBusy(element, false);
            throw error;
        });
    }

    function escapeHtml(value) {
        var container = document.createElement('div');
        container.textContent = value == null ? '' : String(value);
        return container.innerHTML;
    }

    function fallbackToast(message, tone, duration) {
        var region = document.getElementById('novelToastRegion');
        if (!region) {
            region = document.createElement('div');
            region.id = 'novelToastRegion';
            region.className = 'np-toast-region';
            region.setAttribute('aria-live', 'polite');
            region.setAttribute('aria-relevant', 'additions text');
            document.body.appendChild(region);
        }

        var toast = document.createElement('div');
        toast.className = 'np-toast';
        toast.dataset.tone = tone || 'info';
        toast.setAttribute('role', tone === 'error' ? 'alert' : 'status');
        var content = document.createElement('span');
        content.className = 'np-toast__message';
        content.textContent = message == null ? '' : String(message);
        var close = document.createElement('button');
        close.type = 'button';
        close.className = 'np-toast__close np-interactive';
        close.setAttribute('aria-label', textMessage('close', 'Đóng'));
        close.textContent = '×';
        close.addEventListener('click', function () {
            toast.remove();
        });
        toast.appendChild(content);
        toast.appendChild(close);
        region.appendChild(toast);
        window.setTimeout(function () {
            toast.remove();
        }, duration || 3500);
    }

    function toast(message, tone, options) {
        options = options || {};
        var layerApi = window.layer || (window.layui && window.layui.layer);
        if (layerApi && typeof layerApi.msg === 'function') {
            var icon = tone === 'success' ? 1 : tone === 'error' ? 2 : undefined;
            var index = layerApi.msg(escapeHtml(message), {
                icon: icon,
                time: options.duration || 3500
            });
            window.requestAnimationFrame(function () {
                enhanceFeedback(document.getElementById('layui-layer' + index) || document.body);
            });
            return index;
        }
        fallbackToast(message, tone, options.duration);
        return null;
    }

    function clearBusyOnRestore() {
        var busy = document.querySelectorAll('.np-busy');
        for (var index = 0; index < busy.length; index++) setBusy(busy[index], false);
    }

    window.NovelUX = Object.freeze({
        announce: announce,
        busy: setBusy,
        enhance: enhance,
        prefersReducedMotion: prefersReducedMotion,
        toast: toast,
        withBusy: withBusy
    });

    document.addEventListener('click', function (event) {
        var action = closestAction(event.target);
        if (!action) return;
        if (action.getAttribute('aria-disabled') === 'true' || action.disabled) {
            event.preventDefault();
            return;
        }
        if (prefersReducedMotion()) return;
        action.classList.add('np-tapped');
        window.setTimeout(function () {
            action.classList.remove('np-tapped');
        }, 160);
    }, true);

    document.addEventListener('submit', function (event) {
        var form = event.target;
        window.setTimeout(function () {
            if (event.defaultPrevented || !form || (form.method || 'get').toLowerCase() === 'get') return;
            var submitter = event.submitter || document.activeElement;
            if (!submitter || !form.contains(submitter)) return;
            setBusy(submitter, true);
            announce(textMessage('processing', 'Đang xử lý…'));
        }, 0);
    });

    document.addEventListener('DOMContentLoaded', function () {
        enhance(document);
        ensureLiveRegion();
        if (!window.MutationObserver) return;
        var observer = new MutationObserver(function (records) {
            for (var recordIndex = 0; recordIndex < records.length; recordIndex++) {
                var nodes = records[recordIndex].addedNodes;
                for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
                    if (nodes[nodeIndex].nodeType === 1) scheduleEnhance(nodes[nodeIndex]);
                }
            }
        });
        observer.observe(document.body, {childList: true, subtree: true});
    });

    window.addEventListener('pageshow', clearBusyOnRestore);
})();
