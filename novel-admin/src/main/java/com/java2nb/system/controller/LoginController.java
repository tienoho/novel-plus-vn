package com.java2nb.system.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.domain.FileDO;
import com.java2nb.common.domain.Tree;
import com.java2nb.common.service.FileService;
import com.java2nb.common.utils.*;
import com.java2nb.system.domain.MenuDO;
import com.java2nb.system.domain.UserDO;
import com.java2nb.system.service.MenuService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
public class LoginController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    MenuService menuService;
    @Autowired
    FileService fileService;
    @Autowired
    Messages messages;


    @Log("Truy cập trang chủ")
    @GetMapping({"","/","/index"})
    String index(Model model) {
        List<Tree<MenuDO>> menus = menuService.listMenuTree(getUserId());
        model.addAttribute("menus", menus);
        model.addAttribute("name", getUser().getName());
        FileDO fileDO = fileService.get(getUser().getPicId());
        if (fileDO != null && fileDO.getUrl() != null) {
            if (fileService.isExist(fileDO.getUrl())) {
                model.addAttribute("picUrl", fileDO.getUrl());
            } else {
                model.addAttribute("picUrl", "/img/photo_s.jpg");
            }
        } else {
            model.addAttribute("picUrl", "/img/photo_s.jpg");
        }
        model.addAttribute("username", getUser().getUsername());
        return "index";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @Log("Đăng nhập")
    @PostMapping("/login")
    @ResponseBody
    R ajaxLogin(String username, String password,String verify,HttpServletRequest request) {

        try {
            // Lấy mã xác minh đã lưu trong phiên.
            String random = (String) request.getSession().getAttribute(RandomValidateCodeUtil.RANDOMCODEKEY);
            if (StringUtils.isBlank(verify)) {
                return R.error(messages.get("auth.captcha.required"));
            }
            if (random.equals(verify)) {
            } else {
                return R.error(messages.get("auth.captcha.invalid"));
            }
        } catch (Exception e) {
            logger.error("Không thể kiểm tra mã xác minh", e);
            return R.error(messages.get("auth.captcha.failed"));
        }
        UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(token);
            UserDO authenticatedUser = (UserDO) subject.getPrincipal();
            return R.ok().put("mustChangePassword",
                Boolean.TRUE.equals(authenticatedUser.getMustChangePassword()));
        } catch (AuthenticationException e) {
            return R.error(messages.get("auth.login.failed"));
        }
    }

    @GetMapping("/logout")
    String logout() {
        ShiroUtils.logout();
        return "redirect:/login";
    }

    @GetMapping("/main")
    String main() {
        return "main";
    }

    /** Tạo ảnh mã xác minh. */
    @GetMapping(value = "/getVerify")
    public void getVerify(HttpServletRequest request, HttpServletResponse response) {
        try {
            response.setContentType("image/jpeg"); // Trả về ảnh JPEG.
            response.setHeader("Pragma", "No-cache"); // Không lưu ảnh mã xác minh vào bộ nhớ đệm.
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expire", 0);
            RandomValidateCodeUtil randomValidateCode = new RandomValidateCodeUtil();
            randomValidateCode.getRandcode(request, response);
        } catch (Exception e) {
            logger.error("Không thể tạo mã xác minh", e);
        }
    }

}
