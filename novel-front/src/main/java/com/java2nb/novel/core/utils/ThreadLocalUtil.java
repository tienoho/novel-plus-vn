package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.cache.CacheKey;
import com.java2nb.novel.core.cache.CacheService;

/**
 * Tiện ích thao tác mẫu
 * @author Administrator
 */
public class ThreadLocalUtil {

    /**
     * Lưu thư mục mẫu của luồng hiện tại
     * */
    private static final ThreadLocal<String> templateDir = new ThreadLocal<>();

    /**
     * Lưu session ID hiện tại
     * */
    private static final ThreadLocal<String> clientId = new ThreadLocal<>();

    /**
     * Đặt thư mục mẫu hiện tại
     * */
    public static void setTemplateDir(String dir){
        templateDir.set(dir);
    }

    /**
     * Lấy tiền tố đường dẫn mẫu hiện tại
     * */
    public static String getTemplateDir(){
        CacheService cacheService = SpringUtil.getBean(CacheService.class);
        String prefix = cacheService.get(CacheKey.TEMPLATE_DIR_KEY+clientId.get());
        if(prefix != null){
            return prefix;
        }
        return templateDir.get();
    }
    
    /**
     *Đặt ID máy khách của luồng hiện tạiD
     * */
    public static void setClientId(String id){
        clientId.set(id);
    }



}
