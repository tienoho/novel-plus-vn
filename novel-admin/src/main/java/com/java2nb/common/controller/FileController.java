package com.java2nb.common.controller;

import com.java2nb.common.config.Constant;
import com.java2nb.common.config.JnConfig;
import com.java2nb.common.domain.FileDO;
import com.java2nb.common.service.FileService;
import com.java2nb.common.utils.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tải tệp lên
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-19 16:02:20
 */
@Controller
@RequestMapping("/common/sysFile")
public class FileController extends BaseController {

    @Autowired
    private FileService sysFileService;

    @Autowired
    private JnConfig jnConfig;

    @GetMapping()
    @RequiresPermissions("common:sysFile:sysFile")
    String sysFile(Model model) {
        Map<String, Object> params = new HashMap<>(16);
        return "common/file/file";
    }

    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("common:sysFile:sysFile")
    public PageBean list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<FileDO> sysFileList = sysFileService.list(query);
        int total = sysFileService.count(query);
        PageBean pageBean = new PageBean(sysFileList, total);
        return pageBean;
    }

    @GetMapping("/add")
    @RequiresPermissions("common:sysFile:sysFile")
    String add() {
        return "common/sysFile/add";
    }

    @GetMapping("/edit")
    @RequiresPermissions("common:sysFile:sysFile")
    String edit(Long id, Model model) {
        FileDO sysFile = sysFileService.get(id);
        model.addAttribute("sysFile", sysFile);
        return "common/sysFile/edit";
    }

    /**
     * Thông tin
     */
    @RequestMapping("/info/{id}")
    @RequiresPermissions("common:sysFile:sysFile")
    public R info(@PathVariable("id") Long id) {
        FileDO sysFile = sysFileService.get(id);
        return R.ok().put("sysFile", sysFile);
    }

    /**
     * Lưu
     */
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("common:sysFile:sysFile")
    public R save(FileDO sysFile) {
        if (sysFileService.save(sysFile) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @RequestMapping("/update")
    @RequiresPermissions("common:sysFile:sysFile")
    public R update(@RequestBody FileDO sysFile) {
        sysFileService.update(sysFile);

        return R.ok();
    }

    /**
     * Xóa
     */
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("common:sysFile:sysFile")
    public R remove(Long id, HttpServletRequest request) {
        if ("test".equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        FileDO storedFile = sysFileService.get(id);
        if (storedFile == null || storedFile.getUrl() == null
            || !storedFile.getUrl().startsWith(Constant.UPLOAD_FILES_PREFIX)) {
            return R.error();
        }
        Path filePath;
        try {
            filePath = FileUtil.resolveUnderRoot(jnConfig.getUploadPath(),
                storedFile.getUrl().substring(Constant.UPLOAD_FILES_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            return R.error();
        }
        if (sysFileService.remove(id) > 0) {
            boolean b = FileUtil.deleteFile(filePath);
            if (!b) {
                return R.error(messages.get("error.fileDeletePartial"));
            }
            return R.ok();
        } else {
            return R.error();
        }
    }

    /**
     * Xóa
     */
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("common:sysFile:sysFile")
    public R remove(@RequestParam("ids[]") Long[] ids) {
        if ("test".equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        sysFileService.batchRemove(ids);
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/upload")
    @RequiresPermissions("common:sysFile:sysFile")
    R upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if ("test".equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        Date date = new Date();
        String year = DateUtils.format(date, DateUtils.YEAR_PATTERN);
        String month = DateUtils.format(date, DateUtils.MONTH_PATTERN);
        String day = DateUtils.format(date, DateUtils.DAY_PATTERN);

        String fileName = file.getOriginalFilename();
        String fileDir = year + "/" + month + "/" + day + "/";
        fileName = FileUtil.renameToUUID(fileName);
        FileDO sysFile = new FileDO(FileType.fileType(fileName), Constant.UPLOAD_FILES_PREFIX + fileDir + fileName,
            date);
        try {
            FileUtil.uploadFile(file.getBytes(), jnConfig.getUploadPath() + fileDir, fileName);
        } catch (Exception e) {
            return R.error();
        }

        if (sysFileService.save(sysFile) > 0) {
            return R.ok().put("fileName", sysFile.getUrl());
        }
        return R.error();
    }

    /**
     * Tải tệp
     */
    @RequestMapping(value = "/download")
    @RequiresPermissions("common:sysFile:sysFile")
    public void fileDownload(String filePath, String fileName, HttpServletResponse resp) throws Exception {
        String relativePath = filePath;
        if (relativePath != null && relativePath.startsWith(Constant.UPLOAD_FILES_PREFIX)) {
            relativePath = relativePath.substring(Constant.UPLOAD_FILES_PREFIX.length());
        }
        final Path realFilePath;
        try {
            realFilePath = FileUtil.resolveUnderRoot(jnConfig.getUploadPath(), relativePath);
        } catch (IllegalArgumentException exception) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!Files.isRegularFile(realFilePath)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String downloadName = fileName == null || fileName.isBlank()
            ? realFilePath.getFileName().toString() : Path.of(fileName).getFileName().toString();
        String encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        resp.setContentLengthLong(Files.size(realFilePath));

        try (InputStream in = Files.newInputStream(realFilePath); OutputStream out = resp.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
    }


}
