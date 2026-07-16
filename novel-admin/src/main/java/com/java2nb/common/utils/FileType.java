package com.java2nb.common.utils;

/* author：zss
 * Ngày: 31 tháng 3 năm 2017
 * Chức năng: xác định loại theo tên tệp
 * Kiểu tham số nhận: String
 * Kiểu tham số trả về: String
 * Ghi chú: danh sách loại tệp chưa đầy đủ; bổ sung khi cần
 */
public class FileType {
	public static int fileType(String fileName) {
		if (fileName == null) {
			fileName = Messages.getDefault("error.fileNameEmpty");
			return 500;

		} else {
			// Lấy phần mở rộng tệp và chuyển thành chữ thường để so sánh
			String fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()).toLowerCase();
			// Tạo mảng loại ảnh 0
			String[] img = { "bmp", "jpg", "jpeg", "png", "tiff", "gif", "pcx", "tga", "exif", "fpx", "svg", "psd",
					"cdr", "pcd", "dxf", "ufo", "eps", "ai", "raw", "wmf" };
			for (int i = 0; i < img.length; i++) {
				if (img[i].equals(fileType)) {
					return 0;
				}
			}

			// Tạo mảng loại tài liệu 1
			String[] document = { "txt", "doc", "docx", "xls", "htm", "html", "jsp", "rtf", "wpd", "pdf", "ppt" };
			for (int i = 0; i < document.length; i++) {
				if (document[i].equals(fileType)) {
					return 1;
				}
			}
			// Tạo mảng loại video 2
			String[] video = { "mp4", "avi", "mov", "wmv", "asf", "navi", "3gp", "mkv", "f4v", "rmvb", "webm" };
			for (int i = 0; i < video.length; i++) {
				if (video[i].equals(fileType)) {
					return 2;
				}
			}
			// Tạo mảng loại âm thanh 3
			String[] music = { "mp3", "wma", "wav", "mod", "ra", "cd", "md", "asf", "aac", "vqf", "ape", "mid", "ogg",
					"m4a", "vqf" };
			for (int i = 0; i < music.length; i++) {
				if (music[i].equals(fileType)) {
					return 3;
				}
			}

		}
		//4
		return 99;
	}
}
