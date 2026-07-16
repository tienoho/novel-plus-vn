package com.java2nb.common.service;

import com.java2nb.common.domain.DictDO;
import com.java2nb.system.domain.UserDO;

import java.util.List;
import java.util.Map;

/**
 * Bảng từ điển
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-29 18:28:07
 */
public interface DictService {
	
	DictDO get(Long id);
	
	List<DictDO> list(Map<String, Object> map);
	
	int count(Map<String, Object> map);
	
	int save(DictDO dict);
	
	int update(DictDO dict);
	
	int remove(Long id);
	
	int batchRemove(Long[] ids);

	List<DictDO> listType();
	
	String getName(String type,String value);

	/**
	 * Lấy danh sách sở thích
	 * @return
     * @param userDO
	 */
	List<DictDO> getHobbyList(UserDO userDO);

	/**
	 * Lấy danh sách giới tính
 	 * @return
	 */
	List<DictDO> getSexList();

	/**
	 * Lấy dữ liệu theo loại
	 * @param map
	 * @return
	 */
	List<DictDO> listByType(String type);

}
