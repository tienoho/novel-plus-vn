package com.java2nb.novel.service;

import io.github.xxyopen.model.page.PageBean;
import com.java2nb.novel.core.crawl.RuleBean;
import com.java2nb.novel.entity.CrawlSingleTask;
import com.java2nb.novel.entity.CrawlSource;

import java.util.List;

/**
 * @author Administrator
 */
public interface CrawlService {

    /**
     * Thêm nguồn thu thập
     * @param source Đối tượng dữ liệu nguồn thu thập được gửi lên
     * */
    void addCrawlSource(CrawlSource source);

    /**
     * Cập nhật nguồn thu thập
     * @param source
     */
    void updateCrawlSource(CrawlSource source);
    /**
     * Danh sách nguồn thu thập có phân trang
     * @param page Số trang hiện tại
     * @param pageSize Kích thước trang
     *@return Dữ liệu phân trang nguồn thu thập
     * */
    PageBean<CrawlSource> listCrawlByPage(int page, int pageSize);

    /**
     * Bật hoặc dừng trình thu thập
     * @param sourceId ID nguồn thu thập
     * @param sourceStatus Trạng thái: 0 là tắt, 1 là bật
     * */
    void openOrCloseCrawl(Integer sourceId, Byte sourceStatus);

    /**
     * Cập nhật trạng thái trình thu thập
     * @param sourceId ID nguồn thu thập
     * @param sourceStatus Trạng thái: 0 là tắt, 1 là bật
     * */
    void updateCrawlSourceStatus(Integer sourceId, Byte sourceStatus);

    /**
     * Thu thập và lưu truyện
     *
     * @param catId    ID danh mục
     * @param ruleBean Quy tắc thu thập
     * @param sourceId ID nguồn
     * @param bookId   ID truyện
     * @param task
     * @return true: thành công; false: thất bại
     */
    boolean parseBookAndSave(int catId, RuleBean ruleBean, Integer sourceId, String bookId, CrawlSingleTask task) throws InterruptedException;

    /**
     * Truy vấn danh sách nguồn theo trạng thái thu thập
     * @param sourceStatus Trạng thái: 0 là tắt, 1 là bật
     * @return Danh sách nguồn thu thập trả về
     * */
    List<CrawlSource> queryCrawlSourceByStatus(Byte sourceStatus);

    /**
     * Phân tích danh sách danh mục theo ID và quy tắc
     * @param catId ID danh mục
     * @param ruleBean Đối tượng quy tắc
     * @param sourceId ID nguồn thu thập
     */
    void parseBookList(int catId, RuleBean ruleBean, Integer sourceId);


    /**
     * Truy vấn nguồn thu thập
     * @param sourceId ID nguồn
     * @return Thông tin nguồn
     * */
    CrawlSource queryCrawlSource(Integer sourceId);

    /**
     * Thêm tác vụ thu thập từng truyện
     * @param singleTask Đối tượng thông tin tác vụ
     * */
    void addCrawlSingleTask(CrawlSingleTask singleTask);

    /**
     * Truy vấn phân trang tác vụ thu thập từng truyện
     * @param page Số trang hiện tại
     * @param pageSize Kích thước trang
     * @return Dữ liệu phân trang tác vụ thu thập từng truyện
     * */
    PageBean<CrawlSingleTask> listCrawlSingleTaskByPage(int page, int pageSize);

    /**
     * Xóa tác vụ thu thập
     * @param id ID tác vụ
     * */
    void delCrawlSingleTask(Long id);

    /**
     * Lấy tác vụ thu thập
     * @return Tác vụ thu thập
     * */
    CrawlSingleTask getCrawlSingleTask();

    /**
     * Cập nhật tác vụ thu thập từng truyện
     * @param task Tác vụ thu thập
     * @param status Trạng thái thu thập
     * */
    void updateCrawlSingleTask(CrawlSingleTask task, Byte status);

    /**
     * Lấy chi tiết quy tắc thu thập
     * @param id
     * @return
     */
    CrawlSource getCrawlSource(Integer id);

    /**
     * Truy vấn tiến độ tác vụ thu thập
     * */
    Integer getTaskProgress(Long taskId);
}
