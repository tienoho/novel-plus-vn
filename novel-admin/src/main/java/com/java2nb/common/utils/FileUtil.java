package com.java2nb.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class FileUtil {

	public static void uploadFile(byte[] file, String filePath, String fileName) throws Exception {
		Path target = resolveUnderRoot(filePath, fileName);
		Files.createDirectories(target.getParent());
		Files.write(target, file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	public static boolean deleteFile(Path file) {
		try {
			return Files.isRegularFile(file) && Files.deleteIfExists(file);
		} catch (IOException exception) {
			return false;
		}
	}

	public static Path resolveUnderRoot(String root, String relativePath) {
		if (root == null || root.isBlank() || relativePath == null || relativePath.isBlank()) {
			throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ");
		}
		String portablePath = relativePath.replace('\\', '/');
		if (portablePath.startsWith("/") || portablePath.matches("^[A-Za-z]:/.*")) {
			throw new IllegalArgumentException("Đường dẫn tuyệt đối không được phép");
		}
		Path normalizedRoot = Path.of(root).toAbsolutePath().normalize();
		Path target = normalizedRoot.resolve(portablePath).normalize();
		if (target.equals(normalizedRoot) || !target.startsWith(normalizedRoot)) {
			throw new IllegalArgumentException("Đường dẫn tệp nằm ngoài thư mục tải lên");
		}
		return target;
	}

	public static String renameToUUID(String fileName) {
		return UUID.randomUUID() + "." + fileName.substring(fileName.lastIndexOf(".") + 1);
	}
}
