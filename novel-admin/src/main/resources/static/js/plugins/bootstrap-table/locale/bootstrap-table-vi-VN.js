(function ($) {
    'use strict';
    $.fn.bootstrapTable.locales['vi-VN'] = {
        formatLoadingMessage: function () { return adminMessage('loading', 'Đang tải dữ liệu, vui lòng chờ…'); },
        formatRecordsPerPage: function (pageNumber) { return adminFormat('recordsPerPage', '{0} bản ghi mỗi trang', pageNumber); },
        formatShowingRows: function (pageFrom, pageTo, totalRows) { return adminFormat('showingRecords', 'Hiển thị từ {0} đến {1} trên tổng số {2} bản ghi', pageFrom, pageTo, totalRows); },
        formatSearch: function () { return adminMessage('searchRecords', 'Tìm kiếm'); },
        formatNoMatches: function () { return adminMessage('noRecords', 'Không tìm thấy bản ghi phù hợp'); },
        formatPaginationSwitch: function () { return 'Ẩn/hiện phân trang'; },
        formatRefresh: function () { return 'Làm mới'; },
        formatToggle: function () { return 'Chuyển chế độ xem'; },
        formatColumns: function () { return 'Cột'; },
        formatAllRows: function () { return 'Tất cả'; }
    };
    $.extend($.fn.bootstrapTable.defaults, $.fn.bootstrapTable.locales['vi-VN']);
})(jQuery);
