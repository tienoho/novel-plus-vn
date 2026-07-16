package com.java2nb.novel.core.filter;

import com.java2nb.novel.core.cache.CacheKey;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.utils.*;
import io.github.xxyopen.util.UUIDUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Bộ lọc lõi của dự án
 * @author Administrator
 */
public class NovelFilter implements Filter {

    /**
     * Đường dẫn lưu ảnh cục bộ
     * */
    private String picSavePath;

    @Override
    public void init(FilterConfig filterConfig){
        picSavePath = filterConfig.getInitParameter("picSavePath");
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;
        String requestUri = req.getRequestURI();

        //Xử lý truy cập ảnh cục bộ
        if (requestUri.contains(Constants.LOCAL_PIC_PREFIX)) {
            //Lưu bộ nhớ đệm trong 10 ngày
            resp.setDateHeader("expires", System.currentTimeMillis()+60*60*24*10*1000);
            OutputStream out = resp.getOutputStream();
            InputStream input = new FileInputStream(picSavePath + requestUri);
            byte[] b = new byte[4096];
            for (int n; (n = input.read(b)) != -1; ) {
                out.write(b, 0, n);
            }
            input.close();
            out.close();
            return;

        }


        String userMark = CookieUtil.getCookie(req,Constants.USER_CLIENT_MARK_KEY);
        if(userMark == null){
            userMark = UUIDUtil.getUUID32();
            CookieUtil.setCookie(resp,Constants.USER_CLIENT_MARK_KEY,userMark);
        }
        ThreadLocalUtil.setClientId(userMark);
        //Chọn mẫu frontend theo loại trình duyệt
        String to = req.getParameter("to");
        CacheService cacheService = SpringUtil.getBean(CacheService.class);
        if("pc".equals(to)){
            //Chuyển thẳng tới trang máy tính
            cacheService.set(CacheKey.TEMPLATE_DIR_KEY+userMark,"",60*60*24);
        }else if("mobile".equals(to)){
            //Chuyển thẳng tới trang di động
            cacheService.set(CacheKey.TEMPLATE_DIR_KEY+userMark,"mobile/",60*60*24);
        }else{
            //Tự động nhận diện trang máy tính hoặc di động
            if(BrowserUtil.isMobile(req)){
                //Truy cập từ thiết bị di động
                ThreadLocalUtil.setTemplateDir("mobile/");
            }else{
                //Truy cập từ máy tính
                ThreadLocalUtil.setTemplateDir("");
            }
        }


        filterChain.doFilter(servletRequest,servletResponse);
    }

    @Override
    public void destroy() {

    }
}
