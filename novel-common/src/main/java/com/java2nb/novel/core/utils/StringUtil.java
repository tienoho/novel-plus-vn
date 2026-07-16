package com.java2nb.novel.core.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.*;

/**
 * @author xiongxiaoyang
 */
public class StringUtil {

    /**
     * Chuyển tên camelCase thành dạng gạch dưới viết hoa; trả chuỗi rỗng nếu đầu vào rỗng.</br>
     * Ví dụ: HelloWorld -> HELLO_WORLD
     * @param name chuỗi camelCase trước khi chuyển đổi
     * @return chuỗi dạng gạch dưới viết hoa sau chuyển đổi
     */
    public static String underscoreName(String name) {
        StringBuilder result = new StringBuilder();
        if (name != null && name.length() > 0) {
            // Chuyển ký tự đầu thành chữ hoa
            result.append(name.substring(0, 1).toUpperCase());
            // Lặp qua các ký tự còn lại
            for (int i = 1; i < name.length(); i++) {
                String s = name.substring(i, i + 1);
                // Thêm dấu gạch dưới trước chữ hoa
                if (s.equals(s.toUpperCase()) && !Character.isDigit(s.charAt(0))) {
                    result.append("_");
                }
                // Chuyển các ký tự khác thành chữ hoa
                result.append(s.toUpperCase());
            }
        }
        return result.toString();
    }

    /**
     * Chuyển tên dạng gạch dưới viết hoa thành camelCase; trả chuỗi rỗng nếu đầu vào rỗng.</br>
     * Ví dụ: HELLO_WORLD -> HelloWorld
     * @param name chuỗi dạng gạch dưới viết hoa trước khi chuyển đổi
     * @return chuỗi camelCase sau chuyển đổi
     */
    public static String camelName(String name) {
        StringBuilder result = new StringBuilder();
        // Kiểm tra nhanh
        if (name == null || name.isEmpty()) {
            // Không cần chuyển đổi
            return "";
        } else if (!name.contains("_")) {
            // Không có dấu gạch dưới, chỉ chuyển chữ cái đầu thành chữ thường
            return name.substring(0, 1).toLowerCase() + name.substring(1);
        }
        // Tách chuỗi gốc bằng dấu gạch dưới
        String camels[] = name.split("_");
        for (String camel : camels) {
            // Bỏ qua dấu gạch dưới ở đầu, cuối hoặc dấu gạch dưới kép
            if (camel.isEmpty()) {
                continue;
            }
            // Xử lý đoạn camelCase thực
            if (result.length() == 0) {
                // Đoạn camelCase đầu tiên viết thường toàn bộ
                result.append(camel.toLowerCase());
            } else {
                // Các đoạn camelCase khác viết hoa chữ cái đầu
                result.append(camel.substring(0, 1).toUpperCase());
                result.append(camel.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    /**
     * Lấy các chữ Hán hợp lệ trong chuỗi
     * */
    public static String getChineseValidWord(String origStr){

        // Có thể thay phần lớn ký tự trắng; \s khớp khoảng trắng, tab, ký tự xuống trang và các ký tự trắng khác.
        origStr = origStr.replaceAll("\\s*","");

       /* //Xóa hoàn toàn dấu câu
        origStr = origStr.replaceAll("\\pP","");*/

        //Xóa mọi ký hiệu, chỉ giữ chữ cái, chữ số và chữ Hán.
        origStr = origStr.replaceAll("[\\pP\\p{Punct}]","");

        //Loại bỏ chữ cái và chữ số
        origStr = origStr.replaceAll("[A-Za-z0-9]*","");

        return origStr;

    }

    /**
     * Đếm số từ tiếng Anh trong chuỗi
     * */
    public static int getEnglishWordCount(String origStr){
        Pattern pattern = compile("\\b\\w+\\b");
        Matcher matcher = pattern.matcher(origStr);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;

    }

    /**
     * Đếm số chữ Hán trong chuỗi
     * */
    public static int getChineseWordCount(String origStr){
        Pattern pattern = compile("[\u4e00-\u9fa5]");
        Matcher matcher = pattern.matcher(origStr);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;

    }

    /**
     * Đếm số chữ số hợp lệ trong chuỗi
     * */
    public static int getNumberWordCount(String origStr){
        Pattern pattern = compile("\\d+");
        Matcher matcher = pattern.matcher(origStr);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;

    }

    /**
     * Đếm số ký tự hợp lệ trong chuỗi
     * */
    public static int getStrValidWordCount(String origStr){
        return getChineseWordCount(origStr) + getEnglishWordCount(origStr) + getNumberWordCount(origStr);

    }

    public static void main(String[] args) {
        String str = "Xin chào Việt Nam. Tôi là lập trình viên số 1123 và đã phục vụ bạn 23 ngày. Hello World";
        System.out.println(getChineseWordCount(str));
        System.out.println(getEnglishWordCount(str));
        System.out.println(getNumberWordCount(str));
    }


}
