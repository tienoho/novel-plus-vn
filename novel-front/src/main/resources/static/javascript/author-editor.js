(function (window, $) {
    'use strict';

    function resolved(value) {
        var deferred = $.Deferred();
        deferred.resolve(value);
        return deferred.promise();
    }

    function createClientKey() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 'ED_' + window.crypto.randomUUID().replace(/-/g, '');
        }
        return 'ED_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 18);
    }

    function dateToIso(selector) {
        if (!selector) return null;
        var value = $(selector).val();
        if (!value) return null;
        var parsed = new Date(value);
        return isNaN(parsed.getTime()) ? null : parsed.toISOString();
    }

    function toLocalDateTime(value) {
        if (!value) return '';
        var date = new Date(value);
        if (isNaN(date.getTime())) return '';
        function pad(number) { return String(number).padStart(2, '0'); }
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
            + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }

    function parseStoredState(storageKey) {
        try {
            return JSON.parse(window.localStorage.getItem(storageKey) || 'null');
        } catch (error) {
            window.localStorage.removeItem(storageKey);
            return null;
        }
    }

    function Editor(config) {
        this.config = config;
        this.storageKey = 'novel:author-editor:' + config.bookId + ':' + (config.indexId || 'new');
        this.state = parseStoredState(this.storageKey) || {
            draftId: null,
            version: null,
            clientKey: createClientKey(),
            status: 'DRAFT'
        };
        this.saveChain = resolved();
        this.lastFingerprint = null;
        this.timer = null;
    }

    Editor.prototype.message = function (key, fallback) {
        return (this.config.messages && this.config.messages[key]) || fallback;
    };

    Editor.prototype.setStatus = function (text, isError) {
        var target = $(this.config.statusSelector);
        target.text(text || '');
        target.css('color', isError ? '#c0392b' : '#6b7280');
    };

    Editor.prototype.persistState = function () {
        window.localStorage.setItem(this.storageKey, JSON.stringify(this.state));
    };

    Editor.prototype.clearState = function () {
        window.localStorage.removeItem(this.storageKey);
        this.state = {
            draftId: null,
            version: null,
            clientKey: createClientKey(),
            status: 'DRAFT'
        };
        this.lastFingerprint = null;
        this.updateControls();
    };

    Editor.prototype.updateControls = function () {
        var scheduled = this.state.status === 'SCHEDULED';
        $(this.config.titleSelector + ',' + this.config.contentSelector).prop('readonly', scheduled);
        $(this.config.vipSelector).prop('disabled', scheduled);
        $(this.config.saveSelector + ',' + this.config.scheduleSelector + ','
            + this.config.scheduleButtonSelector).prop('disabled', scheduled);
        $(this.config.cancelScheduleSelector).prop('disabled', !scheduled);
        this.updateCommercialControls(scheduled);
    };

    Editor.prototype.commercialSelectors = function () {
        return [this.config.customPriceSelector, this.config.unlockAtSelector,
            this.config.freeFromSelector, this.config.freeUntilSelector].filter(Boolean).join(',');
    };

    Editor.prototype.updateCommercialControls = function (scheduled) {
        var paid = Number($(this.config.vipSelector + ':checked').val() || 0) === 1;
        if (this.config.commercialPanelSelector) {
            $(this.config.commercialPanelSelector).toggle(paid);
        }
        var selectors = this.commercialSelectors();
        if (selectors) {
            $(selectors).prop('disabled', scheduled || !paid);
            if (!paid && !scheduled) $(selectors).val('');
        }
    };

    Editor.prototype.updatePricePreview = function (price) {
        if (!this.config.pricePreviewSelector) return;
        var text = price === null || typeof price === 'undefined'
            ? this.message('automaticPrice', 'Tự động theo số chữ')
            : String(price) + ' Xu';
        $(this.config.pricePreviewSelector).text(text);
    };

    Editor.prototype.payload = function () {
        var customPriceValue = this.config.customPriceSelector
            ? $(this.config.customPriceSelector).val() : '';
        return {
            draftId: this.state.draftId,
            clientKey: this.state.clientKey,
            bookId: Number(this.config.bookId),
            indexId: this.config.indexId ? Number(this.config.indexId) : null,
            indexName: $(this.config.titleSelector).val() || '',
            content: $(this.config.contentSelector).val() || '',
            isVip: Number($(this.config.vipSelector + ':checked').val() || 0),
            customPrice: customPriceValue === '' ? null : Number(customPriceValue),
            unlockAt: dateToIso(this.config.unlockAtSelector),
            freeFrom: dateToIso(this.config.freeFromSelector),
            freeUntil: dateToIso(this.config.freeUntilSelector),
            expectedVersion: this.state.version
        };
    };

    Editor.prototype.fingerprint = function (payload) {
        return JSON.stringify([
            payload.indexName,
            payload.content,
            payload.isVip,
            payload.customPrice,
            payload.unlockAt,
            payload.freeFrom,
            payload.freeUntil,
            payload.bookId,
            payload.indexId
        ]);
    };

    Editor.prototype.performSave = function (silent, force) {
        var self = this;
        if (self.state.status === 'SCHEDULED') {
            if (force) {
                var locked = self.message('scheduledReadOnly',
                    'Bản nháp đã lên lịch; hãy hủy lịch trước khi chỉnh sửa');
                self.setStatus(locked, true);
                return $.Deferred().reject(locked).promise();
            }
            return resolved(self.state);
        }
        var payload = self.payload();
        var fingerprint = self.fingerprint(payload);
        if (!force && fingerprint === self.lastFingerprint) {
            return resolved(self.state);
        }
        self.setStatus(self.message('saving', 'Đang tự lưu...'), false);
        return $.ajax({
            type: 'POST',
            url: '/author/drafts/autosave',
            contentType: 'application/json; charset=UTF-8',
            dataType: 'json',
            data: JSON.stringify(payload)
        }).then(function (response) {
            if (!response || response.code !== 200 || !response.data) {
                return $.Deferred().reject(response && response.msg ? response.msg : 'Không thể lưu bản nháp').promise();
            }
            self.state.draftId = response.data.id;
            self.state.version = response.data.version;
            self.state.status = response.data.status;
            self.state.clientKey = response.data.clientKey;
            self.persistState();
            self.updateControls();
            self.lastFingerprint = fingerprint;
            self.updatePricePreview(response.data.bookPrice);
            self.setStatus(self.message('saved', 'Đã tự lưu'), false);
            if (!silent && window.layer) {
                window.layer.msg(self.message('saved', 'Đã tự lưu'));
            }
            return response.data;
        }, function (xhr) {
            var message = xhr.responseJSON && xhr.responseJSON.msg
                ? xhr.responseJSON.msg : self.message('saveFailed', 'Không thể lưu bản nháp');
            self.setStatus(message, true);
            return $.Deferred().reject(message).promise();
        });
    };

    Editor.prototype.save = function (silent, force) {
        var self = this;
        var deferred = $.Deferred();
        self.saveChain.always(function () {
            self.performSave(silent, force).done(deferred.resolve).fail(deferred.reject);
        });
        self.saveChain = deferred.promise();
        return deferred.promise();
    };

    Editor.prototype.restore = function () {
        var self = this;
        if (!self.state.draftId) {
            self.lastFingerprint = self.fingerprint(self.payload());
            return resolved(null);
        }
        return $.getJSON('/author/drafts/' + self.state.draftId).then(function (response) {
            if (!response || response.code !== 200 || !response.data
                || response.data.status === 'PUBLISHED' || response.data.status === 'CANCELLED') {
                self.clearState();
                return null;
            }
            var draft = response.data;
            self.state.version = draft.version;
            self.state.status = draft.status;
            self.state.clientKey = draft.clientKey;
            $(self.config.titleSelector).val(draft.indexName || '');
            $(self.config.contentSelector).val(draft.content || '');
            $(self.config.vipSelector + '[value="' + Number(draft.isVip || 0) + '"]').prop('checked', true);
            if (self.config.customPriceSelector) $(self.config.customPriceSelector).val(draft.customPrice || '');
            if (self.config.unlockAtSelector) $(self.config.unlockAtSelector).val(toLocalDateTime(draft.unlockAt));
            if (self.config.freeFromSelector) $(self.config.freeFromSelector).val(toLocalDateTime(draft.freeFrom));
            if (self.config.freeUntilSelector) $(self.config.freeUntilSelector).val(toLocalDateTime(draft.freeUntil));
            self.lastFingerprint = self.fingerprint(self.payload());
            self.persistState();
            self.updateControls();
            self.updatePricePreview(draft.bookPrice);
            self.setStatus(draft.status === 'SCHEDULED'
                ? self.message('scheduled', 'Đã lên lịch xuất bản')
                : self.message('restored', 'Đã khôi phục bản nháp'), false);
            return draft;
        }, function () {
            self.setStatus(self.message('restoreFailed',
                'Không thể tải bản nháp; mã bản nháp vẫn được giữ để thử lại'), true);
            return null;
        });
    };

    Editor.prototype.publish = function () {
        var self = this;
        var beforePublish = self.state.status === 'SCHEDULED'
            ? resolved(self.state) : self.save(true, true);
        return beforePublish.then(function () {
            return $.ajax({
                type: 'POST',
                url: '/author/drafts/' + self.state.draftId + '/publish',
                data: {expectedVersion: self.state.version},
                dataType: 'json'
            });
        }).then(function (response) {
            if (!response || response.code !== 200) {
                return $.Deferred().reject(response && response.msg ? response.msg : 'Không thể xuất bản').promise();
            }
            self.clearState();
            window.location.href = self.config.redirectUrl;
            return response.data;
        }, function (error) {
            var message = typeof error === 'string' ? error
                : (error.responseJSON && error.responseJSON.msg ? error.responseJSON.msg : self.message('publishFailed', 'Không thể xuất bản chương'));
            self.setStatus(message, true);
            if (window.layer) window.layer.alert(message);
            return $.Deferred().reject(message).promise();
        });
    };

    Editor.prototype.schedule = function () {
        var self = this;
        var value = $(self.config.scheduleSelector).val();
        var scheduledAt = value ? new Date(value) : null;
        if (!scheduledAt || isNaN(scheduledAt.getTime())) {
            var invalid = self.message('scheduleRequired', 'Vui lòng chọn thời gian xuất bản');
            self.setStatus(invalid, true);
            return $.Deferred().reject(invalid).promise();
        }
        return self.save(true, true).then(function () {
            return $.ajax({
                type: 'POST',
                url: '/author/drafts/' + self.state.draftId + '/schedule',
                contentType: 'application/json; charset=UTF-8',
                dataType: 'json',
                data: JSON.stringify({expectedVersion: self.state.version, scheduledAt: scheduledAt.toISOString()})
            });
        }).then(function (response) {
            if (!response || response.code !== 200 || !response.data) {
                return $.Deferred().reject(response && response.msg ? response.msg : 'Không thể lên lịch').promise();
            }
            self.state.version = response.data.version;
            self.state.status = response.data.status;
            self.persistState();
            self.updateControls();
            self.setStatus(self.message('scheduled', 'Đã lên lịch xuất bản'), false);
            if (window.layer) window.layer.msg(self.message('scheduled', 'Đã lên lịch xuất bản'));
            return response.data;
        }, function (error) {
            var message = typeof error === 'string' ? error
                : (error.responseJSON && error.responseJSON.msg ? error.responseJSON.msg : self.message('scheduleFailed', 'Không thể lên lịch xuất bản'));
            self.setStatus(message, true);
            if (window.layer) window.layer.alert(message);
            return $.Deferred().reject(message).promise();
        });
    };

    Editor.prototype.cancelSchedule = function () {
        var self = this;
        if (!self.state.draftId || self.state.status !== 'SCHEDULED') return resolved(null);
        return $.ajax({
            type: 'POST',
            url: '/author/drafts/' + self.state.draftId + '/cancel-schedule',
            data: {expectedVersion: self.state.version},
            dataType: 'json'
        }).then(function (response) {
            if (!response || response.code !== 200 || !response.data) {
                return $.Deferred().reject(response && response.msg ? response.msg : 'Không thể hủy lịch').promise();
            }
            self.state.version = response.data.version;
            self.state.status = response.data.status;
            self.persistState();
            self.updateControls();
            self.setStatus(self.message('scheduleCancelled', 'Đã hủy lịch xuất bản'), false);
            return response.data;
        });
    };

    Editor.prototype.bind = function () {
        var self = this;
        var debounce;
        var commercialSelectors = self.commercialSelectors();
        var editorSelectors = self.config.titleSelector + ',' + self.config.contentSelector + ','
            + self.config.vipSelector + (commercialSelectors ? ',' + commercialSelectors : '');
        $(editorSelectors)
            .on('input change', function () {
                if ($(this).is(self.config.vipSelector)) self.updateCommercialControls(false);
                if (self.state.status !== 'DRAFT') return;
                window.clearTimeout(debounce);
                debounce = window.setTimeout(function () { self.save(true, false); }, 2000);
            });
        $(self.config.saveSelector).on('click', function () { self.save(false, true); });
        $(self.config.publishSelector).on('click', function () { self.publish(); });
        $(self.config.scheduleButtonSelector).on('click', function () { self.schedule(); });
        $(self.config.cancelScheduleSelector).on('click', function () { self.cancelSchedule(); });
        self.timer = window.setInterval(function () { self.save(true, false); }, 15000);
    };

    Editor.prototype.init = function () {
        var self = this;
        self.updateControls();
        self.bind();
        return self.restore();
    };

    window.NovelAuthorEditor = {
        formatLocalDateTime: toLocalDateTime,
        create: function (config) {
            return new Editor(config);
        }
    };
})(window, window.jQuery);
