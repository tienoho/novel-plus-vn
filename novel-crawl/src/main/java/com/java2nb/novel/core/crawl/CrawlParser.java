package com.java2nb.novel.core.crawl;

import com.java2nb.novel.core.utils.RandomBookInfoUtil;
import com.java2nb.novel.core.utils.StringUtil;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.core.security.RichTextSanitizer;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.CrawlSingleTask;
import com.java2nb.novel.utils.Constants;
import com.java2nb.novel.utils.CrawlHttpClient;
import io.github.xxyopen.util.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ phân tích dữ liệu thu thập
 *
 * @author Administrator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlParser {

    private final IdWorker ID_WORKER = IdWorker.INSTANCE;

    private final CrawlHttpClient crawlHttpClient;

    private final StringRedisTemplate stringRedisTemplate;

    private final Messages messages;

    private final RichTextSanitizer richTextSanitizer;

    /**
     * Khóa bộ nhớ đệm số chương đã thu thập theo nguồn
     */
    private static final String CRAWL_SOURCE_CHAPTER_COUNT_CACHE_KEY = "crawlSource:chapterCount:";

    /**
     * Tiến độ tác vụ thu thập
     */
    private final Map<Long, Integer> crawlTaskProgress = new HashMap<>();

    /**
     * Lấy tiến độ tác vụ thu thập
     */
    public Integer getCrawlTaskProgress(Long taskId) {
        return crawlTaskProgress.get(taskId);
    }

    /**
     * Xóa tiến độ tác vụ thu thập
     */
    public void removeCrawlTaskProgress(Long taskId) {
        crawlTaskProgress.remove(taskId);
    }

    /**
     * Lấy số chương đã thu thập theo nguồn
     */
    public Long getCrawlSourceChapterCount(Integer sourceId) {
        return Optional.ofNullable(
            stringRedisTemplate.opsForValue().get(CRAWL_SOURCE_CHAPTER_COUNT_CACHE_KEY + sourceId)).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public void parseBook(RuleBean ruleBean, String bookId, CrawlBookHandler handler)
        throws InterruptedException {
        Book book = new Book();
        String bookDetailUrl = ruleBean.getBookDetailUrl().replace("{bookId}", bookId);
        String bookDetailHtml = crawlHttpClient.get(bookDetailUrl, ruleBean.getCharset());
        if (bookDetailHtml != null) {
            Pattern bookNamePatten = PatternFactory.getPattern(ruleBean.getBookNamePatten());
            Matcher bookNameMatch = bookNamePatten.matcher(bookDetailHtml);
            boolean isFindBookName = bookNameMatch.find();
            if (isFindBookName) {
                String bookName = bookNameMatch.group(1);
                //Gán tên truyện
                bookName = richTextSanitizer.sanitizeText(bookName);
                book.setBookName(bookName);
                Pattern authorNamePatten = PatternFactory.getPattern(ruleBean.getAuthorNamePatten());
                Matcher authorNameMatch = authorNamePatten.matcher(bookDetailHtml);
                boolean isFindAuthorName = authorNameMatch.find();
                if (isFindAuthorName) {
                    String authorName = authorNameMatch.group(1);
                    //Gán tên tác giả
                    book.setAuthorName(richTextSanitizer.sanitizeText(authorName));
                    if (StringUtils.isNotBlank(ruleBean.getPicUrlPatten())) {
                        Pattern picUrlPatten = PatternFactory.getPattern(ruleBean.getPicUrlPatten());
                        Matcher picUrlMatch = picUrlPatten.matcher(bookDetailHtml);
                        boolean isFindPicUrl = picUrlMatch.find();
                        if (isFindPicUrl) {
                            String picUrl = picUrlMatch.group(1);
                            if (StringUtils.isNotBlank(picUrl) && StringUtils.isNotBlank(ruleBean.getPicUrlPrefix())) {
                                picUrl = ruleBean.getPicUrlPrefix() + picUrl;
                            }
                            //Gán đường dẫn ảnh bìa
                            book.setPicUrl(picUrl);
                        }
                    }
                    if (StringUtils.isNotBlank(ruleBean.getScorePatten())) {
                        Pattern scorePatten = PatternFactory.getPattern(ruleBean.getScorePatten());
                        Matcher scoreMatch = scorePatten.matcher(bookDetailHtml);
                        boolean isFindScore = scoreMatch.find();
                        if (isFindScore) {
                            String score = scoreMatch.group(1);
                            //Gán điểm số
                            book.setScore(Float.parseFloat(score));
                        }
                    }
                    if (StringUtils.isNotBlank(ruleBean.getVisitCountPatten())) {
                        Pattern visitCountPatten = PatternFactory.getPattern(ruleBean.getVisitCountPatten());
                        Matcher visitCountMatch = visitCountPatten.matcher(bookDetailHtml);
                        boolean isFindVisitCount = visitCountMatch.find();
                        if (isFindVisitCount) {
                            String visitCount = visitCountMatch.group(1);
                            //Gán lượt xem
                            book.setVisitCount(Long.parseLong(visitCount));
                        }
                    }

                    String desc = bookDetailHtml.substring(
                        bookDetailHtml.indexOf(ruleBean.getDescStart()) + ruleBean.getDescStart().length());
                    desc = desc.substring(0, desc.indexOf(ruleBean.getDescEnd()));
                    //Loại bỏ thẻ đặc biệt khỏi phần giới thiệu
                    desc = desc.replaceAll("<a[^<]+</a>", "")
                        .replaceAll("<font[^<]+</font>", "")
                        .replaceAll("<p>\\s*</p>", "")
                        .replaceAll("<p>", "")
                        .replaceAll("</p>", "<br/>");
                    // Lọc phần giới thiệu truyện
                    String filterDesc = ruleBean.getFilterDesc();
                    if (StringUtils.isNotBlank(filterDesc)) {
                        String[] filterRules = filterDesc.replace("\r\n", "\n").split("\n");
                        for (String filterRule : filterRules) {
                            if (StringUtils.isNotBlank(filterRule)) {
                                desc = desc.replaceAll(filterRule, "");
                            }
                        }
                    }
                    // Xóa khoảng trắng đầu và cuối phần giới thiệu
                    desc = desc.trim();
                    // Xóa tên truyện dư thừa ở cuối phần giới thiệu
                    if (desc.endsWith(bookName)) {
                        desc = desc.substring(0, desc.length() - bookName.length());
                    }
                    //Gán phần giới thiệu truyện
                    book.setBookDesc(richTextSanitizer.sanitize(desc));
                    if (StringUtils.isNotBlank(ruleBean.getStatusPatten())) {
                        Pattern bookStatusPatten = PatternFactory.getPattern(ruleBean.getStatusPatten());
                        Matcher bookStatusMatch = bookStatusPatten.matcher(bookDetailHtml);
                        boolean isFindBookStatus = bookStatusMatch.find();
                        if (isFindBookStatus) {
                            String bookStatus = bookStatusMatch.group(1);
                            if (ruleBean.getBookStatusRule().get(bookStatus) != null) {
                                //Gán trạng thái cập nhật
                                book.setBookStatus(ruleBean.getBookStatusRule().get(bookStatus));
                            }
                        }
                    }

                    if (StringUtils.isNotBlank(ruleBean.getUpadateTimePatten()) && StringUtils.isNotBlank(
                        ruleBean.getUpadateTimeFormatPatten())) {
                        Pattern updateTimePatten = PatternFactory.getPattern(ruleBean.getUpadateTimePatten());
                        Matcher updateTimeMatch = updateTimePatten.matcher(bookDetailHtml);
                        boolean isFindUpdateTime = updateTimeMatch.find();
                        if (isFindUpdateTime) {
                            String updateTime = updateTimeMatch.group(1);
                            //Gán thời gian cập nhật
                            try {
                                book.setLastIndexUpdateTime(
                                    new SimpleDateFormat(ruleBean.getUpadateTimeFormatPatten()).parse(updateTime));
                            } catch (ParseException e) {
                                log.error(messages.get("crawl.log.updateTimeParseFailed"), e);
                            }

                        }
                    }

                }
                if (book.getVisitCount() == null && book.getScore() != null) {
                    //Tạo ngẫu nhiên lượt xem dựa trên điểm số
                    book.setVisitCount(RandomBookInfoUtil.getVisitCountByScore(book.getScore()));
                } else if (book.getVisitCount() != null && book.getScore() == null) {
                    //Tạo ngẫu nhiên điểm số dựa trên lượt xem
                    book.setScore(RandomBookInfoUtil.getScoreByVisitCount(book.getVisitCount()));
                } else if (book.getVisitCount() == null) {
                    //Nếu cả hai đều thiếu, dùng giá trị cố định
                    book.setVisitCount(Constants.VISIT_COUNT_DEFAULT);
                    book.setScore(6.5f);
                }
            }
        }
        handler.handle(book);
    }

    public boolean parseBookIndexAndContent(String sourceBookId, Book book, RuleBean ruleBean, Integer sourceId,
        Map<Integer, BookIndex> existBookIndexMap, CrawlBookChapterHandler handler, CrawlSingleTask task)
        throws InterruptedException {

        if (task != null) {
            // Bắt đầu thu thập
            crawlTaskProgress.put(task.getId(), 0);
        }

        Date currentDate = new Date();

        List<BookIndex> indexList = new ArrayList<>();
        List<BookContent> contentList = new ArrayList<>();
        //Đọc mục lục
        String indexListUrl = ruleBean.getBookIndexUrl().replace("{bookId}", sourceBookId);
        String indexListHtml = crawlHttpClient.get(indexListUrl, ruleBean.getCharset());

        if (indexListHtml != null) {
            if (StringUtils.isNotBlank(ruleBean.getBookIndexStart())) {
                indexListHtml = indexListHtml.substring(
                    indexListHtml.indexOf(ruleBean.getBookIndexStart()) + ruleBean.getBookIndexStart().length());
            }

            Pattern indexIdPatten = PatternFactory.getPattern(ruleBean.getIndexIdPatten());
            Matcher indexIdMatch = indexIdPatten.matcher(indexListHtml);

            Pattern indexNamePatten = PatternFactory.getPattern(ruleBean.getIndexNamePatten());
            Matcher indexNameMatch = indexNamePatten.matcher(indexListHtml);

            boolean isFindIndex = indexIdMatch.find() & indexNameMatch.find();

            int indexNum = 0;

            //Tổng số chữ
            int totalWordCount = book.getWordCount() == null ? 0 : book.getWordCount();

            while (isFindIndex) {

                BookIndex hasIndex = existBookIndexMap.get(indexNum);
                String indexName = indexNameMatch.group(1);

                if (hasIndex == null || !StringUtils.deleteWhitespace(hasIndex.getIndexName())
                    .equals(StringUtils.deleteWhitespace(indexName))) {

                    String sourceIndexId = indexIdMatch.group(1);
                    String bookContentUrl = ruleBean.getBookContentUrl();
                    int calStart = bookContentUrl.indexOf("{cal_");
                    if (calStart != -1) {
                        //URL trang nội dung cần được tính toán
                        String calStr = bookContentUrl.substring(calStart,
                            calStart + bookContentUrl.substring(calStart).indexOf("}"));
                        String[] calArr = calStr.split("_");
                        int calType = Integer.parseInt(calArr[1]);
                        if (calType == 1) {
                            ///{cal_1_1_3}_{bookId}/{indexId}.html
                            //Quy tắc tính thứ nhất: bỏ y ký tự cuối của tham số thứ x
                            int x = Integer.parseInt(calArr[2]);
                            int y = Integer.parseInt(calArr[3]);
                            String calResult;
                            if (x == 1) {
                                calResult = sourceBookId.substring(0, sourceBookId.length() - y);
                            } else {
                                calResult = sourceIndexId.substring(0, sourceBookId.length() - y);
                            }

                            if (calResult.isEmpty()) {
                                calResult = "0";

                            }

                            bookContentUrl = bookContentUrl.replace(calStr + "}", calResult);
                        }

                    }

                    String contentUrl = bookContentUrl.replace("{bookId}", sourceBookId)
                        .replace("{indexId}", sourceIndexId);

                    //Truy vấn nội dung chương
                    String contentHtml = crawlHttpClient.get(contentUrl, ruleBean.getCharset());
                    if (contentHtml != null && !contentHtml.contains("正在手打中")) {
                        String content = contentHtml.substring(
                            contentHtml.indexOf(ruleBean.getContentStart()) + ruleBean.getContentStart().length());
                        content = content.substring(0, content.indexOf(ruleBean.getContentEnd()));
                        // Lọc nội dung truyện
                        String filterContent = ruleBean.getFilterContent();
                        if (StringUtils.isNotBlank(filterContent)) {
                            String[] filterRules = filterContent.replace("\r\n", "\n").split("\n");
                            for (String filterRule : filterRules) {
                                if (StringUtils.isNotBlank(filterRule)) {
                                    content = content.replaceAll(filterRule, "");
                                }
                            }
                        }
                        // Xóa mọi ký tự xuống dòng ở cuối nội dung
                        content = removeTrailingBrTags(content);
                        content = richTextSanitizer.sanitize(content);
                        //Thêm mục lục và nội dung chương
                        BookIndex bookIndex = new BookIndex();
                        bookIndex.setIndexName(richTextSanitizer.sanitizeText(indexName));
                        bookIndex.setIndexNum(indexNum);
                        int wordCount = StringUtil.getStrValidWordCount(content);
                        bookIndex.setWordCount(wordCount);
                        indexList.add(bookIndex);

                        BookContent bookContent = new BookContent();
                        bookContent.setContent(content);
                        contentList.add(bookContent);

                        if (hasIndex != null) {
                            //Cập nhật chương
                            bookIndex.setId(hasIndex.getId());
                            bookContent.setIndexId(hasIndex.getId());

                            // Tính tổng số chữ.
                            totalWordCount = (totalWordCount + wordCount - hasIndex.getWordCount());
                        } else {
                            //Thêm chương
                            //Gán mục lục và nội dung chương
                            Long indexId = ID_WORKER.nextId();
                            bookIndex.setId(indexId);
                            bookIndex.setBookId(book.getId());

                            bookIndex.setCreateTime(currentDate);

                            bookContent.setIndexId(indexId);

                            // Tính tổng số chữ.
                            totalWordCount += wordCount;
                        }
                        bookIndex.setUpdateTime(currentDate);

                        if (task != null) {
                            // Cập nhật tiến độ tác vụ thu thập từng truyện
                            crawlTaskProgress.put(task.getId(), indexList.size());
                        }

                        // Cập nhật số chương đã thu thập theo nguồn
                        stringRedisTemplate.opsForValue().increment(CRAWL_SOURCE_CHAPTER_COUNT_CACHE_KEY + sourceId);

                    }


                }
                indexNum++;
                isFindIndex = indexIdMatch.find() & indexNameMatch.find();
            }

            if (!indexList.isEmpty()) {
                //Nếu thu thập được chương mới nhất, cập nhật thông tin chương mới nhất của truyện
                //Lấy chương mới nhất vừa thu thập
                BookIndex lastIndex = indexList.get(indexList.size() - 1);
                book.setLastIndexId(lastIndex.getId());
                book.setLastIndexName(lastIndex.getIndexName());
                book.setLastIndexUpdateTime(currentDate);

            }
            book.setWordCount(totalWordCount);
            book.setUpdateTime(currentDate);

            if (indexList.size() == contentList.size() && !indexList.isEmpty()) {

                handler.handle(new ChapterBean() {{
                    setBookIndexList(indexList);
                    setBookContentList(contentList);
                }});

                return true;

            }

        }

        handler.handle(new ChapterBean() {{
            setBookIndexList(new ArrayList<>(0));
            setBookContentList(new ArrayList<>(0));
        }});
        return false;

    }

    /**
     * Xóa mọi thẻ dạng <br> ở cuối chuỗi, cho phép nhiều kiểu khoảng trắng
     */
    public static String removeTrailingBrTags(String str) {
        return str.replaceAll("(?i)(?:\\s*<\\s*br\\s*/?\\s*>)++(?:\\s|\\u3000)*$", "");
    }

}
