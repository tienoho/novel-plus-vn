package com.java2nb.common.service;

import com.java2nb.common.domain.FileDO;

import java.util.List;
import java.util.Map;

/**
 * Tải tệp lên
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-19 16:02:20
 */
public interface FileService {
	
	FileDO get(Long id);
	
	List<FileDO> list(Map<String, Object> map);
	
	int count(Map<String, Object> map);
	
	int save(FileDO sysFile);
	
	int update(FileDO sysFile);
	
	int remove(Long id);
	
	int batchRemove(Long[] ids);

	/**
	 * Kiểm tra tệp có tồn tại hay không
	 * @param url đường dẫn lưu trong FileDO
	 * @return
	 */
    Boolean isExist(String url);
}
