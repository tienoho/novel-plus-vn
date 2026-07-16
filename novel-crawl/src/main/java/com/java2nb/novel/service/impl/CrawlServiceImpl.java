package com.java2nb.novel.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.crawl.CrawlParser;
import com.java2nb.novel.core.crawl.RuleBean;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.CrawlSingleTask;
import com.java2nb.novel.entity.CrawlSource;
import com.java2nb.novel.mapper.CrawlSingleTaskDynamicSqlSupport;
import com.java2nb.novel.mapper.CrawlSingleTaskMapper;
import com.java2nb.novel.mapper.CrawlSourceDynamicSqlSupport;
import com.java2nb.novel.mapper.CrawlSourceMapper;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.CrawlService;
import com.java2nb.novel.utils.CrawlHttpClient;
import com.java2nb.novel.vo.CrawlSingleTaskVO;
import com.java2nb.novel.vo.CrawlSourceVO;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.page.builder.pagehelper.PageBuilder;
import io.github.xxyopen.util.IdWorker;
import io.github.xxyopen.util.ThreadUtil;
import io.github.xxyopen.web.exception.BusinessException;
import io.github.xxyopen.web.util.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.java2nb.novel.mapper.CrawlSourceDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * @author Administrator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlServiceImpl implements CrawlService {

    private final CrawlParser crawlParser;

    private final CrawlSourceMapper crawlSourceMapper;

    private final CrawlSingleTaskMapper crawlSingleTaskMapper;

    private final BookService bookService;

    private final IdWorker idWorker = IdWorker.INSTANCE;

    private final CrawlHttpClient crawlHttpClient;

    private final Map<Integer, Byte> crawlSourceStatusMap = new HashMap<>();

    private final Map<Integer, Set<Long>> runningCrawlThread = new HashMap<>();


    @Override
    public void addCrawlSource(CrawlSource source) {
        Date currentDate = new Date();
        source.setCreateTime(currentDate);
        source.setUpdateTime(currentDate);
        crawlSourceMapper.insertSelective(source);

    }

    @Override
    public void updateCrawlSource(CrawlSource source) {
        if (source.getId() != null) {
            Optional<CrawlSource> opt = crawlSourceMapper.selectByPrimaryKey(source.getId());
            if (opt.isPresent()) {
                CrawlSource crawlSource = opt.get();
                if (crawlSource.getSourceStatus() == (byte) 1) {
                    //Tắt
                    openOrCloseCrawl(crawlSource.getId(), (byte) 0);
                }
                Date currentDate = new Date();
                crawlSource.setUpdateTime(currentDate);
                crawlSource.setCrawlRule(source.getCrawlRule());
                crawlSource.setSourceName(source.getSourceName());
                crawlSourceMapper.updateByPrimaryKey(crawlSource);
            }
        }
    }

    @Override
    public PageBean<CrawlSource> listCrawlByPage(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        SelectStatementProvider render = select(id, sourceName, sourceStatus, createTime, updateTime)
            .from(crawlSource)
            .orderBy(updateTime.descending())
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<CrawlSource> crawlSources = crawlSourceMapper.selectMany(render);
        PageBean<CrawlSource> pageBean = PageBuilder.build(crawlSources);
        List<CrawlSourceVO> crawlSourceVOS = BeanUtil.copyList(crawlSources, CrawlSourceVO.class);
        crawlSourceVOS.forEach(crawlSource -> {
                crawlSource.setSourceStatus(
                    Optional.ofNullable(crawlSourceStatusMap.get(crawlSource.getId())).orElse((byte) 0));
                crawlSource.setChapterCount(crawlParser.getCrawlSourceChapterCount(crawlSource.getId()));
            }
        );
        pageBean.setList(crawlSourceVOS);
        return pageBean;
    }

    @SneakyThrows
    @Override
    public void openOrCloseCrawl(Integer sourceId, Byte sourceStatus) {

        // Xác định thao tác bật hoặc tắt; khi tắt, dừng toàn bộ luồng đang chạy của nguồn.
        // Khi bật, bỏ qua nếu nguồn vẫn chạy; nếu chưa chạy, tạo luồng thu thập và thêm vào runningCrawlThread
        // Cuối cùng lưu trạng thái nguồn thu thập
        if (sourceStatus == (byte) 0) {
            // Tắt
            // Dừng toàn bộ luồng đang chạy của nguồn
            Set<Long> runningCrawlThreadId = runningCrawlThread.get(sourceId);
            if (runningCrawlThreadId != null) {
                for (Long ThreadId : runningCrawlThreadId) {
                    Thread thread = ThreadUtil.findThread(ThreadId);
                    if (thread != null && thread.isAlive()) {
                        thread.interrupt();
                    }
                }
            }


        } else {
            // Bật
            Byte realSourceStatus = Optional.ofNullable(crawlSourceStatusMap.get(sourceId)).orElse((byte) 0);
            if (realSourceStatus == (byte) 0) {
                // Truy vấn quy tắc nguồn thu thập
                CrawlSource source = queryCrawlSource(sourceId);
                //Nguồn đã dừng; tạo luồng thu thập dữ liệu và thêm vào runningCrawlThread
                RuleBean ruleBean = new ObjectMapper().readValue(source.getCrawlRule(), RuleBean.class);
                Set<Long> threadIds = new HashSet<>();
                //Bắt đầu tác vụ phân tích theo danh mục
                for (int i = 1; i < 8; i++) {
                    final int catId = i;
                    Thread thread = new Thread(() -> CrawlServiceImpl.this.parseBookList(catId, ruleBean, sourceId));
                    thread.start();
                    //Thêm luồng vào bộ nhớ đệm giám sát
                    threadIds.add(thread.getId());
                }
                runningCrawlThread.put(sourceId, threadIds);
            }

        }

        // Lưu trạng thái nguồn thu thập
        crawlSourceStatusMap.put(sourceId, sourceStatus);

    }

    @Override
    public CrawlSource queryCrawlSource(Integer sourceId) {
        SelectStatementProvider render = select(CrawlSourceDynamicSqlSupport.sourceStatus,
            CrawlSourceDynamicSqlSupport.crawlRule)
            .from(crawlSource)
            .where(id, isEqualTo(sourceId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return crawlSourceMapper.selectMany(render).get(0);
    }

    @Override
    public void addCrawlSingleTask(CrawlSingleTask singleTask) {

        if (bookService.queryIsExistByBookNameAndAuthorName(singleTask.getBookName(), singleTask.getAuthorName())) {
            throw new BusinessException(ResponseStatus.BOOK_EXISTS);

        }
        singleTask.setCreateTime(new Date());
        crawlSingleTaskMapper.insertSelective(singleTask);


    }

    @Override
    public PageBean<CrawlSingleTask> listCrawlSingleTaskByPage(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        SelectStatementProvider render = select(CrawlSingleTaskDynamicSqlSupport.crawlSingleTask.allColumns())
            .from(CrawlSingleTaskDynamicSqlSupport.crawlSingleTask)
            .orderBy(CrawlSingleTaskDynamicSqlSupport.createTime.descending())
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<CrawlSingleTask> crawlSingleTasks = crawlSingleTaskMapper.selectMany(render);
        PageBean<CrawlSingleTask> pageBean = PageBuilder.build(crawlSingleTasks);
        pageBean.setList(BeanUtil.copyList(crawlSingleTasks, CrawlSingleTaskVO.class));
        for (CrawlSingleTask crawlSingleTask : pageBean.getList()) {
            if (crawlSingleTask.getTaskStatus() == 2
                && crawlParser.getCrawlTaskProgress(crawlSingleTask.getId()) != null) {
                // Nếu tác vụ đang chờ đã có tiến độ, chuyển sang trạng thái đang thu thập và gán tiến độ
                crawlSingleTask.setTaskStatus((byte) 3);
                crawlSingleTask.setCrawlChapters(crawlParser.getCrawlTaskProgress(crawlSingleTask.getId()));
                // Chỉ có một tác vụ được thu thập tại một thời điểm
                break;
            }
        }
        return pageBean;
    }

    @Override
    public void delCrawlSingleTask(Long id) {
        crawlSingleTaskMapper.deleteByPrimaryKey(id);
    }

    @Override
    public CrawlSingleTask getCrawlSingleTask() {

        List<CrawlSingleTask> list = crawlSingleTaskMapper.selectMany(
            select(CrawlSingleTaskDynamicSqlSupport.crawlSingleTask.allColumns())
                .from(CrawlSingleTaskDynamicSqlSupport.crawlSingleTask)
                .where(CrawlSingleTaskDynamicSqlSupport.taskStatus, isEqualTo((byte) 2))
                .orderBy(CrawlSingleTaskDynamicSqlSupport.createTime)
                .limit(1)
                .build()
                .render(RenderingStrategies.MYBATIS3));

        return list.size() > 0 ? list.get(0) : null;
    }

    @Override
    public void updateCrawlSingleTask(CrawlSingleTask task, Byte status) {
        byte excCount = task.getExcCount();
        excCount += 1;
        task.setExcCount(excCount);
        if (status == 1 || excCount == 5) {
            // Khi thành công hoặc đã thử 5 lần, cập nhật trạng thái cuối và dừng thu thập.
            task.setTaskStatus(status);
        }
        if (status == 1) {
            // Khi thành công, lưu số chương đã thu thập.
            task.setCrawlChapters(crawlParser.getCrawlTaskProgress(task.getId()));
        }
        crawlSingleTaskMapper.updateByPrimaryKeySelective(task);
        // Xóa tiến độ tác vụ
        crawlParser.removeCrawlTaskProgress(task.getId());

    }

    @Override
    public CrawlSource getCrawlSource(Integer id) {
        return crawlSourceMapper.selectByPrimaryKey(id).orElse(null);
    }

    @Override
    public Integer getTaskProgress(Long taskId) {
        return Optional.ofNullable(crawlParser.getCrawlTaskProgress(taskId)).orElse(0);
    }

    /**
     * Phân tích danh sách danh mục
     */
    @Override
    public void parseBookList(int catId, RuleBean ruleBean, Integer sourceId) {

        String catIdRule = ruleBean.getCatIdRule().get("catId" + catId);
        if (StringUtils.isBlank(catIdRule)) {
            return;
        }

        //Số trang hiện tại1
        int page = 1;
        int totalPage = page;

        while (page <= totalPage) {

            try {
                String catBookListUrl;
                if (StringUtils.isNotBlank(ruleBean.getBookListUrl())) {
                    // Tương thích quy tắc cũ
                    // Tạo URL danh mục
                    catBookListUrl = ruleBean.getBookListUrl()
                        .replace("{catId}", catIdRule)
                        .replace("{page}", page + "");
                } else {
                    // Quy tắc mới
                    // Tạo URL danh mục
                    catBookListUrl = catIdRule.replace("{page}", page + "");
                }
                log.info("catBookListUrl：{}", catBookListUrl);

                String bookListHtml = crawlHttpClient.get(catBookListUrl, ruleBean.getCharset());
                if (bookListHtml != null) {
                    Pattern bookIdPatten = Pattern.compile(ruleBean.getBookIdPatten());
                    Matcher bookIdMatcher = bookIdPatten.matcher(bookListHtml);
                    boolean isFindBookId = bookIdMatcher.find();
                    while (isFindBookId) {
                        try {
                            //1. Với thao tác chặn như sleep, wait, receiver hoặc accept
                            //Bắt InterruptedException để kết thúc luồng.
                            //2. Với thao tác không chặn, kiểm tra cờ ngắt để kết thúc luồng.
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }

                            String bookId = bookIdMatcher.group(1);
                            parseBookAndSave(catId, ruleBean, sourceId, bookId, null);
                        } catch (InterruptedException e) {
                            log.error(e.getMessage(), e);
                            //1. Với thao tác chặn như sleep, wait, receiver hoặc accept
                            //Bắt InterruptedException để kết thúc luồng.
                            //2. Với thao tác không chặn, kiểm tra cờ ngắt để kết thúc luồng.
                            return;
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }

                        isFindBookId = bookIdMatcher.find();
                    }

                    Pattern totalPagePatten = Pattern.compile(ruleBean.getTotalPagePatten());
                    Matcher totalPageMatcher = totalPagePatten.matcher(bookListHtml);
                    boolean isFindTotalPage = totalPageMatcher.find();
                    if (isFindTotalPage) {

                        totalPage = Integer.parseInt(totalPageMatcher.group(1));

                    }
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                //1. Với thao tác chặn như sleep, wait, receiver hoặc accept
                //Bắt InterruptedException để kết thúc luồng.
                //2. Với thao tác không chặn, kiểm tra cờ ngắt để kết thúc luồng.
                return;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            if (page >= totalPage) {
                // Sau lượt đầu, quay về trang một và chạy lượt hai; phù hợp danh sách cập nhật có ít trang.
                page = 1;
                try {
                    // Sau lượt đầu, tạm dừng một phút.
                    Thread.sleep(Duration.ofMinutes(1));
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                    //1. Với thao tác chặn như sleep, wait, receiver hoặc accept
                    //Bắt InterruptedException để kết thúc luồng.
                    //2. Với thao tác không chặn, kiểm tra cờ ngắt để kết thúc luồng.
                    return;
                }
            } else {
                page += 1;
            }
        }


    }

    @Override
    public boolean parseBookAndSave(int catId, RuleBean ruleBean, Integer sourceId, String bookId, CrawlSingleTask task)
        throws InterruptedException {

        final AtomicBoolean parseResult = new AtomicBoolean(false);

        crawlParser.parseBook(ruleBean, bookId, book -> {
            if (book.getBookName() == null || book.getAuthorName() == null) {
                return;
            }
            //Chỉ nhập truyện mới; trước hết kiểm tra truyện đã tồn tại
            Book existBook = bookService.queryBookByBookNameAndAuthorName(book.getBookName(), book.getAuthorName());
            //Nếu chưa tồn tại, đánh dấu đang nhập và chỉ cho phép nhập lại sau 30 phút
            if (existBook == null) {
                //Truyện chưa tồn tại và có thể được nhập
                book.setCatId(catId);
                //Truy vấn danh mục theo ID
                book.setCatName(bookService.queryCatNameByCatId(catId));
                if (catId == 7) {
                    //Kênh nữ
                    book.setWorkDirection((byte) 1);
                } else {
                    //Kênh nam
                    book.setWorkDirection((byte) 0);
                }
                book.setCrawlBookId(bookId);
                book.setCrawlSourceId(sourceId);
                book.setCrawlLastTime(new Date());
                book.setId(idWorker.nextId());
                //Phân tích mục lục chương
                boolean parseIndexContentResult = crawlParser.parseBookIndexAndContent(bookId, book, ruleBean, sourceId,
                    new HashMap<>(0), chapter -> {
                        bookService.saveBookAndIndexAndContent(book, chapter.getBookIndexList(),
                            chapter.getBookContentList());
                    }, task);
                parseResult.set(parseIndexContentResult);

            } else {
                // Chỉ cập nhật các trường liên quan đến thu thập.
                bookService.updateCrawlProperties(existBook.getId(), sourceId, bookId);
                parseResult.set(true);
            }
        });

        return parseResult.get();

    }

    @Override
    public void updateCrawlSourceStatus(Integer sourceId, Byte sourceStatus) {
        CrawlSource source = new CrawlSource();
        source.setId(sourceId);
        source.setSourceStatus(sourceStatus);
        crawlSourceMapper.updateByPrimaryKeySelective(source);
    }

    @Override
    public List<CrawlSource> queryCrawlSourceByStatus(Byte sourceStatus) {
        SelectStatementProvider render = select(CrawlSourceDynamicSqlSupport.id,
            CrawlSourceDynamicSqlSupport.sourceStatus, CrawlSourceDynamicSqlSupport.crawlRule)
            .from(crawlSource)
            .where(CrawlSourceDynamicSqlSupport.sourceStatus, isEqualTo(sourceStatus))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return crawlSourceMapper.selectMany(render);
    }

}
