package com.java2nb.system.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.java2nb.system.vo.UserVO;
import org.springframework.stereotype.Service;

import com.java2nb.common.domain.Tree;
import com.java2nb.system.domain.DeptDO;
import com.java2nb.system.domain.UserDO;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface SysUserService {
	UserDO get(Long id);

	List<UserDO> list(Map<String, Object> map);

	int count(Map<String, Object> map);

	int save(UserDO user);

	int update(UserDO user);

	int remove(Long userId);

	int batchremove(Long[] userIds);

	boolean exit(Map<String, Object> params);

	Set<String> listRoles(Long userId);

	int resetPwd(UserVO userVO,UserDO userDO) throws Exception;
	int adminResetPwd(UserVO userVO) throws Exception;
	Tree<DeptDO> getTree();

	/**
	 * Cập nhật thông tin cá nhân
	 * @param userDO
	 * @return
	 */
	int updatePersonal(UserDO userDO);

	/**
	 * Cập nhật ảnh cá nhân
	 * @param file ảnh
	 * @param avatar_data thông tin cắt ảnh
	 * @param userId ID người dùng
	 * @throws Exception
	 */
    Map<String, Object> updatePersonalImg(MultipartFile file, String avatar_data, Long userId) throws Exception;
}
