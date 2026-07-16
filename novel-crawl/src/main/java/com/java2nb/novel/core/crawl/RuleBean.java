package com.java2nb.novel.core.crawl;

import com.java2nb.novel.utils.Constants;
import lombok.Data;

import java.util.Map;

/**
 * Bean quy tắc phân tích dữ liệu thu thập
 *
 * @author Administrator
 */
@Data
public class RuleBean {

    /**
     * Bảng mã trang web
     */
    private String charset = Constants.CRAWL_DEFAULT_CHARSET;


    /**
     * URL danh sách cập nhật truyện
     */
    private String updateBookListUrl;

    /**
     * Quy tắc URL trang danh mục
     */
    private String bookListUrl;

    private Map<String, String> catIdRule;

    private Map<String, Byte> bookStatusRule;

    private String bookIdPatten;
    private String pagePatten;
    private String totalPagePatten;
    private String bookDetailUrl;
    private String bookNamePatten;
    private String authorNamePatten;
    private String picUrlPatten;
    private String statusPatten;
    private String scorePatten;
    private String visitCountPatten;
    private String descStart;
    private String descEnd;
    private String filterDesc;
    private String upadateTimePatten;
    private String upadateTimeFormatPatten;
    private String bookIndexUrl;
    private String indexIdPatten;
    private String indexNamePatten;
    private String bookContentUrl;
    private String contentStart;
    private String contentEnd;


    private String picUrlPrefix;

    private String bookIndexStart;

    private String filterContent;


}
