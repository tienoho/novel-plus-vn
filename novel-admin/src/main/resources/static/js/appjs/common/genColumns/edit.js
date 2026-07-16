var columnData = JSON.parse($("#genColumns").val());
var columnJsData = [[]];
var row_template = ['text','text','text','text','text','text','text','text','text'];
var headerCols = [adminMessage('columnName', 'Tên cột'), adminMessage('columnType', 'Kiểu cột'), adminMessage('columnJavaType', 'Kiểu Java ánh xạ'), adminMessage('columnComment', 'Chú thích cột'), adminMessage('columnLabel', 'Nhãn cột'), adminMessage('columnDisplayType', 'Kiểu hiển thị'), adminMessage('columnDictType', 'Loại từ điển'), adminMessage('columnRequired', 'Bắt buộc'), adminMessage('columnSort', 'Thứ tự cột')];
for (var i = 0; i < columnData.length; i++) {
    columnJsData[i] = [columnData[i].columnName, columnData[i].columnType, columnData[i].javaType, columnData[i].columnComment,
        columnData[i].columnLabel, columnData[i].pageType, columnData[i].dictType, columnData[i].isRequired, columnData[i].columnSort];

}


var mynewtable = $('#edittable').editTable({
    field_templates: {
        'checkbox': {
            html: '<input type="checkbox"/>',
            getValue: function (input) {
                return $(input).is(':checked');
            },
            setValue: function (input, value) {
                if (value) {
                    return $(input).attr('checked', true);
                }
                return $(input).removeAttr('checked');
            }
        },
        'textarea': {
            html: '<textarea/>',
            getValue: function (input) {
                return $(input).val();
            },
            setValue: function (input, value) {
                return $(input).text(value);
            }
        },
        'select': {
            html: '<select><option value="">None</option><option>All</option></select>',
            getValue: function (input) {
                return $(input).val();
            },
            setValue: function (input, value) {
                var select = $(input);
                select.find('option').filter(function () {
                    return $(this).val() == value;
                }).attr('selected', true);
                return select;
            }
        }
    },
    row_template: row_template,
    headerCols: headerCols,
    first_row: false,
    data: columnJsData,
    jsonData: false,
    validate_field: function (col_id, value, col_type, $element) {
        if (col_type === 'checkbox') {
            $element.parent('td').animate({'background-color': '#fff'});
            if (value === false) {
                $element.parent('td').animate({'background-color': '#DB4A39'});
                return false;
            }
        }
        return true;
    },
    tableClass: 'inputtable custom'

});


