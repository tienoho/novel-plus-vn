package com.java2nb.novel.core.utils;

import lombok.SneakyThrows;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.internal.DefaultShellCallback;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ sinh mã
 *
 * @author 11797
 */
public class Generator {

    @SneakyThrows
    public static void main(String[] args) {
        //Thông tin cảnh báo trong quá trình chạy MBG
        List<String> warnings = new ArrayList<>();
        //Đọc tệp cấu hình MBG
        InputStream is = Generator.class.getResourceAsStream("/mybatis/generatorConfig.xml");
        ConfigurationParser cp = new ConfigurationParser(warnings);
        Configuration config = cp.parseConfiguration(is);
        is.close();
        //Không ghi đè mã cũ khi mã sinh ra bị trùng
        DefaultShellCallback callback = new DefaultShellCallback(false);
        //Tạo MBG
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
        //Thực thi sinh mã
        myBatisGenerator.generate(null);
        //Xuất thông tin cảnh báo
        for (String warning : warnings) {
            System.out.println(warning);
        }
    }
}
