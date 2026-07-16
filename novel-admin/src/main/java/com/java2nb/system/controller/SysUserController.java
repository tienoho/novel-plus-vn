package com.java2nb.system.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.config.Constant;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.domain.Tree;
import com.java2nb.common.service.DictService;
import com.java2nb.common.utils.MD5Utils;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.system.domain.DeptDO;
import com.java2nb.system.domain.RoleDO;
import com.java2nb.system.domain.UserDO;
import com.java2nb.system.service.RoleService;
import com.java2nb.system.service.SysUserService;
import com.java2nb.system.vo.UserVO;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/sys/user")
@Controller
public class SysUserController extends BaseController {

    private String prefix = "system/user";
    @Autowired
    SysUserService userService;
    @Autowired
    RoleService roleService;
    @Autowired
    DictService dictService;

    @RequiresPermissions("sys:user:user")
    @GetMapping("")
    String user(Model model) {
        return prefix + "/user";
    }

    @GetMapping("/list")
    @ResponseBody
    PageBean list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<UserDO> sysUserList = userService.list(query);
        int total = userService.count(query);
        PageBean pageUtil = new PageBean(sysUserList, total);
        return pageUtil;
    }

    @RequiresPermissions("sys:user:add")
    @Log("Thêm người dùng")
    @GetMapping("/add")
    String add(Model model) {
        List<RoleDO> roles = roleService.list();
        model.addAttribute("roles", roles);
        return prefix + "/add";
    }

    @RequiresPermissions("sys:user:edit")
    @Log("Sửa người dùng")
    @GetMapping("/edit/{id}")
    String edit(Model model, @PathVariable("id") Long id) {
        UserDO userDO = userService.get(id);
        model.addAttribute("user", userDO);
        List<RoleDO> roles = roleService.list(id);
        model.addAttribute("roles", roles);
        return prefix + "/edit";
    }

    public static void main(String[] args) {
        System.out.println(MD5Utils.encrypt("admin", "admin"));
    }

    @RequiresPermissions("sys:user:add")
    @Log("Lưu người dùng")
    @PostMapping("/save")
    @ResponseBody
    R save(UserDO user) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        user.setPassword(MD5Utils.encrypt(user.getUsername(), user.getPassword()));
        if (userService.save(user) > 0) {
            return R.ok();
        }
        return R.error();
    }

    @RequiresPermissions("sys:user:edit")
    @Log("Cập nhật người dùng")
    @PostMapping("/update")
    @ResponseBody
    R update(UserDO user) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        if (userService.update(user) > 0) {
            return R.ok();
        }
        return R.error();
    }


    @RequiresPermissions("sys:user:edit")
    @Log("Cập nhật người dùng")
    @PostMapping("/updatePeronal")
    @ResponseBody
    R updatePeronal(UserDO user) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        if (userService.updatePersonal(user) > 0) {
            return R.ok();
        }
        return R.error();
    }


    @RequiresPermissions("sys:user:remove")
    @Log("Xóa người dùng")
    @PostMapping("/remove")
    @ResponseBody
    R remove(Long id) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        if (userService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    @RequiresPermissions("sys:user:batchRemove")
    @Log("Xóa nhiều người dùng")
    @PostMapping("/batchRemove")
    @ResponseBody
    R batchRemove(@RequestParam("ids[]") Long[] userIds) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        int r = userService.batchremove(userIds);
        if (r > 0) {
            return R.ok();
        }
        return R.error();
    }

    @PostMapping("/exit")
    @ResponseBody
    boolean exit(@RequestParam Map<String, Object> params) {
        // Nếu đã tồn tại thì không hợp lệ, trả false
        return !userService.exit(params);
    }

    @RequiresPermissions("sys:user:resetPwd")
    @Log("Yêu cầu đổi mật khẩu người dùng")
    @GetMapping("/resetPwd/{id}")
    String resetPwd(@PathVariable("id") Long userId, Model model) {

        UserDO userDO = new UserDO();
        userDO.setUserId(userId);
        model.addAttribute("user", userDO);
        return prefix + "/reset_pwd";
    }

    @Log("Gửi yêu cầu đổi mật khẩu người dùng")
    @PostMapping("/resetPwd")
    @ResponseBody
    R resetPwd(UserVO userVO) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        try {
            userService.resetPwd(userVO, getUser());
            return R.ok();
        } catch (Exception e) {
            return R.error(1, e.getMessage());
        }

    }

    @RequiresPermissions("sys:user:resetPwd")
    @Log("Quản trị viên gửi yêu cầu đổi mật khẩu người dùng")
    @PostMapping("/adminResetPwd")
    @ResponseBody
    R adminResetPwd(UserVO userVO) {
        if (Constant.DEMO_ACCOUNT.equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        try {
            userService.adminResetPwd(userVO);
            return R.ok();
        } catch (Exception e) {
            return R.error(1, e.getMessage());
        }

    }

    @GetMapping("/tree")
    @ResponseBody
    public Tree<DeptDO> tree() {
        Tree<DeptDO> tree = new Tree<DeptDO>();
        tree = userService.getTree();
        return tree;
    }

    @GetMapping("/treeView")
    String treeView() {
        return prefix + "/userTree";
    }

    @GetMapping("/personal")
    String personal(Model model) {
        UserDO userDO = userService.get(getUserId());
        model.addAttribute("user", userDO);
        model.addAttribute("hobbyList", dictService.getHobbyList(userDO));
        model.addAttribute("sexList", dictService.getSexList());
        return prefix + "/personal";
    }

    @ResponseBody
    @PostMapping("/uploadImg")
    R uploadImg(@RequestParam("avatar_file") MultipartFile file, String avatar_data, HttpServletRequest request) {
        if ("test".equals(getUsername())) {
            return R.error(1, messages.get("error.demoReadOnly"));
        }
        Map<String, Object> result = new HashMap<>();
        try {
            result = userService.updatePersonalImg(file, avatar_data, getUserId());
        } catch (Exception e) {
            return R.error(messages.get("error.avatarUpdateFailed"));
        }
        if (result != null && result.size() > 0) {
            return R.ok(result);
        } else {
            return R.error(messages.get("error.avatarUpdateFailed"));
        }
    }
}
