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
        //1.Tạo động phần tử chứa, ví dụ DIV
        var temp = document.createElement("div");
        //2.Gán chuỗi cần chuyển đổi vào innerHTML
        temp.innerHTML = text;
        //3.Trả về innerText hoặc textContent để nhận chuỗi đã giải mã HTML.
        var output = temp.innerText || temp.textContent;
        temp = null;
        return output;
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
