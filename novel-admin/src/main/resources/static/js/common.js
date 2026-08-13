var HtmlUtil = {
    /*1.Mã hóa HTML bằng bộ chuyển đổi của trình duyệt*/
    htmlEncode:function (html){
        //1.Tạo động phần tử chứa, ví dụ DIV
        var temp = document.createElement ("div");
        //2.Gán chuỗi cần chuyển đổi vào innerText hoặc textContent
        (temp.textContent != undefined ) ? (temp.textContent = html) : (temp.innerText = html);
        //3.Trả về innerHTML để nhận chuỗi đã mã hóa HTML
        var output = temp.innerHTML;
        temp = null;
        return output;
    },
    /*2.Giải mã HTML bằng bộ chuyển đổi của trình duyệt*/
    htmlDecode:function (text){
        if (text == null) return "";
        var named = {amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " "};
        return String(text).replace(/&#(?:x([0-9a-f]+)|(\d+));|&(amp|lt|gt|quot|apos|nbsp);/gi,
            function (entity, hex, decimal, name) {
                if (name) return named[name.toLowerCase()];
                var codePoint = parseInt(hex || decimal, hex ? 16 : 10);
                if (!Number.isInteger(codePoint) || codePoint < 0 || codePoint > 0x10FFFF
                    || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
                    return entity;
                }
                return String.fromCodePoint(codePoint);
            });
    },
    /*3.Mã hóa HTML bằng biểu thức chính quy*/
    htmlEncodeByRegExp:function (str){
        var temp = "";
        if(str.length == 0) return "";
        temp = str.replace(/&/g,"&amp;");
        temp = temp.replace(/</g,"&lt;");
        temp = temp.replace(/>/g,"&gt;");
        temp = temp.replace(/\s/g,"&nbsp;");
        temp = temp.replace(/\'/g,"&#39;");
        temp = temp.replace(/\"/g,"&quot;");
        return temp;
    },
    /*4.Giải mã HTML bằng biểu thức chính quy*/
    htmlDecodeByRegExp:function (str){
        var temp = "";
        if(str.length == 0) return "";
        temp = str.replace(/&amp;/g,"&");
        temp = temp.replace(/&lt;/g,"<");
        temp = temp.replace(/&gt;/g,">");
        temp = temp.replace(/&nbsp;/g," ");
        temp = temp.replace(/&#39;/g,"\'");
        temp = temp.replace(/&quot;/g,"\"");
        return temp;
    },
    /*5.Mã hóa HTML bằng biểu thức chính quy（Cách viết khác）*/
    html2Escape:function(sHtml) {
        if(sHtml == undefined || sHtml == null || sHtml.length == 0) return "";
        return sHtml.replace(/[<>&"]/g,function(c){return {'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[c];});
    },
    /*6.Giải mã HTML bằng biểu thức chính quy（Cách viết khác）*/
    escape2Html:function (str) {
        if(str == undefined || str == null || str.length == 0) return "";
        var arrEntities={'lt':'<','gt':'>','nbsp':' ','amp':'&','quot':'"'};
        return str.replace(/&(lt|gt|nbsp|amp|quot);/ig,function(all,t){return arrEntities[t];});
    }
};

function getFormJson(formID) {
    var fields = $('#'+formID).serializeArray();
    var obj = {}; //Khai báo đối tượng
    $.each(fields, function (index, field) {
        obj[field.name] = field.value; //Đưa tên thuộc tính và giá trị vào đối tượng
    })
    return obj;
}


//Thông báo tải AJAX toàn hệ thống
(function ($) {
    $(document).ajaxStart(function () {
        var index = layer.load(1, {
            shade: [0.1, '#fff'] //0.1Nền trắng trong suốt
        });
    });
    $(document).ajaxStop(function () {
        layer.closeAll('loading');
    });
    //Phiên đăng nhập hết hạn; Shiro trả trang đăng nhập
    $.ajaxSetup({
        complete: function (xhr, status,dataType) {
            if('text/html;charset=UTF-8'==xhr.getResponseHeader('Content-Type')){
                top.location.href = '/login';
            }
        }
    });
})(jQuery);

// Chỉ cho phép các thao tác giao diện đã biết thay cho thuộc tính onclick nội tuyến.
(function () {
    function callGlobal(name, args) {
        var handler = window[name];
        if (typeof handler === 'function') {
            handler.apply(window, args || []);
        }
    }

    var actions = {
        'add': function (element) {
            var argument = element.getAttribute('data-admin-arg');
            callGlobal('add', argument === null ? [] : [argument]);
        },
        'batch-remove': function () {
            callGlobal('batchRemove');
        },
        'reload': function () {
            callGlobal('reLoad');
        },
        'edit': function () {
            callGlobal('edit');
        },
        'batch-code': function () {
            callGlobal('batchCode');
        },
        'batch-download': function () {
            callGlobal('batchDownload');
        },
        'save': function () {
            callGlobal('save');
        },
        'load-user': function () {
            callGlobal('loadUser');
        },
        'open-department': function () {
            callGlobal('openDept');
        }
    };

    var rowActions = {
        'edit': function (args) { callGlobal('edit', args); },
        'remove': function (args) { callGlobal('remove', args); },
        'add-dictionary': function (args) { callGlobal('addD', args); },
        'column-edit': function (args) { callGlobal('columnEdit', args); },
        'reset-password': function (args) { callGlobal('resetPwd', args); },
        'download-book': function (args) { callGlobal('downloadBook', args); },
        'detail': function (args) { callGlobal('detail', args); },
        'add': function (args) { callGlobal('add', args); },
        'remove-one': function (args) { callGlobal('removeone', args); },
        'force-logout': function (args) { callGlobal('forceLogout', args); }
    };

    function escapeAttribute(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    window.adminRowAction = function (options) {
        options = options || {};
        var variants = {primary: 'btn-primary', warning: 'btn-warning', success: 'btn-success'};
        var icons = {detail: 'fa-file', edit: 'fa-edit', remove: 'fa-remove', add: 'fa-plus', download: 'fa-cloud-download', generate: 'fa-bug', key: 'fa-key', none: ''};
        var action = options.action;
        var args = Array.isArray(options.args) ? options.args : [];
        var visibility = String(options.visibility || '').trim();
        if (!Object.prototype.hasOwnProperty.call(rowActions, action)
            || !Object.prototype.hasOwnProperty.call(variants, options.variant)
            || !Object.prototype.hasOwnProperty.call(icons, options.icon)
            || args.length > 3
            || (visibility && !/^[A-Za-z0-9_-]+(?:\s+[A-Za-z0-9_-]+)*$/.test(visibility))) {
            return '';
        }
        var classes = 'btn btn-sm ' + variants[options.variant] + (visibility ? ' ' + visibility : '');
        var title = escapeAttribute(options.title);
        var html = '<a class="' + classes + '" href="#" title="' + title
            + '" aria-label="' + title + '" data-admin-row-action="' + action + '"';
        for (var i = 0; i < args.length; i++) {
            try {
                html += ' data-admin-arg-' + i + '="' + encodeURIComponent(String(args[i] == null ? '' : args[i])) + '"';
            } catch (error) {
                return '';
            }
        }
        var label = escapeAttribute(options.label);
        var content = icons[options.icon]
            ? '<i class="fa ' + icons[options.icon] + '"></i>' + label
            : '<i>' + label + '</i>';
        return html + '>' + content + '</a> ';
    };

    function findActionElement(node, attribute) {
        while (node && node !== document) {
            if (node.nodeType === 1 && node.hasAttribute(attribute)) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    document.addEventListener('click', function (event) {
        var element = findActionElement(event.target, 'data-admin-action');
        if (!element) {
            return;
        }
        var action = element.getAttribute('data-admin-action');
        if (Object.prototype.hasOwnProperty.call(actions, action)) {
            actions[action](element);
        }
    });

    document.addEventListener('click', function (event) {
        var element = findActionElement(event.target, 'data-admin-row-action');
        if (!element) {
            return;
        }
        var action = element.getAttribute('data-admin-row-action');
        if (!Object.prototype.hasOwnProperty.call(rowActions, action)) {
            return;
        }
        var args = [];
        for (var i = 0; i < 3; i++) {
            var value = element.getAttribute('data-admin-arg-' + i);
            if (value === null) {
                break;
            }
            try {
                args.push(decodeURIComponent(value));
            } catch (error) {
                return;
            }
        }
        event.preventDefault();
        rowActions[action](args);
    });

    function initializeLaydateFields() {
        if (typeof window.laydate !== 'function') {
            return;
        }
        var fields = document.querySelectorAll('[data-admin-laydate="datetime"]');
        for (var i = 0; i < fields.length; i++) {
            var id = fields[i].id;
            if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(id)) {
                continue;
            }
            window.laydate({
                elem: '#' + id,
                istime: true,
                format: 'YYYY-MM-DD hh:mm:ss'
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeLaydateFields);
    } else {
        initializeLaydateFields();
    }
})();
