var $C = function (objName) {
    if (typeof (document.getElementById(objName)) != "object")
    { return null; }
    else
    { return document.getElementById(objName); }
}
var YT = {
    BaseCommon: {
        gL: function (x) { var l = 0; while (x) { l += x.offsetLeft; x = x.offsetParent; } return l },
        gT: function (x) { var t = 0; while (x) { t += x.offsetTop; x = x.offsetParent; } return t }
    },
    BaseData: {
        WaitImg: "/images/loading.gif"
    },
    Fun: {
        GetWordLength: function (str) {
            str = str.replace(/(\n)+|(\r\n)+/g, "");
            str = str.replace(" ", "");
            str = str.replace("　", "");
            return str.length;
        },
        ConvertToMoney: function (btanch) {
            if (btanch != undefined) {
                return parseFloat(btanch) / 100;
            }
            else {
                return 0;
            }
        },
        LoadShow: function () {
            if ($C("LayerShowPic") == null) {
                var sp = document.createElement("div");
                sp.innerHTML = "<div id=\"LayerShowPic\" style=\"position:absolute;width:180px;height:70px;z-index:100;background-color: #fdfce9;border: 1px solid #666666;font-size:12px;\"><div align=\"center\" style=\"z-index:91;\"><br><img src=\"" + YT.BaseData.WaitImg + "\" align=\"absmiddle\" /> " + novelMessage('wait', 'Vui lòng chờ…') + "</div></div><iframe id=\"LayerCover\" style=\"position:absolute;width:100%;height:100%;z-index:10;left: 0px;top: 0px;background-color:#eeeeee;FILTER: alpha(opacity=1);opacity: 0.3 !important; \"></iframe>";
                document.body.appendChild(sp);
            }
            $C("LayerShowPic").style.display = '';
            $C("LayerCover").style.display = '';
            $C("LayerCover").style.height = String(document.documentElement.scrollHeight) + 'px';
            YT.Fun.ScreenCenter($C("LayerShowPic"), 266, 200);

        },
        LoadHide: function () {
            if ($C("LayerShowPic") != null) {
                $C("LayerShowPic").style.display = 'none';
                $C("LayerCover").style.display = 'none';
            }
        },
        ScreenCenter: function (obj, width, height) {
            if (obj.style.display == 'none') {
                obj.style.display = '';
            }
            var scrolltop = document.documentElement.scrollTop;
            if (width <= 0) {
                width = obj.offsetWidth;
            }
            if (height <= 0) {
                height = obj.offsetHeight;
            }
            if (scrolltop == null || scrolltop == 0) {
                scrolltop = document.body.scrollTop;
            }
            var offsetHT = document.body.clientHeight / 2 - height / 2;
            if (offsetHT <= 0) { offsetHT = 10; }
            var offsetWT = document.body.clientWidth / 2 - width / 2;
            if (offsetWT <= 0) { offsetWT = 10; }
            obj.style.top = String(scrolltop + offsetHT) + 'px';
            obj.style.left = String(offsetWT) + 'px';
        },
        NewPanel: function (url, title, width, height, needFits) {
            if (typeof (width) == 'undefind' || width == null) { width = 750; }
            if (typeof (height) == 'undefind' || height == null) { height = 550; }
            var fits = false;
            if (typeof (needFits) != "undefined" && needFits) {
                if (document.body.clientWidth < 650 || document.body.clientHeight < 450 || document.body.clientHeight - 50 < height)
                { fits = true; }
            }
            if ($C("YT_Panel") == null) {
                var sp = document.createElement("div");
                sp.innerHTML = "<div id=\"YT_Panel\" class=\"easyui-panel\"><iframe frameborder=\"0\" id=\"YT_Panel_i\" name=\"YT_Panel_i\" scrolling=\"auto\" src=\"" + url + "\" style=\"height:100%;visibility:inherit; width:100%;z-index:1;\"></iframe></div>";
                document.body.appendChild(sp);
            }
            if (url.indexOf("?") > 0) {
                url = url + "&";
            }
            else {
                url = url + "?";
            }
            url = url + "randomkeys=" + Math.random();
            $C("YT_Panel_i").src = url;
            var sTop = null, sLeft = null;
            if (window.screen.height < 800) {
                sTop = 0;
            }
            if (fits) {
                sLeft = 0;
            }
            $('#YT_Panel').window({
                width: width,
                height: height,
                title: title,
                collapsible: true,
                minimizable: false,
                maximizable: true,
                closable: true,
                modal: true,
                fit: fits,
                top: sTop,
                left: sLeft
            });
        },
        NewPanelNoClose: function (url, title, width, height) {
            if (typeof (width) == 'undefind' || width == null) { width = 750; }
            if (typeof (height) == 'undefind' || height == null) { height = 550; }
            if ($C("YT_Panel") == null) {
                var sp = document.createElement("div");
                sp.innerHTML = "<div id=\"YT_Panel\" class=\"easyui-panel\" ><iframe frameborder=\"0\" id=\"YT_Panel_i\" name=\"YT_Panel_i\" scrolling=\"auto\" src=\"" + url + "\" style=\"height:100%;visibility:inherit; width:100%;z-index:1;\"></iframe></div>";
                document.body.appendChild(sp);
            }
            if (url.indexOf("?") > 0) {
                url = url + "&";
            }
            else {
                url = url + "?";
            }
            url = url + "randomkeys=" + Math.random();
            $C("YT_Panel_i").src = url;
            $('#YT_Panel').window({
                width: width,
                height: height,
                title: title,
                collapsible: false,
                minimizable: false,
                maximizable: true,
                closable: false,
                modal: true
            });
        },
        ClosePanel: function (id) {
            if (typeof (id) == 'undefind' || id == null) {
                $('#YT_Panel').panel('close');
                /*CreateGrid();*/
                CreateGridReload();
            }
            else { $('#' + id).panel('close'); }
        },
        /* Định dạng chuỗi thời gian. */
        formatDate: function (now, types) {
            if (now != null && now != "") {
                var dateN = new Date(+/\d+/.exec(now)[0]);
                var year = dateN.getFullYear();
                var month = dateN.getMonth() + 1;
                var date = dateN.getDate();
                var hour = dateN.getHours();
                var minute = dateN.getMinutes();
                var second = dateN.getSeconds();
                if (typeof (types) != "undefined" && types != null) {
                    return year + "-" + month + "-" + date;
                }
                else if (hour == 0 && minute == 0 && second == 0) {
                    return year + "-" + month + "-" + date;
                }
                else {
                    return year + "-" + month + "-" + date + "   " + hour + ":" + minute + ":" + second;
                }
            }
            else {
                return "";
            }
        },
        /** Lấy tháng của thời gian hiện tại. */
        formatMonth: function (now) {
            if (now != null && now != "") {
                var dateN = new Date(+/\d+/.exec(now)[0]);
                var month = dateN.getMonth() + 1;
                return month;
            }
            else {
                return "";
            }
        },
        /** Lấy ngày cụ thể của thời gian hiện tại. */
        formatDay: function (now) {
            if (now != null && now != "") {
                var dateN = new Date(+/\d+/.exec(now)[0]);
                var month = dateN.getMonth() + 1;
                var date = dateN.getDate();
                return month + "-" + date;
            }
            else {
                return "";
            }
        },
        /** Lấy quý của thời gian hiện tại. */
        formatSeasonal: function (now) {
            if (now != null && now != "") {
                var dateN = new Date(+/\d+/.exec(now)[0]);
                var year = dateN.getFullYear();
                var month = dateN.getMonth() + 1;
                if (month == 1) {
                    return year + " - " + novelMessage('quarter1', 'Quý 1');
                }
                else if (month == 4) {
                    return year + " - " + novelMessage('quarter2', 'Quý 2');
                }
                else if (month == 7) {
                    return year + " - " + novelMessage('quarter3', 'Quý 3');
                }
                else {
                    return year + " - " + novelMessage('quarter4', 'Quý 4');
                }
            }
            else {
                return "";
            }
        },

        formatStatus: function (id) {
            if (id == 0) {
                return novelMessage('invalid', 'Không hợp lệ');
            }
            else {
                return novelMessage('valid', 'Hợp lệ');
            }
        },
        ShowPanel: function (obj, divName, xlong, ylong) {
            var showobj = $C(divName);
            if (showobj) {
                if (showobj.style.display == 'none') {
                    showobj.style.display = '';
                }
                if (xlong)
                { showobj.style.top = YT.BaseCommon.gT(obj) + 20 + xlong + "px"; }
                else
                { showobj.style.top = YT.BaseCommon.gT(obj) + 20 + "px"; }
                if (ylong)
                { showobj.style.left = YT.BaseCommon.gL(obj) + ylong + "px"; }
                else
                { showobj.style.left = YT.BaseCommon.gL(obj) + "px"; }
            }
        },
        GetDateDiff:function(startTime,endTime, diffType) {
            startTime = startTime.replace(/\-/g, "/");
            endTime= endTime.replace(/\-/g, "/");
            diffType = diffType.toLowerCase();
            var sTime = new Date(startTime);
            var eTime = new Date(endTime);
            var timeType = 1;
            switch (diffType) {
                case "second":
                    timeType = 1000;
                    break;
                case "minute":
                    timeType = 1000 * 60;
                    break;
                case "hour":
                    timeType = 1000 * 3600;
                    break;
                case "day":
                    timeType = 1000 * 3600 * 24;
                    break;
                default:
                    break;
            }
            return parseInt((eTime.getTime() - sTime.getTime()) / parseInt(timeType));
        }
    },
    Dirt: {
        /* Gắn dữ liệu vào danh sách. */
        BindList: function (listId, dirtName, needBlock) {
            var obj = $C(listId);
            if (obj != undefined) {
                obj.length = 0;
                var objV = DirtInfo[dirtName];
                if (objV != undefined && objV != null) {
                    for (var i = 0; i < objV.length; i++) {
                        obj.options.add(new Option(objV[i][1], objV[i][0]));
                    }
                }
                if (needBlock) {
                    obj.options.add(new Option(novelMessage('select', 'Vui lòng chọn'), "0"));
                    obj.value = "";
                }
            }
        },
        /* Lấy nhãn tương ứng với giá trị. */
        GetName: function (dirtName, dValue) {
            var obj = DirtInfo[dirtName];
            if (obj != undefined && obj != null) {
                for (var i = 0; i < obj.length; i++) {
                    if (obj[i][0] == dValue) {
                        return obj[i][1];
                    }
                }
            }
            return "";
        }
    },
    Cookie: function (name, value, options) {
        if (typeof value != 'undefined') { /* name and value given, set cookie*/
            options = options || {};
            if (value === null) {
                value = '';
                options.expires = -1;
            }
            var expires = '';
            if (options.expires && (typeof options.expires == 'number' || options.expires.toUTCString)) {
                var date;
                if (typeof options.expires == 'number') {
                    date = new Date();
                    date.setTime(date.getTime() + (options.expires * 24 * 60 * 60 * 1000));
                } else {
                    date = options.expires;
                }
                expires = '; expires=' + date.toUTCString(); /* use expires attribute, max-age is not supported by IE */
            }
            var path = options.path ? '; path=' + options.path : '';
            var domain = options.domain ? '; domain=' + options.domain : '';
            var secure = options.secure ? '; secure' : '';
            document.cookie = [name, '=', encodeURIComponent(value), expires, path, domain, secure].join('');
        } else { /* only name given, get cookie */
            var cookieValue = null;
            if (document.cookie && document.cookie != '') {
                var cookies = document.cookie.split(';');
                for (var i = 0; i < cookies.length; i++) {
                    var cookie = jQuery.trim(cookies[i]);
                    if (cookie.substring(0, name.length + 1) == (name + '=')) {
                        cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                        break;
                    }
                }
            }
            return cookieValue;
        }
    }
}

/* Định nghĩa lại các rule kiểm tra ô nhập. */
$.extend($.fn.validatebox.defaults.rules, {
    chinaMobile: {/* Số điện thoại. */
        validator: function (value, param) {
            var reg = /^(13|14|15|17|18)\d{9}$/;
            var reglt = /^(\d{3}|\d{4})-\d{8}$/;
            /*var reg = /^\d{11,12}$/;*/
            if (value.indexOf('-') > 0) {
                return reglt.test(value);
            }
            else {
                return reg.test(value);
            }
        }, message: novelMessage('phoneInvalid', 'Số điện thoại không hợp lệ.')
    },
    chinaName: {/* Tên theo quy tắc cũ. */
        validator: function (value, param) {
            //            var reg = /^[\u4e00-\u9fa5a-zA-Z0-9]{2,6}$/;
            var reg = /^[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5]{1,5}$/;
            //            var reg = /^[\u4e00-\u9fa5,a-zA-Z0-9]{2,5}$/;
            return reg.test(value);
        }, message: novelMessage('penNameInvalid', 'Bút danh không được bắt đầu bằng chữ số và phải dài từ 2 đến 6 ký tự.')
    },
    realName: {/* Tên thật. */
        validator: function (value, param) {
            //            var reg = /^[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5]{1,5}$/;
            var reg = /^[\u4e00-\u9fa5,a-zA-Z0-9]{2,5}$/;
            return reg.test(value);
        }, message: novelMessage('realNameInvalid', 'Tên thật phải dài từ 2 đến 5 ký tự.')
    },
    maxLength: {
        validator: function (value, param) {
            $.fn.validatebox.defaults.rules.maxLength.message = novelMessage('maxLength', 'Độ dài phải nhỏ hơn {0} ký tự.').replace('{0}', param);
            return value.length < param;
        }
    },
    isNumber: {
        validator: function (value, param) {
            var reg = /^(-|[0-9])(|\d{1,9})$/;
            return reg.test(value);
        }, message: novelMessage('numberRequired', 'Giá trị phải là số.')
    },
    isBankNumber: {
        validator: function (value, param) {
            var reg = /^([0-9]{16}|[0-9]{19})$/;
            return reg.test(value);
        }, message: novelMessage('bankNumberInvalid', 'Số tài khoản ngân hàng không hợp lệ.')
    },
    isEmail: {
        validator: function (value, param) {
            var reg = /^([a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+((\.[a-zA-Z0-9_-]{2,3}){1,2})$/;
            return reg.test(value);
        }, message: novelMessage('emailInvalid', 'Định dạng email không hợp lệ.')
    },

    isPosInt: {
        validator: function (value, param) {
            var reg = /^(\d{1,9})$/;
            if (reg.test(value) && value > 0) {
                return true;
            }
            else {
                return false;
            }
        }, message: novelMessage('positiveInteger', 'Giá trị phải là số nguyên dương lớn hơn 0.')
    },
    isPosIntTen: {
        validator: function (value, param) {
            var reg = /^(\d{1,9})$/;
            if (reg.test(value) && value > 10) {
                return true;
            }
            else {
                return false;
            }
        }, message: novelMessage('integerAbove10', 'Giá trị phải là số nguyên dương lớn hơn 10.')
    },
    isDate: {
        validator: function (value, param) {
            var reg = /^((((1[6-9]|[2-9]\d)\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\d|3[01]))|(((1[6-9]|[2-9]\d)\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\d|30))|(((1[6-9]|[2-9]\d)\d{2})-0?2-(0?[1-9]|1\d|2[0-8]))|(((1[6-9]|[2-9]\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29))$/;
            return reg.test(value);
        }, message: 'yyyy-MM-dd'
    },
    isIdCard: {
        validator: function (value, param) {
            var reg = /^(^\d{15}$|^\d{18}$|^\d{17}(\d|X|x))$/;
            return isCardID(value);
        }, message: novelMessage('idCardInvalid', 'Số giấy tờ tùy thân không hợp lệ.')
    },
    isFloat: {
        validator: function (value, param) {
            var reg = /^(^\+?[1-9][0-9]*$)$|^(\d{1,9}\.\d{1,9})$/;
            return reg.test(value);
        }, message: novelMessage('positiveNumber', 'Giá trị phải là số lớn hơn 0.')
    },
    isFloatMin0:
        {
            validator: function (value, param) {
                var reg = /^(^\d{1,9})$|^(\d{1,9}\.\d{1,9})$/;
                return reg.test(value);
            }, message: novelMessage('positiveNumber', 'Giá trị phải là số lớn hơn 0.')
        },
    isPassWord: {
        validator: function (value, param) {
            var reg = /^[a-zA-Z0-9_]{5,15}$/;
            return reg.test(value);
        }, message: novelMessage('passwordInvalid', 'Định dạng mật khẩu không hợp lệ.')
    },
    isConfirmPassword: {
        validator: function (value, param) {
            return $(param[0]).val() == value;
        }, message: novelMessage('passwordMismatch', 'Hai mật khẩu đã nhập không khớp.')
    },
    phoneCheck: {
        validator: function (value, param) {
            var reg = /^(((\()?\d{2,4}(\))?[-(\s)*]){0,2})?(\d{8})$/;
            return reg.test(value);
        }, message: novelMessage('phoneInvalid', 'Số điện thoại không hợp lệ.')
    },
    isUserName: {
        validator: function (value, param) {
            var reg = /^[a-zA-Z0-9_]{3,15}$/;
            return reg.test(value);
        }, message: novelMessage('usernameInvalid', 'Định dạng tên đăng nhập không hợp lệ.')
    },
    equalTo: {
        validator: function (value, param) {
            return $(param[0]).val() == value;
        },
        message: novelMessage('fieldMismatch', 'Giá trị không khớp.')
    }
});

/* Hàm rỗng giữ tương thích API cũ. */
function CreateGrid() { }
function CreateGridReload() { }

/* Kiểm tra số giấy tờ theo quy tắc cũ. */
var NumbCardCity = {11:true,12:true,13:true,14:true,15:true,21:true,22:true,23:true,31:true,32:true,33:true,34:true,35:true,36:true,37:true,41:true,42:true,43:true,44:true,45:true,46:true,50:true,51:true,52:true,53:true,54:true,61:true,62:true,63:true,64:true,65:true,71:true,81:true,82:true,91:true};
function isCardID(sId) {
    var iSum = 0;
    var info = "";
    if (!/^\d{17}(\d|x)$/i.test(sId)) return false; /* Độ dài hoặc định dạng giấy tờ không hợp lệ. */
    sId = sId.replace(/x$/i, "a");
    if (NumbCardCity[parseInt(sId.substr(0, 2))] == null) return false; /* Mã khu vực không hợp lệ. */
    sBirthday = sId.substr(6, 4) + "-" + Number(sId.substr(10, 2)) + "-" + Number(sId.substr(12, 2));
    var d = new Date(sBirthday.replace(/-/g, "/"));
    if (sBirthday != (d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate())) return false; /* Ngày sinh trên giấy tờ không hợp lệ. */
    for (var i = 17; i >= 0; i--) iSum += (Math.pow(2, i) % 11) * parseInt(sId.charAt(17 - i), 11);
    if (iSum % 11 != 1) return false; /* Số giấy tờ không hợp lệ. */
    return true;
}

function getSex(val) {
    if (parseInt(val.charAt(16) / 2) * 2 != val.charAt(16))
        return '1';
    else
        return '0';
}
function showBirthday(val) {
    var mm;
    if (18 == val.length) {/* Số giấy tờ 18 chữ số. */
        mm = val.charAt(6) + val.charAt(7) + val.charAt(8) + val.charAt(9) + '-' + val.charAt(10) + val.charAt(11) + '-' + val.charAt(12) + val.charAt(13);

    }
    return mm;
}


var DirtInfo = {
    TrueOrFalse: [[0, novelMessage('legacyNo', 'Không')], [1, novelMessage('legacyYes', 'Có')]],
    EnumUserCommendStatus: [[0, novelMessage('legacyNew', 'Mới')], [1, novelMessage('legacyProcessed', 'Đã xử lý')], [2, novelMessage('legacyViewed', 'Đã xem')]],
    AvailablesStatus: [[0, novelMessage('legacyDisabled', 'Đã tắt')], [1, novelMessage('legacyAvailable', 'Khả dụng')]],
    SettleClass: [[0, novelMessage('legacyCash', 'Tiền mặt')], [1, novelMessage('legacyPrepaid', 'Khấu trừ trả trước')]],
    EnumSexClass: [[0, novelMessage('legacyUnlimited', 'Không giới hạn')], [1, novelMessage('legacyMale', 'Nam')], [2, novelMessage('legacyFemale', 'Nữ')]],
    EnumUserType: [[1, novelMessage('legacyMobileApp', 'Ứng dụng di động')], [2, novelMessage('legacyMobileWeb', 'Web di động')]],
    EnumPayClass: [[1, novelMessage('legacyPaymentChannel1', 'Kênh thanh toán cũ')], [2, novelMessage('legacyWechat', 'WeChat')], [3, novelMessage('legacyWechatQr', 'Mã QR WeChat')], [4, 'VNPAY'], [100, novelMessage('legacyPhoneReward', 'Thưởng liên kết số điện thoại')]],
    EnumPayStatus: [[0, novelMessage('legacyNewRequest', 'Yêu cầu mới')], [2, novelMessage('legacyRechargeFailed', 'Nạp tiền thất bại')], [3, novelMessage('legacySuccess', 'Thành công')]],
    EnumMoneyClass: [[0, novelMessage('legacyPurchase', 'Mua')], [1, novelMessage('legacyGift', 'Tặng')]],
    EnumUserFrom: [[1, novelMessage('legacyOther', 'Khác')], [2, novelMessage('legacyWeibo', 'Weibo')], [3, novelMessage('legacyQq', 'QQ')], [4, novelMessage('legacyWechat', 'WeChat')], [10, novelMessage('legacyAppRegister', 'Đăng ký qua ứng dụng')], [11, novelMessage('legacyWapRegister', 'Đăng ký qua web di động')], [12, novelMessage('legacyWeiboBind', 'Liên kết Weibo')], [13, novelMessage('legacyQqBind', 'Liên kết QQ')], [14, novelMessage('legacyWechatBind', 'Liên kết WeChat')]],
    EnumSignType: [[0, novelMessage('legacyUnsigned', 'Chưa ký hợp đồng')], [1, novelMessage('legacyRevenueShare', 'Chia sẻ doanh thu')], [2, novelMessage('legacyBuyout', 'Mua đứt')], [3, novelMessage('legacyGuarantee', 'Bảo đảm tối thiểu')], [4, novelMessage('legacyFullBuyout', 'Mua đứt toàn bộ')], [9, novelMessage('legacyGuarantee', 'Bảo đảm tối thiểu')], [15, novelMessage('legacyItemSettlement', 'Đối soát vật phẩm')], [30, novelMessage('legacyAttendanceBonus', 'Thưởng chuyên cần')]],
    EnumLogType: [[0, novelMessage('legacyAppLogin', 'Đăng nhập ứng dụng')], [1, novelMessage('legacyWapLogin', 'Đăng nhập web di động')]],
    EnumAuditStatus: [[-10, novelMessage('legacyOffline', 'Đã gỡ')], [-1, novelMessage('legacyAuditFailed', 'Duyệt thất bại')], [0, novelMessage('legacyEditing', 'Đang biên tập')], [1, novelMessage('legacySubmitted', 'Đã gửi duyệt')], [2, novelMessage('legacyApproved', 'Đã duyệt')], [3, novelMessage('legacyPublished', 'Đã xuất bản')]],
    EnumHandleStatus: [[-1, novelMessage('legacyProcessingFailed', 'Xử lý thất bại')], [0, novelMessage('legacyNewRequest', 'Yêu cầu mới')], [1, novelMessage('legacyPending', 'Chờ xử lý')], [2, novelMessage('legacyProcessedSuccess', 'Xử lý thành công')]],
    EnumAuthorLevel: [[1, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 1)], [2, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 2)], [3, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 3)], [4, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 4)], [5, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 5)]],
    EnumChannelClass: [[0, novelMessage('legacySpecial', 'Đặc biệt')], [1, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 1)], [2, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 2)], [3, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 3)], [4, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 4)], [5, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 5)], [6, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 6)], [7, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 7)], [8, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 8)], [9, novelMessage('legacyLevel', 'Cấp {0}').replace('{0}', 9)], [1100, novelMessage('legacyThousandLevel', 'Cấp nghìn')]],
    EnumVipChapter: [[0, novelMessage('legacyPublic', 'Công khai')], [1, "VIP"]],
    EnumBookLeveType: [[1, "A"], [2, "B"], [3, "C"], [4, novelMessage('legacyNormal', 'Thông thường')], [5, "S"]],
    EnumBookLeveTypeL: [[1, "A"], [2, "B"], [3, "C"]],
    EnumAdmActClass: [[50, novelMessage('legacyContractLevelChange', 'Đổi cấp hợp đồng')], [51, novelMessage('legacyCoverChange', 'Đổi ảnh bìa')], [52, novelMessage('legacyChannelAdd', 'Thêm kênh')], [53, novelMessage('legacyChannelEdit', 'Sửa kênh')], [54, novelMessage('legacyChannelDelete', 'Xóa kênh')], [55, novelMessage('legacyChapterDelete', 'Xóa chương')]],
    EnumSettlementType: [[0, novelMessage('legacyUnsettled', 'Chưa đối soát')], [1, novelMessage('legacySettled', 'Đã đối soát')], [2, novelMessage('legacySettlementFailed', 'Đối soát thất bại')]],
    EnumBookProcess: [[0, novelMessage('legacySerializing', 'Đang ra')], [1, novelMessage('legacyCompleted', 'Đã hoàn thành')]],
    EnumAuthStatus: [[1, novelMessage('legacyExclusive', 'Độc quyền')], [2, novelMessage('legacyNonExclusive', 'Không độc quyền')]]
};

function dateToDate(date) {
    var sDate = new Date();
    if (typeof date == 'object'
        && typeof new Date().getMonth == "function"
    ) {
        sDate = date;
    }
    else if (typeof date == "string") {
        var arr = date.split('-')
        if (arr.length == 3) {
            sDate = new Date(arr[0] + '-' + arr[1] + '-' + arr[2]);
        }
    }

    return sDate;
}


function addMonth(date, num) {
    num = parseInt(num);
    var sDate = dateToDate(date);

    var sYear = sDate.getFullYear();
    var sMonth = sDate.getMonth() + 1;
    var sDay = sDate.getDate();

    var eYear = sYear;
    var eMonth = sMonth + num;
    var eDay = sDay;
    while (eMonth > 12) {
        eYear++;
        eMonth -= 12;
    }

    var eDate = new Date(eYear, eMonth - 1, eDay);

    while (eDate.getMonth() != eMonth - 1) {
        eDay--;
        eDate = new Date(eYear, eMonth - 1, eDay);
    }

    return eDate;
}

function checkAll() {
    if ($("#selAll").attr("checked")) {
        $("input[name='selBox']").each(function () {
            $(this).attr("checked", true);
        });
    }
    else {
        $("input[name='selBox']").each(function () {
            $(this).removeAttr("checked");
        });
    }
}

$(function () {
    initSubmitButton(3);
});

// Khóa nút gửi trong thời gian chờ.
function initSubmitButton(wait) {
    $("input[type='submit']").each(function () {
        $(this).click(function () {
            if ($(this).attr("submited") == "1") {
                return false;
            }
            var oldVal = $(this).val();
            $(this).val(novelMessage('processing', 'Đang xử lý, vui lòng chờ ({0})').replace('{0}', wait));
            $(this).attr("submited", "1");
            setTimeout('ButtonLimit("' + $(this).attr("id") + '",' + wait + ',"' + oldVal + '")', 1000);
        });
    });
}
function ButtonLimit(objId, wait, oldVal) {
    wait--;
    if (wait > 0) {
        $("#" + objId).val(novelMessage('processing', 'Đang xử lý, vui lòng chờ ({0})').replace('{0}', wait));
        setTimeout('ButtonLimit("' + objId + '",' + wait + ',"' + oldVal + '");', 1000);
    }
    else {
        $("#" + objId).removeAttr("submited");
        $("#" + objId).val(oldVal);
    }
}
