package com.java2nb.common.controller;

import com.java2nb.system.domain.UserToken;
import com.java2nb.common.utils.Messages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import com.java2nb.common.utils.ShiroUtils;
import com.java2nb.system.domain.UserDO;

@Controller
public class BaseController {
	@Autowired
	protected Messages messages;

	public UserDO getUser() {
		return ShiroUtils.getUser();
	}

	public Long getUserId() {
		return getUser().getUserId();
	}

	public String getUsername() {
		return getUser().getUsername();
	}
}
