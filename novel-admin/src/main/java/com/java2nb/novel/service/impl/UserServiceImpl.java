package com.java2nb.novel.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.java2nb.novel.dao.UserDao;
import com.java2nb.novel.domain.UserDO;
import com.java2nb.novel.service.UserService;



@Service
public class UserServiceImpl implements UserService {
	private final UserDao userDao;

	@Autowired
	public UserServiceImpl(UserDao userDao) {
		this.userDao = userDao;
	}
	
	@Override
	public UserDO get(Long id){
		return userDao.get(id);
	}
	
	@Override
	public List<UserDO> list(Map<String, Object> map){
		return userDao.list(map);
	}
	
	@Override
	public int count(Map<String, Object> map){
		return userDao.count(map);
	}
	
	@Override
	public int save(UserDO user){
		user.setAccountBalance(0L);
		return userDao.save(user);
	}
	
	@Override
	public int update(UserDO user){
		// Số dư chỉ được thay đổi qua sổ cái; CRUD người dùng không được ghi projection này.
		user.setAccountBalance(null);
		return userDao.update(user);
	}
	
	@Override
	public int remove(Long id){
		return userDao.remove(id);
	}
	
	@Override
	public int batchRemove(Long[] ids){
		return userDao.batchRemove(ids);
	}

	@Override
	public Map<Object, Object> tableSta(Date minDate) {

		List<Map<Object, Object>> maps = userDao.tableSta(minDate);

		return maps.stream().collect(Collectors.toMap(x -> x.get("staDate"), x -> x.get("userCount")));
	}

}
