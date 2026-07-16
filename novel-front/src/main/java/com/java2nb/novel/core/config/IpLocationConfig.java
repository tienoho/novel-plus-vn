package com.java2nb.novel.core.config;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lớp cấu hình định vị địa chỉ IP
 *
 * @author xiongxiaoyang
 * @date 2025/6/30
 */
@Slf4j
@Configuration
public class IpLocationConfig {

    /**
     * Dùng {@link Searcher} để truy vấn IP cục bộ hiệu quả; đối tượng tra cứu trong bộ nhớ hỗ trợ đồng thời và chỉ cần khởi tạo một lần.
     *
     * <p>Phương thức tải tệp cơ sở dữ liệu ip2region.xdb vào bộ nhớ,
     * sau đó tạo {@link Searcher} an toàn luồng để định vị IP hiệu quả và đồng thời.</p>
     *
     * <p>{@link Searcher} an toàn luồng và có thể dùng làm singleton toàn cục.</p>
     *
     * <p>Cấu hình destroyMethod="close" để tự giải phóng tài nguyên khi Spring container đóng.</p>
     */
    @Bean(destroyMethod = "close")
    public Searcher searcher() throws IOException {
        // 1. Tải toàn bộ xdb từ classpath vào bộ nhớ.
        try (InputStream inputStream = new ClassPathResource("ip2region.xdb").getInputStream()) {
            File tempDbFile = File.createTempFile("ip2region", ".xdb");
            try (FileOutputStream outputStream = new FileOutputStream(tempDbFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            // Bảo đảm xóa tệp tạm khi ứng dụng thoát
            tempDbFile.deleteOnExit();
            byte[] cBuff = Searcher.loadContentFromFile(tempDbFile.getPath());

            // 2. Dùng cBuff để tạo đối tượng truy vấn hoàn toàn trong bộ nhớ.
            return Searcher.newWithBuffer(cBuff);
        }
    }

}
