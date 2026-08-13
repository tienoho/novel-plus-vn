var dictList = Array.isArray(parent.dictList) ? parent.dictList : [];
$(function () {

    loadDict();




});

function loadDict(){
    $(".chosen-select").each(function (index, domEle) {

        var dictType = $(domEle).attr("dict-type");
        var dictValue = $(domEle).attr("dict-value");
        var changeFunc = $(domEle).attr("dict-change-func");
        if (dictType) {
            // Tải dữ liệu
            for (var i = 0; i < dictList.length; i++) {
                if (dictList[i].type === dictType) {
                    var option = document.createElement("option");
                    option.value = dictList[i].value == null ? "" : String(dictList[i].value);
                    option.textContent = dictList[i].name == null ? "" : String(dictList[i].name);
                    domEle.appendChild(option);
                }
            }
            $(domEle).chosen({
                maxHeight: 200
            });
            $(domEle).val(dictValue);
            $(domEle).trigger("chosen:updated");
            // Sự kiện nhấp
            $(domEle).on('change', function (e, params) {
                if (changeFunc && /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(changeFunc)) {
                    var handler = window[changeFunc];
                    if (typeof handler === "function") {
                        handler.call(domEle, e, params);
                    }
                }
            });
        }


    });

    $(".dict-type").each(function (index, domEle) {

        var dictType = $(domEle).attr("dict-type");
        var dictValue = $(domEle).attr("dict-value");
        $(domEle).text(formatDict(dictType,dictValue));


    });
}


function formatDict(dictType, value) {
    var name = "";
    // Tải dữ liệu
    for (var i = 0; i < dictList.length; i++) {

        if (dictList[i].type === dictType && String(dictList[i].value) === String(value)) {
            name = dictList[i].name;
        }
    }

    return name;


}


