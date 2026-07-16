package com.java2nb.novel.controller;


import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.utils.Constants;
import com.java2nb.novel.core.utils.FileUtil;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.core.utils.RandomValidateCodeUtil;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.util.UUIDUtil;
import io.github.xxyopen.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Date;

/**
 * @author 11797
 */
@Controller
@RequestMapping("file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final CacheService cacheService;

    @Value("${pic.save.path}")
    private String picSavePath;

    /**
     * Tạo mã xác minh
     */
    @GetMapping(value = "getVerify")
    @SneakyThrows
    public void getVerify(HttpServletRequest request, HttpServletResponse response) {
        //Đặt loại phản hồi để trình duyệt nhận nội dung là ảnh
        response.setContentType("image/jpeg");
        //Đặt header phản hồi để trình duyệt không lưu bộ nhớ đệm
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expire", 0);
        RandomValidateCodeUtil randomValidateCode = new RandomValidateCodeUtil();
        //Xuất ảnh mã xác minh
        String randomString = randomValidateCode.genRandCodeImage(response.getOutputStream());
        //Lưu chuỗi ngẫu nhiên đã tạo vào bộ nhớ đệm
        cacheService.set(RandomValidateCodeUtil.RANDOM_CODE_KEY + ":" + IpUtil.getRealIp(request), randomString,
            60 * 5);
    }

    /**
     * Tải ảnh lên
     *
     * - Khi dùng `$.ajax`, đặt `dataType: "json"` sẽ tự thêm `Accept: application/json`, cho biết máy khách mong nhận
     *   dữ liệu định dạng JSON.
     * - `$.ajaxFileUpload` không tự thay đổi header `Accept` như `$.ajax`, kể cả khi đặt `dataType: "json"`,
     *   `$.ajaxFileUpload` vẫn không thêm `Accept: application/json`.
     *
     * Spring Boot mặc định trả JSON nhưng dùng content negotiation để chọn định dạng theo header `Accept`.
     * Nếu `Accept` chứa `application/xml` và Spring Boot hỗ trợ XML, phản hồi sẽ dùng XML.
     * Spring Boot mặc định không hỗ trợ XML; sau khi nâng Sharding-JDBC, phụ thuộc `jackson-dataformat-xml` được đưa vào và bật hỗ trợ XML,
     * Do `$.ajaxFileUpload` mặc định gửi `Accept` có `application/xml`, API tải tệp phải chỉ rõ phản hồi `application/json`.
     *
     */
    @SneakyThrows
    @ResponseBody
    @PostMapping(value = "/picUpload", produces = MediaType.APPLICATION_JSON_VALUE)
    RestResult<String> upload(@RequestParam("file") MultipartFile file) {
        Date currentDate = new Date();
        String savePath =
            Constants.LOCAL_PIC_PREFIX + DateUtils.formatDate(currentDate, "yyyy") + "/" +
                DateUtils.formatDate(currentDate, "MM") + "/" +
                DateUtils.formatDate(currentDate, "dd");
        String oriName = file.getOriginalFilename();
        assert oriName != null;
        String saveFileName = UUIDUtil.getUUID32() + oriName.substring(oriName.lastIndexOf("."));
        File saveFile = new File(picSavePath + savePath, saveFileName);
        if (!saveFile.getParentFile().exists()) {
            boolean isSuccess = saveFile.getParentFile().mkdirs();
            if (!isSuccess) {
                throw new BusinessException(ResponseStatus.FILE_DIR_MAKE_FAIL);
            }
        }
        file.transferTo(saveFile);
        if (!FileUtil.isImage(saveFile)) {
            //Tệp tải lên không phải hình ảnh
            saveFile.delete();
            throw new BusinessException(ResponseStatus.FILE_NOT_IMAGE);
        }
        ;
        return RestResult.ok(savePath + "/" + saveFileName);

    }


}
