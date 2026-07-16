//package com.java2nb.common.utils;
//
//import org.apache.commons.lang3.Validate;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.DisposableBean;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.stereotype.Service;
//
///**
// * Lưu Spring ApplicationContext trong biến tĩnh để có thể truy cập ở mọi nơi.
// *
// */
//@Service
//@Lazy(false)
//public class SpringContextHolder implements ApplicationContextAware, DisposableBean {
//
//	private static ApplicationContext applicationContext = null;
//
//	private static Logger logger = LoggerFactory.getLogger(SpringContextHolder.class);
//
//	/**
//	 * Lấy ApplicationContext lưu trong biến tĩnh.
//	 */
//	public static ApplicationContext getApplicationContext() {
//		assertContextInjected();
//		return applicationContext;
//	}
//
//	/**
//	 * Lấy Bean từ applicationContext tĩnh và tự động ép sang kiểu đích.
//	 */
//	@SuppressWarnings("unchecked")
//	public static <T> T getBean(String name) {
//		assertContextInjected();
//		return (T) applicationContext.getBean(name);
//	}
//
//	/**
//	 * Lấy Bean từ applicationContext tĩnh và tự động ép sang kiểu đích.
//	 */
//	public static <T> T getBean(Class<T> requiredType) {
//		assertContextInjected();
//		return applicationContext.getBean(requiredType);
//	}
//
//	/**
//	 * Đặt ApplicationContext trong SpringContextHolder thành null.
//	 */
//	public static void clearHolder() {
//		if (logger.isDebugEnabled()) {
//			logger.debug("Xóa ApplicationContext khỏi SpringContextHolder: " + applicationContext);
//		}
//		applicationContext = null;
//	}
//
//	/**
//	 * Triển khai ApplicationContextAware để đưa Context vào biến tĩnh.
//	 */
//	@Override
//	public void setApplicationContext(ApplicationContext applicationContext) {
//		SpringContextHolder.applicationContext = applicationContext;
//	}
//
//	/**
//	 * Triển khai DisposableBean để dọn biến tĩnh khi Context đóng.
//	 */
//	@Override
//	public void destroy() throws Exception {
//		SpringContextHolder.clearHolder();
//	}
//
//	/**
//	 * Kiểm tra ApplicationContext không rỗng.
//	 */
//	private static void assertContextInjected() {
//		Validate.validState(applicationContext != null,
//				"Thuộc tính applicationContext chưa được inject; hãy khai báo SpringContextHolder trong applicationContext.xml.");
//	}
//}
