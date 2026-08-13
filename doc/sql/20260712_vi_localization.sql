-- Việt hóa dữ liệu mặc định. Các điều kiện so khớp chính xác giúp bảo toàn dữ liệu đã tùy chỉnh
-- và cho phép chạy migration nhiều lần mà không phát sinh thay đổi bổ sung.

SET NAMES utf8mb4;

UPDATE book_category
SET name = CASE name
    WHEN '玄幻奇幻' THEN 'Huyền huyễn/Kỳ ảo'
    WHEN '武侠仙侠' THEN 'Kiếm hiệp/Tiên hiệp'
    WHEN '都市言情' THEN 'Đô thị/Ngôn tình'
    WHEN '历史军事' THEN 'Lịch sử/Quân sự'
    WHEN '科幻灵异' THEN 'Viễn tưởng/Linh dị'
    WHEN '网游竞技' THEN 'Trò chơi thi đấu'
    WHEN '女生频道' THEN 'Truyện nữ'
    WHEN '轻小说' THEN 'Light novel'
    WHEN '漫画' THEN 'Truyện tranh'
    ELSE name
END
WHERE name IN ('玄幻奇幻', '武侠仙侠', '都市言情', '历史军事', '科幻灵异', '网游竞技', '女生频道', '轻小说', '漫画');

-- Menu hệ thống: khóa theo menu_id và tên mặc định để bảo toàn menu đã tùy chỉnh.
UPDATE sys_menu
SET name = 'Quản lý cơ bản'
WHERE menu_id = 1
  AND name = '基础管理';

UPDATE sys_menu
SET name = 'Menu hệ thống'
WHERE menu_id = 2
  AND name = '系统菜单';

UPDATE sys_menu
SET name = 'Quản trị hệ thống'
WHERE menu_id = 3
  AND name = '系统管理';

UPDATE sys_menu
SET name = 'Quản lý người dùng'
WHERE menu_id = 6
  AND name = '用户管理';

UPDATE sys_menu
SET name = 'Quản lý vai trò'
WHERE menu_id = 7
  AND name = '角色管理';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 12
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 13
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 14
  AND name = '删除';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 15
  AND name = '新增';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 20
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 21
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 22
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 24
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Vô hiệu hóa'
WHERE menu_id = 25
  AND name = '停用';

UPDATE sys_menu
SET name = 'Đặt lại mật khẩu'
WHERE menu_id = 26
  AND name = '重置密码';

UPDATE sys_menu
SET name = 'Nhật ký hệ thống'
WHERE menu_id = 27
  AND name = '系统日志';

UPDATE sys_menu
SET name = 'Làm mới'
WHERE menu_id = 28
  AND name = '刷新';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 29
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa sạch'
WHERE menu_id = 30
  AND name = '清空';

UPDATE sys_menu
SET name = 'Sinh mã nguồn'
WHERE menu_id = 48
  AND name = '代码生成';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 55
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 56
  AND name = '删除';

UPDATE sys_menu
SET name = 'Giám sát hoạt động'
WHERE menu_id = 57
  AND name = '运行监控';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 61
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 62
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Quản lý tệp'
WHERE menu_id = 71
  AND name = '文件管理';

UPDATE sys_menu
SET name = 'Quản lý phòng ban'
WHERE menu_id = 73
  AND name = '部门管理';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 74
  AND name = '增加';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 75
  AND name = '刪除';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 76
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Công cụ phát triển'
WHERE menu_id = 77
  AND name = '研发工具';

UPDATE sys_menu
SET name = 'Từ điển dữ liệu'
WHERE menu_id = 78
  AND name = '数据字典';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 79
  AND name = '增加';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 80
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 81
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 83
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Giám sát hệ thống'
WHERE menu_id = 91
  AND name = '系统监控';

UPDATE sys_menu
SET name = 'Người dùng trực tuyến'
WHERE menu_id = 92
  AND name = '在线用户';

UPDATE sys_menu
SET name = 'Tài liệu API'
WHERE menu_id = 104
  AND name = 'swagger';

UPDATE sys_menu
SET name = 'Quản lý kiểm thử'
WHERE menu_id = 202
  AND name = '测试管理';

UPDATE sys_menu
SET name = 'Quản lý đơn hàng'
WHERE menu_id = 203
  AND name = '订单管理';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 204
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 205
  AND name = '编辑';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 206
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 207
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Chi tiết'
WHERE menu_id = 208
  AND name = '详情';

UPDATE sys_menu
SET name = 'Quyền dữ liệu'
WHERE menu_id = 209
  AND name = '数据权限';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 210
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 211
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 212
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 213
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 214
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Quản lý tác giả'
WHERE menu_id = 221
  AND name = '作家管理';

UPDATE sys_menu
SET name = 'Danh sách tác giả'
WHERE menu_id = 222
  AND name = '作者列表';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 223
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 224
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 225
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 226
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 227
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Quản lý mã mời'
WHERE menu_id = 228
  AND name = '邀请码管理';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 229
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 230
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 231
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 232
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 233
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Quản lý website'
WHERE menu_id = 300
  AND name = '网站管理';

UPDATE sys_menu
SET name = 'Thông tin website'
WHERE menu_id = 301
  AND name = '网站信息';

UPDATE sys_menu
SET name = 'Liên kết bạn bè'
WHERE menu_id = 310
  AND name = '友情链接';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 311
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 312
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 313
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 314
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 315
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Quản lý thành viên'
WHERE menu_id = 400
  AND name = '会员管理';

UPDATE sys_menu
SET name = 'Danh sách thành viên'
WHERE menu_id = 401
  AND name = '会员列表';

UPDATE sys_menu
SET name = 'Quản lý đơn hàng'
WHERE menu_id = 500
  AND name = '订单管理';

UPDATE sys_menu
SET name = 'Danh sách đơn hàng'
WHERE menu_id = 501
  AND name = '订单列表';

UPDATE sys_menu
SET name = 'Quản lý tác phẩm'
WHERE menu_id = 600
  AND name = '小说管理';

UPDATE sys_menu
SET name = 'Danh sách tác phẩm'
WHERE menu_id = 601
  AND name = '小说列表';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 602
  AND name = '删除';

UPDATE sys_menu
SET name = 'Quản lý bình luận'
WHERE menu_id = 603
  AND name = '评论管理';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 604
  AND name = '删除';

UPDATE sys_menu
SET name = 'Đề xuất tác phẩm'
WHERE menu_id = 320
  AND name = '小说推荐';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 321
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 322
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 323
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 324
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 325
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Phản hồi thành viên'
WHERE menu_id = 410
  AND name = '会员反馈';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 305
  AND name = '修改';

UPDATE sys_menu
SET name = 'Quản lý tin tức'
WHERE menu_id = 234
  AND name = '新闻管理';

UPDATE sys_menu
SET name = 'Quản lý danh mục'
WHERE menu_id = 235
  AND name = '类别管理';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 236
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 237
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 238
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 239
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 240
  AND name = '批量删除';

UPDATE sys_menu
SET name = 'Danh sách tin tức'
WHERE menu_id = 241
  AND name = '新闻列表';

UPDATE sys_menu
SET name = 'Xem'
WHERE menu_id = 242
  AND name = '查看';

UPDATE sys_menu
SET name = 'Thêm mới'
WHERE menu_id = 243
  AND name = '新增';

UPDATE sys_menu
SET name = 'Chỉnh sửa'
WHERE menu_id = 244
  AND name = '修改';

UPDATE sys_menu
SET name = 'Xóa'
WHERE menu_id = 245
  AND name = '删除';

UPDATE sys_menu
SET name = 'Xóa hàng loạt'
WHERE menu_id = 246
  AND name = '批量删除';

-- Thông tin website: mỗi cột chỉ đổi khi vẫn giữ nguyên giá trị mặc định.
UPDATE website_info
SET name = 'Khởi Thư'
WHERE id = 1
  AND name = '小说精品屋';

UPDATE website_info
SET keyword = 'Khởi Thư,truyện,đọc truyện,CMS truyện,văn học'
WHERE id = 1
  AND keyword = '小说精品屋,小说,小说CMS,原创文学系统,开源小说系统,免费小说建站程序';

UPDATE website_info
SET description = 'Khởi Thư là nền tảng đọc và sáng tác truyện trên máy tính và thiết bị di động, hỗ trợ thành viên, tác giả, thanh toán, tin tức, báo cáo và thu thập nội dung.'
WHERE id = 1
  AND description = '小说精品屋是一个多端（PC、WAP）阅读、功能完善的原创文学CMS系统，由前台门户系统、作家后台管理系统、平台后台管理系统、爬虫管理系统等多个子系统构成，支持会员充值、订阅模式、新闻发布和实时统计报表等功能，新书自动入库，老书自动更新。';

-- Từ điển hệ thống: khóa theo id, type và cả hai giá trị mặc định để không ghi đè bản ghi đã tùy chỉnh.
UPDATE sys_dict
SET name = 'Bình thường',
    description = 'Cờ xóa'
WHERE id = 1
  AND type = 'del_flag'
  AND name = '正常'
  AND description = '删除标记';

UPDATE sys_dict
SET name = 'Hiển thị',
    description = 'Hiển thị / ẩn'
WHERE id = 3
  AND type = 'show_hide'
  AND name = '显示'
  AND description = '显示/隐藏';

UPDATE sys_dict
SET name = 'Ẩn',
    description = 'Hiển thị / ẩn'
WHERE id = 4
  AND type = 'show_hide'
  AND name = '隐藏'
  AND description = '显示/隐藏';

UPDATE sys_dict
SET name = 'Có',
    description = 'Có / không'
WHERE id = 5
  AND type = 'yes_no'
  AND name = '是'
  AND description = '是/否';

UPDATE sys_dict
SET name = 'Không',
    description = 'Có / không'
WHERE id = 6
  AND type = 'yes_no'
  AND name = '否'
  AND description = '是/否';

UPDATE sys_dict
SET name = 'Màu đỏ',
    description = 'Giá trị màu'
WHERE id = 7
  AND type = 'color'
  AND name = '红色'
  AND description = '颜色值';

UPDATE sys_dict
SET name = 'Màu xanh lá',
    description = 'Giá trị màu'
WHERE id = 8
  AND type = 'color'
  AND name = '绿色'
  AND description = '颜色值';

UPDATE sys_dict
SET name = 'Màu xanh dương',
    description = 'Giá trị màu'
WHERE id = 9
  AND type = 'color'
  AND name = '蓝色'
  AND description = '颜色值';

UPDATE sys_dict
SET name = 'Màu vàng',
    description = 'Giá trị màu'
WHERE id = 10
  AND type = 'color'
  AND name = '黄色'
  AND description = '颜色值';

UPDATE sys_dict
SET name = 'Màu cam',
    description = 'Giá trị màu'
WHERE id = 11
  AND type = 'color'
  AND name = '橙色'
  AND description = '颜色值';

UPDATE sys_dict
SET name = 'Giao diện mặc định',
    description = 'Phương án giao diện'
WHERE id = 12
  AND type = 'theme'
  AND name = '默认主题'
  AND description = '主题方案';

UPDATE sys_dict
SET name = 'Giao diện xanh da trời',
    description = 'Phương án giao diện'
WHERE id = 13
  AND type = 'theme'
  AND name = '天蓝主题'
  AND description = '主题方案';

UPDATE sys_dict
SET name = 'Giao diện cam',
    description = 'Phương án giao diện'
WHERE id = 14
  AND type = 'theme'
  AND name = '橙色主题'
  AND description = '主题方案';

UPDATE sys_dict
SET name = 'Giao diện đỏ',
    description = 'Phương án giao diện'
WHERE id = 15
  AND type = 'theme'
  AND name = '红色主题'
  AND description = '主题方案';

UPDATE sys_dict
SET name = 'Giao diện phẳng',
    description = 'Phương án giao diện'
WHERE id = 16
  AND type = 'theme'
  AND name = 'Flat主题'
  AND description = '主题方案';

UPDATE sys_dict
SET name = 'Quốc gia',
    description = 'Loại khu vực'
WHERE id = 17
  AND type = 'sys_area_type'
  AND name = '国家'
  AND description = '区域类型';

UPDATE sys_dict
SET name = 'Tỉnh / thành phố trực thuộc',
    description = 'Loại khu vực'
WHERE id = 18
  AND type = 'sys_area_type'
  AND name = '省份、直辖市'
  AND description = '区域类型';

UPDATE sys_dict
SET name = 'Thành phố',
    description = 'Loại khu vực'
WHERE id = 19
  AND type = 'sys_area_type'
  AND name = '地市'
  AND description = '区域类型';

UPDATE sys_dict
SET name = 'Quận / huyện',
    description = 'Loại khu vực'
WHERE id = 20
  AND type = 'sys_area_type'
  AND name = '区县'
  AND description = '区域类型';

UPDATE sys_dict
SET name = 'Công ty',
    description = 'Loại tổ chức'
WHERE id = 21
  AND type = 'sys_office_type'
  AND name = '公司'
  AND description = '机构类型';

UPDATE sys_dict
SET name = 'Phòng ban',
    description = 'Loại tổ chức'
WHERE id = 22
  AND type = 'sys_office_type'
  AND name = '部门'
  AND description = '机构类型';

UPDATE sys_dict
SET name = 'Nhóm',
    description = 'Loại tổ chức'
WHERE id = 23
  AND type = 'sys_office_type'
  AND name = '小组'
  AND description = '机构类型';

UPDATE sys_dict
SET name = 'Khác',
    description = 'Loại tổ chức'
WHERE id = 24
  AND type = 'sys_office_type'
  AND name = '其它'
  AND description = '机构类型';

UPDATE sys_dict
SET name = 'Phòng tổng hợp',
    description = 'Phòng ban dùng nhanh'
WHERE id = 25
  AND type = 'sys_office_common'
  AND name = '综合部'
  AND description = '快捷通用部门';

UPDATE sys_dict
SET name = 'Phòng phát triển',
    description = 'Phòng ban dùng nhanh'
WHERE id = 26
  AND type = 'sys_office_common'
  AND name = '开发部'
  AND description = '快捷通用部门';

UPDATE sys_dict
SET name = 'Phòng nhân sự',
    description = 'Phòng ban dùng nhanh'
WHERE id = 27
  AND type = 'sys_office_common'
  AND name = '人力部'
  AND description = '快捷通用部门';

UPDATE sys_dict
SET name = 'Cấp 1',
    description = 'Cấp tổ chức'
WHERE id = 28
  AND type = 'sys_office_grade'
  AND name = '一级'
  AND description = '机构等级';

UPDATE sys_dict
SET name = 'Cấp 2',
    description = 'Cấp tổ chức'
WHERE id = 29
  AND type = 'sys_office_grade'
  AND name = '二级'
  AND description = '机构等级';

UPDATE sys_dict
SET name = 'Cấp 3',
    description = 'Cấp tổ chức'
WHERE id = 30
  AND type = 'sys_office_grade'
  AND name = '三级'
  AND description = '机构等级';

UPDATE sys_dict
SET name = 'Cấp 4',
    description = 'Cấp tổ chức'
WHERE id = 31
  AND type = 'sys_office_grade'
  AND name = '四级'
  AND description = '机构等级';

UPDATE sys_dict
SET name = 'Mọi dữ liệu',
    description = 'Phạm vi dữ liệu'
WHERE id = 32
  AND type = 'sys_data_scope'
  AND name = '所有数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Dữ liệu công ty và cấp dưới',
    description = 'Phạm vi dữ liệu'
WHERE id = 33
  AND type = 'sys_data_scope'
  AND name = '所在公司及以下数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Dữ liệu công ty',
    description = 'Phạm vi dữ liệu'
WHERE id = 34
  AND type = 'sys_data_scope'
  AND name = '所在公司数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Dữ liệu phòng ban và cấp dưới',
    description = 'Phạm vi dữ liệu'
WHERE id = 35
  AND type = 'sys_data_scope'
  AND name = '所在部门及以下数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Dữ liệu phòng ban',
    description = 'Phạm vi dữ liệu'
WHERE id = 36
  AND type = 'sys_data_scope'
  AND name = '所在部门数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Chỉ dữ liệu cá nhân',
    description = 'Phạm vi dữ liệu'
WHERE id = 37
  AND type = 'sys_data_scope'
  AND name = '仅本人数据'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Thiết lập chi tiết',
    description = 'Phạm vi dữ liệu'
WHERE id = 38
  AND type = 'sys_data_scope'
  AND name = '按明细设置'
  AND description = '数据范围';

UPDATE sys_dict
SET name = 'Quản trị hệ thống',
    description = 'Loại người dùng'
WHERE id = 39
  AND type = 'sys_user_type'
  AND name = '系统管理'
  AND description = '用户类型';

UPDATE sys_dict
SET name = 'Quản lý phòng ban',
    description = 'Loại người dùng'
WHERE id = 40
  AND type = 'sys_user_type'
  AND name = '部门经理'
  AND description = '用户类型';

UPDATE sys_dict
SET name = 'Người dùng thông thường',
    description = 'Loại người dùng'
WHERE id = 41
  AND type = 'sys_user_type'
  AND name = '普通用户'
  AND description = '用户类型';

UPDATE sys_dict
SET name = 'Giao diện cơ bản',
    description = 'Giao diện website'
WHERE id = 42
  AND type = 'cms_theme'
  AND name = '基础主题'
  AND description = '站点主题';

UPDATE sys_dict
SET name = 'Giao diện xanh dương',
    description = 'Giao diện website'
WHERE id = 43
  AND type = 'cms_theme'
  AND name = '蓝色主题'
  AND description IN ('站点主题', 'Giao diện website');

UPDATE sys_dict
SET name = 'Giao diện đỏ',
    description = 'Giao diện website'
WHERE id = 44
  AND type = 'cms_theme'
  AND name = '红色主题'
  AND description = '站点主题';

UPDATE sys_dict
SET name = 'Mô hình bài viết',
    description = 'Mô hình danh mục'
WHERE id = 45
  AND type = 'cms_module'
  AND name = '文章模型'
  AND description = '栏目模型';

UPDATE sys_dict
SET name = 'Mô hình hình ảnh',
    description = 'Mô hình danh mục'
WHERE id = 46
  AND type = 'cms_module'
  AND name = '图片模型'
  AND description = '栏目模型';

UPDATE sys_dict
SET name = 'Mô hình tải xuống',
    description = 'Mô hình danh mục'
WHERE id = 47
  AND type = 'cms_module'
  AND name = '下载模型'
  AND description = '栏目模型';

UPDATE sys_dict
SET name = 'Mô hình liên kết',
    description = 'Mô hình danh mục'
WHERE id = 48
  AND type = 'cms_module'
  AND name = '链接模型'
  AND description = '栏目模型';

UPDATE sys_dict
SET name = 'Mô hình chuyên đề',
    description = 'Mô hình danh mục'
WHERE id = 49
  AND type = 'cms_module'
  AND name = '专题模型'
  AND description = '栏目模型';

UPDATE sys_dict
SET name = 'Kiểu hiển thị mặc định',
    description = 'Kiểu hiển thị'
WHERE id = 50
  AND type = 'cms_show_modes'
  AND name = '默认展现方式'
  AND description = '展现方式';

UPDATE sys_dict
SET name = 'Danh sách nội dung danh mục đầu',
    description = 'Kiểu hiển thị'
WHERE id = 51
  AND type = 'cms_show_modes'
  AND name = '首栏目内容列表'
  AND description = '展现方式';

UPDATE sys_dict
SET name = 'Nội dung đầu tiên của danh mục',
    description = 'Kiểu hiển thị'
WHERE id = 52
  AND type = 'cms_show_modes'
  AND name = '栏目第一条内容'
  AND description = '展现方式';

UPDATE sys_dict
SET name = 'Phát hành',
    description = 'Trạng thái nội dung'
WHERE id = 53
  AND type = 'cms_del_flag'
  AND name = '发布'
  AND description = '内容状态';

UPDATE sys_dict
SET name = 'Xóa',
    description = 'Trạng thái nội dung'
WHERE id = 54
  AND type = 'cms_del_flag'
  AND name = '删除'
  AND description = '内容状态';

UPDATE sys_dict
SET name = 'Duyệt',
    description = 'Trạng thái nội dung'
WHERE id = 55
  AND type = 'cms_del_flag'
  AND name = '审核'
  AND description = '内容状态';

UPDATE sys_dict
SET name = 'Ảnh nổi bật trang chủ',
    description = 'Vị trí đề xuất'
WHERE id = 56
  AND type = 'cms_posid'
  AND name = '首页焦点图'
  AND description = '推荐位';

UPDATE sys_dict
SET name = 'Đề xuất bài viết trang danh mục',
    description = 'Vị trí đề xuất'
WHERE id = 57
  AND type = 'cms_posid'
  AND name = '栏目页文章推荐'
  AND description = '推荐位';

UPDATE sys_dict
SET name = 'Tư vấn',
    description = 'Danh mục phản hồi'
WHERE id = 58
  AND type = 'cms_guestbook'
  AND name = '咨询'
  AND description = '留言板分类';

UPDATE sys_dict
SET name = 'Góp ý',
    description = 'Danh mục phản hồi'
WHERE id = 59
  AND type = 'cms_guestbook'
  AND name = '建议'
  AND description = '留言板分类';

UPDATE sys_dict
SET name = 'Khiếu nại',
    description = 'Danh mục phản hồi'
WHERE id = 60
  AND type = 'cms_guestbook'
  AND name = '投诉'
  AND description = '留言板分类';

UPDATE sys_dict
SET name = 'Khác',
    description = 'Danh mục phản hồi'
WHERE id = 61
  AND type = 'cms_guestbook'
  AND name = '其它'
  AND description = '留言板分类';

UPDATE sys_dict
SET name = 'Nghỉ phép',
    description = 'Loại nghỉ phép'
WHERE id = 62
  AND type = 'oa_leave_type'
  AND name = '公休'
  AND description = '请假类型';

UPDATE sys_dict
SET name = 'Nghỉ ốm',
    description = 'Loại nghỉ phép'
WHERE id = 63
  AND type = 'oa_leave_type'
  AND name = '病假'
  AND description = '请假类型';

UPDATE sys_dict
SET name = 'Nghỉ việc riêng',
    description = 'Loại nghỉ phép'
WHERE id = 64
  AND type = 'oa_leave_type'
  AND name = '事假'
  AND description = '请假类型';

UPDATE sys_dict
SET name = 'Nghỉ bù',
    description = 'Loại nghỉ phép'
WHERE id = 65
  AND type = 'oa_leave_type'
  AND name = '调休'
  AND description = '请假类型';

UPDATE sys_dict
SET name = 'Nghỉ cưới',
    description = 'Loại nghỉ phép'
WHERE id = 66
  AND type = 'oa_leave_type'
  AND name = '婚假'
  AND description = '请假类型';

UPDATE sys_dict
SET name = 'Log truy cập',
    description = 'Loại log'
WHERE id = 67
  AND type = 'sys_log_type'
  AND name = '接入日志'
  AND description = '日志类型';

UPDATE sys_dict
SET name = 'Log ngoại lệ',
    description = 'Loại log'
WHERE id = 68
  AND type = 'sys_log_type'
  AND name = '异常日志'
  AND description = '日志类型';

UPDATE sys_dict
SET name = 'Quy trình xin nghỉ',
    description = 'Loại quy trình'
WHERE id = 69
  AND type = 'act_type'
  AND name = '请假流程'
  AND description = '流程类型';

UPDATE sys_dict
SET name = 'Quy trình kiểm thử phê duyệt',
    description = 'Loại quy trình'
WHERE id = 70
  AND type = 'act_type'
  AND name = '审批测试流程'
  AND description = '流程类型';

UPDATE sys_dict
SET name = 'Danh mục 1',
    description = 'Danh mục quy trình'
WHERE id = 71
  AND type = 'act_category'
  AND name = '分类1'
  AND description = '流程分类';

UPDATE sys_dict
SET name = 'Danh mục 2',
    description = 'Danh mục quy trình'
WHERE id = 72
  AND type = 'act_category'
  AND name = '分类2'
  AND description = '流程分类';

UPDATE sys_dict
SET name = 'CRUD',
    description = 'Danh mục sinh mã'
WHERE id = 73
  AND type = 'gen_category'
  AND name = '增删改查'
  AND description = '代码生成分类';

UPDATE sys_dict
SET name = 'CRUD (gồm bảng con)',
    description = 'Danh mục sinh mã'
WHERE id = 74
  AND type = 'gen_category'
  AND name = '增删改查（包含从表）'
  AND description = '代码生成分类';

UPDATE sys_dict
SET name = 'Cấu trúc cây',
    description = 'Danh mục sinh mã'
WHERE id = 75
  AND type = 'gen_category'
  AND name = '树结构'
  AND description = '代码生成分类';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 76
  AND type = 'gen_query_type'
  AND name = '='
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 77
  AND type = 'gen_query_type'
  AND name = '!='
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 78
  AND type = 'gen_query_type'
  AND name = '&gt;'
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 79
  AND type = 'gen_query_type'
  AND name = '&lt;'
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 80
  AND type = 'gen_query_type'
  AND name = 'Between'
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 81
  AND type = 'gen_query_type'
  AND name = 'Like'
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 82
  AND type = 'gen_query_type'
  AND name = 'Left Like'
  AND description = '查询方式';

UPDATE sys_dict
SET description = 'Phương thức truy vấn'
WHERE id = 83
  AND type = 'gen_query_type'
  AND name = 'Right Like'
  AND description = '查询方式';

UPDATE sys_dict
SET name = 'Ô văn bản',
    description = 'Phương án sinh trường'
WHERE id = 84
  AND type = 'gen_show_type'
  AND name = '文本框'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Vùng văn bản',
    description = 'Phương án sinh trường'
WHERE id = 85
  AND type = 'gen_show_type'
  AND name = '文本域'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Danh sách thả xuống',
    description = 'Phương án sinh trường'
WHERE id = 86
  AND type = 'gen_show_type'
  AND name = '下拉框'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Hộp kiểm',
    description = 'Phương án sinh trường'
WHERE id = 87
  AND type = 'gen_show_type'
  AND name = '复选框'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Nút chọn',
    description = 'Phương án sinh trường'
WHERE id = 88
  AND type = 'gen_show_type'
  AND name = '单选框'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Chọn ngày',
    description = 'Phương án sinh trường'
WHERE id = 89
  AND type = 'gen_show_type'
  AND name = '日期选择'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Chọn người',
    description = 'Phương án sinh trường'
WHERE id = 90
  AND type = 'gen_show_type'
  AND name = '人员选择'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Chọn phòng ban',
    description = 'Phương án sinh trường'
WHERE id = 91
  AND type = 'gen_show_type'
  AND name = '部门选择'
  AND description = '字段生成方案';

UPDATE sys_dict
SET name = 'Chọn khu vực',
    description = 'Phương án sinh trường'
WHERE id = 92
  AND type = 'gen_show_type'
  AND name = '区域选择'
  AND description = '字段生成方案';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 93
  AND type = 'gen_java_type'
  AND name = 'String'
  AND description = 'Java类型';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 94
  AND type = 'gen_java_type'
  AND name = 'Long'
  AND description = 'Java类型';

UPDATE sys_dict
SET name = 'Chỉ tầng lưu trữ',
    description = 'Danh mục sinh mã'
WHERE id = 95
  AND type = 'gen_category'
  AND name = '仅持久层'
  AND description = '代码生成分类';

UPDATE sys_dict
SET name = 'Nam',
    description = 'Giới tính'
WHERE id = 96
  AND type = 'sex'
  AND name = '男'
  AND description = '性别';

UPDATE sys_dict
SET name = 'Nữ',
    description = 'Giới tính'
WHERE id = 97
  AND type = 'sex'
  AND name = '女'
  AND description = '性别';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 98
  AND type = 'gen_java_type'
  AND name = 'Integer'
  AND description = 'Java类型';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 99
  AND type = 'gen_java_type'
  AND name = 'Double'
  AND description = 'Java类型';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 100
  AND type = 'gen_java_type'
  AND name = 'Date'
  AND description = 'Java类型';

UPDATE sys_dict
SET description = 'Kiểu Java'
WHERE id = 104
  AND type = 'gen_java_type'
  AND name = 'Custom'
  AND description = 'Java类型';

UPDATE sys_dict
SET name = 'Thông báo cuộc họp',
    description = 'Loại thông báo'
WHERE id = 105
  AND type = 'oa_notify_type'
  AND name = '会议通告'
  AND description = '通知通告类型';

UPDATE sys_dict
SET name = 'Thông báo khen thưởng/kỷ luật',
    description = 'Loại thông báo'
WHERE id = 106
  AND type = 'oa_notify_type'
  AND name = '奖惩通告'
  AND description = '通知通告类型';

UPDATE sys_dict
SET name = 'Thông báo hoạt động',
    description = 'Loại thông báo'
WHERE id = 107
  AND type = 'oa_notify_type'
  AND name = '活动通告'
  AND description = '通知通告类型';

UPDATE sys_dict
SET name = 'Bản nháp',
    description = 'Trạng thái thông báo'
WHERE id = 108
  AND type = 'oa_notify_status'
  AND name = '草稿'
  AND description = '通知通告状态';

UPDATE sys_dict
SET name = 'Phát hành',
    description = 'Trạng thái thông báo'
WHERE id = 109
  AND type = 'oa_notify_status'
  AND name = '发布'
  AND description = '通知通告状态';

UPDATE sys_dict
SET name = 'Chưa đọc',
    description = 'Trạng thái thông báo'
WHERE id = 110
  AND type = 'oa_notify_read'
  AND name = '未读'
  AND description = '通知通告状态';

UPDATE sys_dict
SET name = 'Đã đọc',
    description = 'Trạng thái thông báo'
WHERE id = 111
  AND type = 'oa_notify_read'
  AND name = '已读'
  AND description = '通知通告状态';

UPDATE sys_dict
SET name = 'Bản nháp',
    description = 'Trạng thái thông báo'
WHERE id = 112
  AND type = 'oa_notify_status'
  AND name = '草稿'
  AND description = '通知通告状态';

UPDATE sys_dict
SET name = 'Xóa',
    description = 'Cờ xóa'
WHERE id = 113
  AND type = 'del_flag'
  AND name = '删除'
  AND description = '删除标记';

UPDATE sys_dict
SET name = 'Lập trình',
    description = 'Sở thích'
WHERE id = 121
  AND type = 'hobby'
  AND name = '编码'
  AND description = '爱好';

UPDATE sys_dict
SET name = 'Hội họa',
    description = 'Sở thích'
WHERE id = 122
  AND type = 'hobby'
  AND name = '绘画'
  AND description = '爱好';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 123
  AND type = 'java_type'
  AND name = 'Integer'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 124
  AND type = 'java_type'
  AND name = 'Long'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 125
  AND type = 'java_type'
  AND name = 'Float'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 126
  AND type = 'java_type'
  AND name = 'Double'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 127
  AND type = 'java_type'
  AND name = 'BigDecimal'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 128
  AND type = 'java_type'
  AND name = 'Boolean'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 129
  AND type = 'java_type'
  AND name = 'String'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET description = 'Kiểu dữ liệu Java'
WHERE id = 130
  AND type = 'java_type'
  AND name = 'Date'
  AND description = 'Java数据类型';

UPDATE sys_dict
SET name = 'Ô văn bản',
    description = 'Kiểu hiển thị trang'
WHERE id = 131
  AND type = 'page_type'
  AND name = '文本框'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Danh sách thả xuống',
    description = 'Kiểu hiển thị trang'
WHERE id = 132
  AND type = 'page_type'
  AND name = '下拉框'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Số',
    description = 'Kiểu hiển thị trang'
WHERE id = 133
  AND type = 'page_type'
  AND name = '数值'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Ngày',
    description = 'Kiểu hiển thị trang'
WHERE id = 134
  AND type = 'page_type'
  AND name = '日期'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Vùng văn bản',
    description = 'Kiểu hiển thị trang'
WHERE id = 135
  AND type = 'page_type'
  AND name = '文本域'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Văn bản phong phú',
    description = 'Kiểu hiển thị trang'
WHERE id = 136
  AND type = 'page_type'
  AND name = '富文本'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Tải một ảnh',
    description = 'Kiểu hiển thị trang'
WHERE id = 137
  AND type = 'page_type'
  AND name = '上传图片【单文件】'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Trường ẩn',
    description = 'Kiểu hiển thị trang'
WHERE id = 138
  AND type = 'page_type'
  AND name = '隐藏域'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Không hiển thị',
    description = 'Kiểu hiển thị trang'
WHERE id = 139
  AND type = 'page_type'
  AND name = '不显示'
  AND description = '页面显示类型';

UPDATE sys_dict
SET name = 'Truyện dành cho nam',
    description = 'Định hướng tác phẩm'
WHERE id = 140
  AND type = 'work_direction'
  AND name = '男频'
  AND description = '作品方向';

UPDATE sys_dict
SET name = 'Truyện dành cho nữ',
    description = 'Định hướng tác phẩm'
WHERE id = 141
  AND type = 'work_direction'
  AND name = '女频'
  AND description = '作品方向';

-- Vị trí đề xuất được thêm ở các phiên bản sau và không có id cố định trong mọi cơ sở dữ liệu.
UPDATE sys_dict SET name = 'Ảnh trình chiếu', description = 'Loại đề xuất tác phẩm'
WHERE type = 'book_rec_type' AND value = '0' AND name = '轮播图' AND description = '小说推荐类型';
UPDATE sys_dict SET name = 'Khu tác phẩm đầu trang', description = 'Loại đề xuất tác phẩm'
WHERE type = 'book_rec_type' AND value = '1' AND name = '顶部小说栏' AND description = '小说推荐类型';
UPDATE sys_dict SET name = 'Đề cử trong tuần', description = 'Loại đề xuất tác phẩm'
WHERE type = 'book_rec_type' AND value = '2' AND name = '本周强推' AND description = '小说推荐类型';
UPDATE sys_dict SET name = 'Đề xuất phổ biến', description = 'Loại đề xuất tác phẩm'
WHERE type = 'book_rec_type' AND value = '3' AND name = '热门推荐' AND description = '小说推荐类型';
UPDATE sys_dict SET name = 'Đề xuất chọn lọc', description = 'Loại đề xuất tác phẩm'
WHERE type = 'book_rec_type' AND value = '4' AND name = '精品推荐' AND description = '小说推荐类型';

-- Quyền dữ liệu mặc định.
UPDATE sys_data_perm SET name = 'Xem toàn bộ dữ liệu người dùng', module_name = 'Quản lý người dùng'
WHERE id = 210 AND name = '查看用户表全部数据' AND module_name = '用户管理';
UPDATE sys_data_perm SET name = 'Xem dữ liệu người dùng thuộc phòng ban cấp dưới', module_name = 'Quản lý người dùng'
WHERE id = 211 AND name = '查看用户表下级部门数据' AND module_name = '用户管理';
UPDATE sys_data_perm SET name = 'Xem dữ liệu người dùng thuộc phòng ban hiện tại', module_name = 'Quản lý người dùng'
WHERE id = 212 AND name = '查看用户表本部门数据' AND module_name = '用户管理';
UPDATE sys_data_perm SET name = 'Xem dữ liệu người dùng cá nhân', module_name = 'Quản lý người dùng'
WHERE id = 213 AND name = '查看用户表个人数据' AND module_name = '用户管理';
UPDATE sys_data_perm SET name = 'Xem đơn hàng của phòng ban cấp dưới', module_name = 'Quản lý đơn hàng'
WHERE id = 214 AND name = '查看下级部门订单数据' AND module_name = '订单管理';
UPDATE sys_data_perm SET name = 'Xem đơn hàng của phòng ban hiện tại', module_name = 'Quản lý đơn hàng'
WHERE id = 215 AND name = '查看本部门订单数据' AND module_name = '订单管理';

-- Dữ liệu mẫu quản trị và danh mục tin tức.
UPDATE sys_dept SET name = 'Phòng kiểm thử' WHERE dept_id = 13 AND name = '测试部';
UPDATE sys_dept SET name = 'Nhóm kiểm thử 1' WHERE dept_id = 14 AND name = '测试一部';
UPDATE sys_dept SET name = 'Nhóm kiểm thử 2' WHERE dept_id = 15 AND name = '测试二部';
UPDATE sys_dept SET name = 'Nhóm kiểm thử 3' WHERE dept_id = 16 AND name = '测试三部';
UPDATE news_category SET name = 'Ngành nghề' WHERE id = 1 AND name = '行业';
UPDATE news_category SET name = 'Tin tức' WHERE id = 3 AND name = '资讯';
UPDATE news SET cat_name = 'Ngành nghề' WHERE id = 1 AND cat_id = 1 AND cat_name = '行业';
UPDATE news SET source_name = 'Không rõ' WHERE id = 1 AND source_name = '未知';
UPDATE news
SET title = 'Yuewen ra mắt hợp đồng tùy chọn: phân cấp bản quyền, miễn phí hoặc trả phí'
WHERE id = 1
  AND title = '阅文推“单本可选新合同”：授权分级、免费或付费自选';
UPDATE news
SET content = 'Yuewen ra mắt hợp đồng tùy chọn: phân cấp bản quyền, miễn phí hoặc trả phí'
WHERE id = 1
  AND content = '阅文推“单本可选新合同”：授权分级、免费或付费自选';
UPDATE news SET cat_name = 'Tin tức' WHERE id = 2 AND cat_id = 3 AND cat_name = '资讯';
UPDATE news SET source_name = 'Quanmeipai' WHERE id = 2 AND source_name = '全媒派公众号';
UPDATE news
SET title = 'Truyện AI âm thầm phổ biến: sức sáng tạo của con người đã bị AI sao chép?'
WHERE id = 2
  AND title = 'AI小说悄然流行：人类特有的创作力，已经被AI复制？';
UPDATE news
SET content = 'Truyện AI âm thầm phổ biến: sức sáng tạo của con người đã bị AI sao chép?'
WHERE id = 2
  AND content = 'AI小说悄然流行：人类特有的创作力，已经被AI复制？';
UPDATE sys_role SET role_name = 'Vai trò siêu quản trị', remark = 'Có quyền cao nhất'
WHERE role_id = 1 AND role_name = '超级用户角色' AND remark = '拥有最高权限';
UPDATE sys_user SET name = 'Siêu quản trị viên'
WHERE user_id = 1 AND name = '超级管理员';

UPDATE sys_user SET province = 'Bắc Kinh'
WHERE user_id = 1 AND province = '北京市';

UPDATE sys_user SET city = 'Thành phố Bắc Kinh'
WHERE user_id = 1 AND city = '北京市市辖区';

UPDATE sys_user SET district = 'Quận Đông Thành'
WHERE user_id = 1 AND district = '东城区';
