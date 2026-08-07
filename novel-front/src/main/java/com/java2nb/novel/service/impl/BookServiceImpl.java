package com.java2nb.novel.service.impl;

import com.github.pagehelper.PageHelper;
import com.java2nb.novel.core.cache.CacheKey;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.core.utils.Constants;
import com.java2nb.novel.core.utils.FileUtil;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.core.utils.SimHashUtil;
import com.java2nb.novel.core.utils.SensitiveWordFilter;
import com.java2nb.novel.core.utils.StringUtil;
import com.java2nb.novel.core.security.RichTextSanitizer;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.mapper.*;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.FileService;
import com.java2nb.novel.service.LikeService;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.search.VietnameseSearchMatcher;
import com.java2nb.novel.vo.*;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.page.builder.pagehelper.PageBuilder;
import io.github.xxyopen.util.IdWorker;
import io.github.xxyopen.web.exception.BusinessException;
import io.github.xxyopen.web.util.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.DateUtils;
import org.mybatis.dynamic.sql.SortSpecification;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.QueryExpressionDSL;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.java2nb.novel.mapper.BookCategoryDynamicSqlSupport.bookCategory;
import static com.java2nb.novel.mapper.BookCommentDynamicSqlSupport.bookComment;
import static com.java2nb.novel.mapper.BookContentDynamicSqlSupport.bookContent;
import static com.java2nb.novel.mapper.BookContentDynamicSqlSupport.content;
import static com.java2nb.novel.mapper.BookDynamicSqlSupport.*;
import static com.java2nb.novel.mapper.BookDynamicSqlSupport.book;
import static com.java2nb.novel.mapper.BookIndexDynamicSqlSupport.bookIndex;
import static com.java2nb.novel.mapper.BookSettingDynamicSqlSupport.bookSetting;
import static org.mybatis.dynamic.sql.SqlBuilder.*;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * @author 11797
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {
    private static final int FUZZY_SEARCH_CANDIDATE_LIMIT = 500;
    private static final Pattern LOCAL_COVER_PATTERN = Pattern.compile(
        "^/localPic/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9]+\\.(?:jpg|jpeg|gif|png|JPG|JPEG|GIF|PNG)$");

    private final Messages messages;

    private final RichTextSanitizer richTextSanitizer;

    /**
     * Đường dẫn lưu ảnh cục bộ
     */
    @Value("${pic.save.path}")
    private String picSavePath;

    private final FrontBookSettingMapper bookSettingMapper;

    private final FrontBookMapper bookMapper;

    private final BookCategoryMapper bookCategoryMapper;

    private final BookIndexMapper bookIndexMapper;

    private final BookContentMapper bookContentMapper;

    private final FrontBookCommentMapper bookCommentMapper;

    private final FrontBookCommentReplyMapper bookCommentReplyMapper;

    private final BookContentHistoryMapper bookContentHistoryMapper;

    private final BookAuthorMapper bookAuthorMapper;

    private final CacheService cacheService;

    private final AuthorService authorService;

    private final AuthorBookCollaborationService collaborationService;

    private final FileService fileService;

    private final LikeService likeService;

    private final ChapterCommercialPolicyService chapterCommercialPolicyService;

    private final OpenAiImageModel openAiImageModel;

    private final ThreadPoolExecutor threadPoolExecutor;

    private final IdWorker idWorker = IdWorker.INSTANCE;


    @SneakyThrows
    @Override
    public Map<String, List<BookSettingVO>> listBookSettingVO() {
        List<BookSettingVO> list = cacheService.getList(CacheKey.INDEX_BOOK_SETTINGS_KEY, BookSettingVO.class);
        if (list == null || list.isEmpty()) {
            list = bookSettingMapper.listVO();
            if (list.isEmpty()) {
                //Khởi tạo cấu hình tác phẩm trang chủ nếu chưa có
                list = initIndexBookSetting();
            }
            cacheService.setObject(CacheKey.INDEX_BOOK_SETTINGS_KEY, list, 3600 * 24);
        }
        return list.stream().collect(
            Collectors.groupingBy(book -> book.getType().toString())
        );
    }


    /**
     * Khởi tạo cấu hình tác phẩm trang chủ
     */
    private List<BookSettingVO> initIndexBookSetting() {
        Date currentDate = new Date();
        List<Book> books = bookMapper.selectIdsByScoreAndRandom(Constants.INDEX_BOOK_SETTING_NUM);
        if (books.size() == Constants.INDEX_BOOK_SETTING_NUM) {
            List<BookSetting> bookSettingList = new ArrayList<>(Constants.INDEX_BOOK_SETTING_NUM);
            List<BookSettingVO> bookSettingVOList = new ArrayList<>(Constants.INDEX_BOOK_SETTING_NUM);
            for (int i = 0; i < books.size(); i++) {
                Book book = books.get(i);
                byte type;
                if (i < 4) {
                    type = 0;
                } else if (i < 14) {
                    type = 1;
                } else if (i < 19) {
                    type = 2;
                } else if (i < 25) {
                    type = 3;
                } else {
                    type = 4;
                }
                BookSettingVO bookSettingVO = new BookSettingVO();
                BookSetting bookSetting = new BookSetting();
                bookSetting.setType(type);
                bookSetting.setSort((byte) i);
                bookSetting.setBookId(book.getId());
                bookSetting.setCreateTime(currentDate);
                bookSetting.setUpdateTime(currentDate);
                bookSettingList.add(bookSetting);

                BeanUtils.copyProperties(book, bookSettingVO);
                BeanUtils.copyProperties(bookSetting, bookSettingVO);
                bookSettingVOList.add(bookSettingVO);
            }

            bookSettingMapper.delete(deleteFrom(bookSetting).build()
                .render(RenderingStrategies.MYBATIS3));
            bookSettingMapper.insertMultiple(bookSettingList);

            return bookSettingVOList;
        }
        return new ArrayList<>(0);
    }

    @Override
    public List<Book> listClickRank() {
        List<Book> result = cacheService.getList(CacheKey.INDEX_CLICK_BANK_BOOK_KEY, Book.class);
        if (result == null || result.isEmpty()) {
            result = listRank((byte) 0, 10);
            cacheService.setObject(CacheKey.INDEX_CLICK_BANK_BOOK_KEY, result, 5000);
        }
        return result;
    }

    @Override
    public List<Book> listNewRank() {
        List<Book> result = cacheService.getList(CacheKey.INDEX_NEW_BOOK_KEY, Book.class);
        if (result == null || result.isEmpty()) {
            result = listRank((byte) 1, 10);
            cacheService.setObject(CacheKey.INDEX_NEW_BOOK_KEY, result, 3600);
        }
        return result;
    }

    @Override
    public List<BookVO> listUpdateRank() {
        List<BookVO> result = cacheService.getList(CacheKey.INDEX_UPDATE_BOOK_KEY, BookVO.class);
        if (result == null || result.isEmpty()) {
            List<Book> bookPOList = listRank((byte) 2, 23);
            result = BeanUtil.copyList(bookPOList, BookVO.class);
            cacheService.setObject(CacheKey.INDEX_UPDATE_BOOK_KEY, result, 60 * 10);
        }
        return result;
    }

    @Override
    public PageBean<?> searchByPage(BookSpVO params, int page, int pageSize) {

        params.setKeyword(normalizeSearchKeyword(params.getKeyword()));

        if (params.getUpdatePeriod() != null) {
            long cur = System.currentTimeMillis();
            long period = params.getUpdatePeriod() * 24 * 3600 * 1000;
            long time = cur - period;
            params.setUpdateTimeMin(new Date(time));
        }

        PageHelper.startPage(page, pageSize);
        PageBean<BookVO> directResults = PageBuilder.build(bookMapper.searchByPage(params));
        if (params.getKeyword() == null || directResults.getTotal() > 0) {
            return directResults;
        }

        List<BookVO> ranked = VietnameseSearchMatcher.rank(params.getKeyword(),
            bookMapper.searchFuzzyCandidates(params, FUZZY_SEARCH_CANDIDATE_LIMIT));
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        long requestedOffset = (long) (safePage - 1) * safePageSize;
        int fromIndex = (int) Math.min(ranked.size(), requestedOffset);
        int toIndex = (int) Math.min(ranked.size(), (long) fromIndex + safePageSize);
        return PageBean.of(safePage, safePageSize, ranked.size(), ranked.subList(fromIndex, toIndex));

    }

    static String normalizeSearchKeyword(String keyword) {
        return StringUtils.isBlank(keyword) ? null : StringUtils.normalizeSpace(keyword);
    }

    @Override
    public List<BookCategory> listBookCategory() {
        SelectStatementProvider selectStatementProvider = select(BookCategoryDynamicSqlSupport.id,
            BookCategoryDynamicSqlSupport.name, BookCategoryDynamicSqlSupport.workDirection)
            .from(bookCategory)
            .orderBy(BookCategoryDynamicSqlSupport.sort)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookCategoryMapper.selectMany(selectStatementProvider);
    }

    @Override
    public Book queryBookDetail(Long bookId) {
        SelectStatementProvider selectStatement = select(book.allColumns())
            .from(book)
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookMapper.selectMany(selectStatement).get(0);
    }

    @Override
    public List<BookIndex> queryIndexList(Long bookId, String orderBy, Integer page, Integer pageSize) {
        if (page != null && pageSize != null) {
            PageHelper.startPage(page, pageSize);
        }
        QueryExpressionDSL<org.mybatis.dynamic.sql.select.SelectModel>.QueryExpressionWhereBuilder where = select(
            BookIndexDynamicSqlSupport.id,
            BookIndexDynamicSqlSupport.bookId, BookIndexDynamicSqlSupport.indexNum,
            BookIndexDynamicSqlSupport.indexName, BookIndexDynamicSqlSupport.updateTime,
            BookIndexDynamicSqlSupport.isVip)
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1));
        if ("index_num desc".equals(orderBy)) {
            where.orderBy(BookIndexDynamicSqlSupport.indexNum.descending());
        }
        return bookIndexMapper.selectMany(where
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }


    @Override
    public BookIndex queryBookIndex(Long bookIndexId) {
        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id,
            BookIndexDynamicSqlSupport.bookId, BookIndexDynamicSqlSupport.indexNum,
            BookIndexDynamicSqlSupport.indexName, BookIndexDynamicSqlSupport.wordCount,
            BookIndexDynamicSqlSupport.bookPrice, BookIndexDynamicSqlSupport.updateTime,
            BookIndexDynamicSqlSupport.isVip, BookIndexDynamicSqlSupport.storageType,
            BookIndexDynamicSqlSupport.auditStatus)
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.id, isEqualTo(bookIndexId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookIndexMapper.selectMany(selectStatement).get(0);
    }

    @Override
    public Long queryPreBookIndexId(Long bookId, Integer indexNum) {
        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id)
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.indexNum, isLessThan(indexNum))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1))
            .orderBy(BookIndexDynamicSqlSupport.indexNum.descending())
            .limit(1)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<BookIndex> list = bookIndexMapper.selectMany(selectStatement);
        if (list.size() == 0) {
            return 0L;
        } else {
            return list.get(0).getId();
        }
    }

    @Override
    public Long queryNextBookIndexId(Long bookId, Integer indexNum) {
        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id)
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.indexNum, isGreaterThan(indexNum))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1))
            .orderBy(BookIndexDynamicSqlSupport.indexNum)
            .limit(1)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<BookIndex> list = bookIndexMapper.selectMany(selectStatement);
        if (list.size() == 0) {
            return 0L;
        } else {
            return list.get(0).getId();
        }
    }

    @Override
    public BookContent queryBookContent(Long bookIndexId) {
        SelectStatementProvider selectStatement = select(BookContentDynamicSqlSupport.id,
            BookContentDynamicSqlSupport.content)
            .from(bookContent)
            .where(BookContentDynamicSqlSupport.indexId, isEqualTo(bookIndexId))
            .limit(1)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookContentMapper.selectMany(selectStatement).get(0);
    }

    @Override
    public List<Book> listRank(Byte type, Integer limit) {
        SortSpecification sortSpecification = visitCount.descending();
        switch (type) {
            case 1: {
                //Sắp xếp theo thời gian nhập kho mới nhất
                sortSpecification = createTime.descending();
                break;
            }
            case 2: {
                //Sắp xếp theo thời gian cập nhật mới nhất
                sortSpecification = lastIndexUpdateTime.descending();
                break;
            }
            case 3: {
                //Sắp xếp theo số bình luận
                sortSpecification = commentCount.descending();
                break;
            }
            default: {
                break;
            }
        }
        SelectStatementProvider selectStatement = select(id, catId, catName, bookName, lastIndexId, lastIndexName,
            authorId, authorName, picUrl, bookDesc, wordCount, lastIndexUpdateTime)
            .from(book)
            .where(wordCount, isGreaterThan(0))
            .and(lastIndexId, isNotNull())
            .and(status, isEqualTo((byte) 1))
            .and(auditStatus, isEqualTo((byte) 1))
            .and(coverAuditStatus, isEqualTo((byte) 1))
            .and(ageRating, isEqualTo((byte) 0))
            .orderBy(sortSpecification)
            .limit(limit)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookMapper.selectMany(selectStatement);

    }

    @Override
    public void addVisitCount(Long bookId, Integer visitCount) {
        bookMapper.addVisitCount(bookId, visitCount);
    }

    @Override
    public long queryIndexCount(Long bookId) {
        SelectStatementProvider selectStatement = select(count(BookIndexDynamicSqlSupport.id))
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1))
            .build()
            .render(RenderingStrategies.MYBATIS3);

        return bookIndexMapper.count(selectStatement);
    }

    @Override
    public List<Book> listRecBookByCatId(Integer catId) {
        return bookMapper.listRecBookByCatId(catId);
    }

    @Override
    public Long queryFirstBookIndexId(Long bookId) {
        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id)
            .from(bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1))
            .orderBy(BookIndexDynamicSqlSupport.indexNum)
            .limit(1)
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookIndexMapper.selectMany(selectStatement).stream()
            .findFirst()
            .map(BookIndex::getId)
            .orElse(null);
    }

    @Override
    public PageBean<BookCommentVO> listCommentByPage(Long userId, Long bookId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        PageBean<BookCommentVO> pageBean = PageBuilder.build(bookCommentMapper.listCommentByPage(userId, bookId));
        for (BookCommentVO bookCommentVO : pageBean.getList()) {
            bookCommentVO.setLikesCount(likeService.getCommentLikesCount(bookCommentVO.getId()));
            bookCommentVO.setUnLikesCount(likeService.getCommentUnLikesCount(bookCommentVO.getId()));
        }
        return pageBean;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addBookComment(Long userId, BookComment comment) {
        comment.setCommentContent(richTextSanitizer.sanitizeText(comment.getCommentContent()));
        //Kiểm tra người dùng đã bình luận tác phẩm hay chưa
        SelectStatementProvider selectStatement = select(count(BookCommentDynamicSqlSupport.id))
            .from(bookComment)
            .where(BookCommentDynamicSqlSupport.createUserId, isEqualTo(userId))
            .and(BookCommentDynamicSqlSupport.bookId, isEqualTo(comment.getBookId()))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        if (bookCommentMapper.count(selectStatement) > 0) {
            throw new BusinessException(ResponseStatus.HAS_COMMENTS);
        }
        //Tăng bình luận
        comment.setCreateUserId(userId);
        comment.setCreateTime(new Date());
        bookCommentMapper.insertSelective(comment);
        //Tăng số bình luận của tác phẩm
        bookMapper.addCommentCount(comment.getBookId());

    }

    @Override
    public Long getOrCreateAuthorIdByName(String authorName, Byte workDirection) {
        Long authorId;
        SelectStatementProvider selectStatement = select(BookAuthorDynamicSqlSupport.id)
            .from(BookAuthorDynamicSqlSupport.bookAuthor)
            .where(BookAuthorDynamicSqlSupport.penName, isEqualTo(authorName))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<BookAuthor> bookAuthors = bookAuthorMapper.selectMany(selectStatement);
        if (bookAuthors.size() > 0) {
            //Tác giả đã tồn tại
            authorId = bookAuthors.get(0).getId();
        } else {
            //Tác giả chưa tồn tại, tạo tác giả trước
            Date currentDate = new Date();
            authorId = idWorker.nextId();
            BookAuthor bookAuthor = new BookAuthor();
            bookAuthor.setId(authorId);
            bookAuthor.setPenName(authorName);
            bookAuthor.setWorkDirection(workDirection);
            bookAuthor.setStatus((byte) 1);
            bookAuthor.setCreateTime(currentDate);
            bookAuthor.setUpdateTime(currentDate);
            bookAuthorMapper.insertSelective(bookAuthor);


        }

        return authorId;
    }


    @Override
    public Long queryIdByNameAndAuthor(String bookName, String author) {
        //Truy vấn ID tác phẩm
        SelectStatementProvider selectStatement = select(id)
            .from(book)
            .where(BookDynamicSqlSupport.bookName, isEqualTo(bookName))
            .and(BookDynamicSqlSupport.authorName, isEqualTo(authorName))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<Book> books = bookMapper.selectMany(selectStatement);
        if (books.size() > 0) {
            return books.get(0).getId();
        }
        return null;
    }

    @Override
    public List<Integer> queryIndexNumByBookId(Long bookId) {
        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.indexNum)
            .from(BookIndexDynamicSqlSupport.bookIndex)
            .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3);

        return bookIndexMapper.selectMany(selectStatement).stream().map(BookIndex::getIndexNum)
            .collect(Collectors.toList());
    }

    @Override
    public List<Book> queryNetworkPicBooks(String localPicPrefix, Integer limit) {
        return bookMapper.queryNetworkPicBooks(localPicPrefix, limit);
    }

    @Override
    public void updateBookPicToLocal(String picUrl, Long bookId) {

        picUrl = fileService.transFile(picUrl, picSavePath);

        bookMapper.update(update(book)
            .set(BookDynamicSqlSupport.picUrl)
            .equalTo(picUrl)
            .set(updateTime)
            .equalTo(new Date())
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3));

    }

    @Override
    public PageBean<Book> listBookPageByUserId(Long userId, int page, int pageSize) {

        Author author = authorService.queryAuthor(userId);
        PageHelper.startPage(page, pageSize);
        return PageBuilder.build(collaborationService.listAccessibleBooks(author.getId()));

    }

    @Override
    public void addBook(Book book, Long authorId, String penName) {
        BookCategory category = Optional.ofNullable(book.getCatId())
            .flatMap(bookCategoryMapper::selectByPrimaryKey)
            .orElseThrow(() -> new BusinessException(ResponseStatus.BOOK_CATEGORY_INVALID));
        book.setCatName(richTextSanitizer.sanitizeText(category.getName()));
        book.setWorkDirection(category.getWorkDirection());
        if (book.getPicUrl() == null || !LOCAL_COVER_PATTERN.matcher(book.getPicUrl()).matches()) {
            book.setPicUrl(null);
        }
        book.setBookName(richTextSanitizer.sanitizeText(book.getBookName()));
        book.setBookDesc(richTextSanitizer.sanitize(book.getBookDesc()));
        penName = richTextSanitizer.sanitizeText(penName);
        book.setId(IdWorker.INSTANCE.nextId());
        //Kiểm tra tên tác phẩm có tồn tại hay không
        if (queryIdByNameAndAuthor(book.getBookName(), penName) != null) {
            //Tác giả đã xuất bản tác phẩm trùng tên
            throw new BusinessException(ResponseStatus.BOOKNAME_EXISTS);
        }
        book.setAuthorName(penName);
        book.setAuthorId(authorId);
        book.setVisitCount(0L);
        book.setWordCount(0);
        book.setScore(6.5f);
        book.setLastIndexName("");
        book.setCreateTime(new Date());
        book.setUpdateTime(book.getCreateTime());
        bookMapper.insertSelective(book);
        if (Objects.isNull(book.getPicUrl()) || !book.getPicUrl().startsWith(Constants.LOCAL_PIC_PREFIX)) {
            // Người dùng chưa tải bìa; AI tự động tạo ảnh bìa
            threadPoolExecutor.execute(() -> {
                String prompt = messages.get("ai.cover.prompt", book.getBookName(), book.getAuthorName());
                ImageResponse response = openAiImageModel.call(
                    new ImagePrompt(prompt,
                        OpenAiImageOptions.builder()
                            .quality("hd")
                            .height(800)
                            .width(600).build())
                );
                Image output = response.getResult().getOutput();
                Date currentDate = new Date();
                String picUrl = Constants.LOCAL_PIC_PREFIX +
                    "aiGen/" + DateUtils.formatDate(currentDate, "yyyy") + "/" +
                    DateUtils.formatDate(currentDate, "MM") + "/" +
                    DateUtils.formatDate(currentDate, "dd") + "/" + book.getId() + ".png";
                FileUtil.downloadFile(output.getUrl(), picSavePath + picUrl);
                bookMapper.update(update(BookDynamicSqlSupport.book)
                    .set(BookDynamicSqlSupport.picUrl)
                    .equalTo(picUrl)
                    .set(BookDynamicSqlSupport.coverAuditStatus)
                    .equalTo((byte) 0)
                    .set(BookDynamicSqlSupport.coverAuditReason)
                    .equalToNull()
                    .set(updateTime)
                    .equalTo(currentDate)
                    .where(id, isEqualTo(book.getId()))
                    .build()
                    .render(RenderingStrategies.MYBATIS3));
                cacheService.set(CacheKey.AI_GEN_PIC + book.getId(), picUrl, 60 * 60);
            });
        }
    }

    @Override
    public void updateBookStatus(Long bookId, Byte status, Long authorId) {
        collaborationService.requirePermission(authorId, bookId, BookPermission.PUBLISH_BOOK);
        bookMapper.update(update(book)
            .set(BookDynamicSqlSupport.status)
            .equalTo(status)
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addBookContent(Long bookId, String indexName, String content, Byte isVip, Long authorId) {
        collaborationService.requirePermission(authorId, bookId, BookPermission.PUBLISH_CHAPTERS);
        indexName = richTextSanitizer.sanitizeText(indexName);
        content = richTextSanitizer.sanitize(content);
        Book book = bookMapper.lockById(bookId);
        if (book == null) {
            throw new IllegalArgumentException("Không tìm thấy tác phẩm");
        }
        Long lastIndexId = idWorker.nextId();
        Date currentDate = new Date();
        int wordCount = StringUtil.getStrValidWordCount(content);

        //Cập nhật thông tin bảng chính tác phẩm
        bookMapper.update(update(BookDynamicSqlSupport.book)
            .set(BookDynamicSqlSupport.lastIndexId)
            .equalTo(lastIndexId)
            .set(BookDynamicSqlSupport.lastIndexName)
            .equalTo(indexName)
            .set(BookDynamicSqlSupport.lastIndexUpdateTime)
            .equalTo(currentDate)
            .set(BookDynamicSqlSupport.wordCount)
            .equalTo(book.getWordCount() + wordCount)
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3));

        byte normalizedVip = isVip == null ? 0 : isVip;
        int automaticPrice = chapterCommercialPolicyService.calculateAutomaticPrice(wordCount);
        int bookPrice = chapterCommercialPolicyService.resolveEffectivePrice(normalizedVip, null, automaticPrice);
        String contentHash = ContentHashUtil.sha256Hex(content);
        String simHash = SimHashUtil.getSimHash(content);
        byte auditStatus = 1;

        if (SensitiveWordFilter.getInstance().containsSensitiveWord(content)
            || SensitiveWordFilter.getInstance().containsSensitiveWord(indexName)) {
            auditStatus = 0;
        } else {
            SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id, BookIndexDynamicSqlSupport.contentHash, BookIndexDynamicSqlSupport.simHash)
                .from(BookIndexDynamicSqlSupport.bookIndex)
                .where(BookIndexDynamicSqlSupport.contentHash, isNotNull())
                .build().render(RenderingStrategies.MYBATIS3);
            List<BookIndex> existingChapters = bookIndexMapper.selectMany(selectStatement);
            for (BookIndex existing : existingChapters) {
                if (contentHash.equalsIgnoreCase(existing.getContentHash())) {
                    auditStatus = 0;
                    break;
                }
                if (existing.getSimHash() != null && SimHashUtil.isSimilar(simHash, existing.getSimHash(), 3)) {
                    auditStatus = 0;
                    break;
                }
            }
        }

        //Cập nhật bảng mục lục tác phẩm
        int indexNum = 0;
        if (book.getLastIndexId() != null) {
            indexNum = queryBookIndex(book.getLastIndexId()).getIndexNum() + 1;
        }
        BookIndex lastBookIndex = new BookIndex();
        lastBookIndex.setId(lastIndexId);
        lastBookIndex.setWordCount(wordCount);
        lastBookIndex.setIndexName(indexName);
        lastBookIndex.setIndexNum(indexNum);
        lastBookIndex.setBookId(bookId);
        lastBookIndex.setIsVip(normalizedVip);
        lastBookIndex.setBookPrice(bookPrice);
        lastBookIndex.setContentHash(contentHash);
        lastBookIndex.setSimHash(simHash);
        lastBookIndex.setAuditStatus(auditStatus);
        lastBookIndex.setCreateTime(currentDate);
        lastBookIndex.setUpdateTime(currentDate);
        bookIndexMapper.insertSelective(lastBookIndex);

        //Cập nhật bảng nội dung tác phẩm
        BookContent bookContent = new BookContent();
        bookContent.setIndexId(lastIndexId);
        bookContent.setContent(content);
        bookContentMapper.insertSelective(bookContent);

        // Chapter version snapshotting
        BookContentHistory history = new BookContentHistory();
        history.setBookId(bookId);
        history.setIndexId(lastIndexId);
        history.setVersionNum(1);
        history.setIndexName(indexName);
        history.setContent(content);
        history.setWordCount(wordCount);
        history.setContentHash(contentHash);
        history.setModifiedBy(authorId);
        history.setModifiedType((byte) 1);
        history.setChangeReason("Tạo mới chương");
        history.setCreateTime(currentDate);
        bookContentHistoryMapper.insertSelective(history);

        return lastIndexId;
    }

    @Override
    public List<Book> queryBookByUpdateTimeByPage(Date startDate, int limit) {

        return bookMapper.selectMany(select(book.allColumns())
            .from(book)
            .where(updateTime, isGreaterThan(startDate))
            .and(lastIndexId, isNotNull())
            .orderBy(updateTime)
            .limit(limit)
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public List<Book> queryBookList(Long authorId) {
        return collaborationService.listChapterManageableBooks(authorId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteIndex(Long indexId, Long authorId) {
        BookIndex lockedBookIndex = bookIndexMapper.lockById(indexId);
        if (lockedBookIndex == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        Long bookId = lockedBookIndex.getBookId();
        collaborationService.requirePermission(authorId, bookId, BookPermission.PUBLISH_CHAPTERS);
        Book lockedBook = bookMapper.lockById(bookId);
        if (lockedBook == null) {
            throw new IllegalArgumentException("Không tìm thấy tác phẩm");
        }

        bookIndexMapper.deleteByPrimaryKey(indexId);
        bookContentMapper.delete(
            deleteFrom(bookContent).where(BookContentDynamicSqlSupport.indexId, isEqualTo(indexId)).build()
                .render(RenderingStrategies.MYBATIS3));

        int aggregateWordCount = Math.max(0,
            Objects.requireNonNullElse(lockedBook.getWordCount(), 0)
                - Objects.requireNonNullElse(lockedBookIndex.getWordCount(), 0));
        Long lastIndexId = null;
        String lastIndexName = null;
        Date lastIndexUpdateTime = null;
        List<BookIndex> lastBookIndices = bookIndexMapper.selectMany(
            select(BookIndexDynamicSqlSupport.id, BookIndexDynamicSqlSupport.indexName,
                BookIndexDynamicSqlSupport.createTime)
                .from(BookIndexDynamicSqlSupport.bookIndex)
                .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
                .orderBy(BookIndexDynamicSqlSupport.indexNum.descending())
                .limit(1)
                .build()
                .render(RenderingStrategies.MYBATIS3));
        if (!lastBookIndices.isEmpty()) {
            BookIndex lastBookIndex = lastBookIndices.get(0);
            lastIndexId = lastBookIndex.getId();
            lastIndexName = lastBookIndex.getIndexName();
            lastIndexUpdateTime = lastBookIndex.getCreateTime();
        }
        bookMapper.update(update(BookDynamicSqlSupport.book)
            .set(BookDynamicSqlSupport.wordCount).equalTo(aggregateWordCount)
            .set(updateTime).equalTo(new Date())
            .set(BookDynamicSqlSupport.lastIndexId).equalTo(lastIndexId)
            .set(BookDynamicSqlSupport.lastIndexName).equalTo(lastIndexName)
            .set(BookDynamicSqlSupport.lastIndexUpdateTime).equalTo(lastIndexUpdateTime)
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public void updateIndexName(Long indexId, String indexName, Long authorId) {
        BookIndex index = bookIndexMapper.selectByPrimaryKey(indexId).orElse(null);
        if (index == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        collaborationService.requirePermission(authorId, index.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        bookIndexMapper.update(
            update(BookIndexDynamicSqlSupport.bookIndex)
                .set(BookIndexDynamicSqlSupport.indexName)
                .equalTo(indexName)
                .set(BookIndexDynamicSqlSupport.updateTime)
                .equalTo(new Date())
                .where(BookIndexDynamicSqlSupport.id, isEqualTo(indexId))
                .build()
                .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public String queryIndexContent(Long indexId, Long authorId) {
        BookIndex index = bookIndexMapper.selectByPrimaryKey(indexId).orElse(null);
        if (index == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        collaborationService.requirePermission(authorId, index.getBookId(), BookPermission.MANAGE_CHAPTERS);
        return bookContentMapper.selectMany(
                select(content)
                    .from(bookContent)
                    .where(BookContentDynamicSqlSupport.indexId, isEqualTo(indexId))
                    .limit(1)
                    .build().render(RenderingStrategies.MYBATIS3))
            .stream().findFirst().map(BookContent::getContent).orElse("");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateBookContent(Long indexId, String indexName, String content, Long authorId) {
        BookIndex lockedIndex = bookIndexMapper.lockById(indexId);
        if (lockedIndex == null) {
            throw new IllegalArgumentException("Không tìm thấy chương cần cập nhật");
        }

        Long bookId = lockedIndex.getBookId();
        collaborationService.requirePermission(authorId, bookId, BookPermission.PUBLISH_CHAPTERS);
        indexName = richTextSanitizer.sanitizeText(indexName);
        content = richTextSanitizer.sanitize(content);
        Book lockedBook = bookMapper.lockById(bookId);
        if (lockedBook == null) {
            throw new IllegalArgumentException("Không tìm thấy tác phẩm");
        }

        BookContent existingContent = bookContentMapper.selectMany(
                select(BookContentDynamicSqlSupport.content)
                    .from(bookContent)
                    .where(BookContentDynamicSqlSupport.indexId, isEqualTo(indexId))
                    .limit(1)
                    .build().render(RenderingStrategies.MYBATIS3))
            .stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nội dung chương cần cập nhật"));
        SelectStatementProvider versionQuery = select(
                org.mybatis.dynamic.sql.SqlBuilder.count(BookContentHistoryDynamicSqlSupport.id))
            .from(BookContentHistoryDynamicSqlSupport.bookContentHistory)
            .where(BookContentHistoryDynamicSqlSupport.indexId, isEqualTo(indexId))
            .build().render(RenderingStrategies.MYBATIS3);
        long historyCount = bookContentHistoryMapper.count(versionQuery);

        Date currentDate = new Date();
        int oldWordCount = Objects.requireNonNullElse(lockedIndex.getWordCount(), 0);
        if (historyCount == 0) {
            BookContentHistory original = new BookContentHistory();
            original.setBookId(bookId);
            original.setIndexId(indexId);
            original.setVersionNum(1);
            original.setIndexName(lockedIndex.getIndexName());
            original.setContent(existingContent.getContent());
            original.setWordCount(oldWordCount);
            original.setContentHash(StringUtils.defaultIfBlank(lockedIndex.getContentHash(),
                ContentHashUtil.sha256Hex(existingContent.getContent())));
            original.setModifiedBy(authorId);
            original.setModifiedType((byte) 1);
            original.setChangeReason("Phiên bản trước lần chỉnh sửa đầu tiên");
            original.setCreateTime(currentDate);
            bookContentHistoryMapper.insertSelective(original);
        }
        int nextVersion = historyCount == 0 ? 2 : Math.toIntExact(historyCount + 1);
        int newWordCount = StringUtil.getStrValidWordCount(content);
        byte currentVip = lockedIndex.getIsVip() == null ? 0 : lockedIndex.getIsVip();
        int automaticPrice = chapterCommercialPolicyService.calculateAutomaticPrice(newWordCount);
        int bookPrice = chapterCommercialPolicyService.resolveEffectivePrice(currentVip,
            chapterCommercialPolicyService.findCustomPrice(indexId), automaticPrice);

        String contentHash = ContentHashUtil.sha256Hex(content);
        String simHash = SimHashUtil.getSimHash(content);
        byte auditStatus = 1;
        if (SensitiveWordFilter.getInstance().containsSensitiveWord(content)
            || SensitiveWordFilter.getInstance().containsSensitiveWord(indexName)) {
            auditStatus = 0;
        } else {
            SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.id,
                    BookIndexDynamicSqlSupport.contentHash, BookIndexDynamicSqlSupport.simHash)
                .from(BookIndexDynamicSqlSupport.bookIndex)
                .where(BookIndexDynamicSqlSupport.contentHash, isNotNull())
                .build().render(RenderingStrategies.MYBATIS3);
            List<BookIndex> existingChapters = bookIndexMapper.selectMany(selectStatement);
            for (BookIndex existing : existingChapters) {
                if (!existing.getId().equals(indexId)
                    && (contentHash.equalsIgnoreCase(existing.getContentHash())
                    || existing.getSimHash() != null && SimHashUtil.isSimilar(simHash, existing.getSimHash(), 3))) {
                    auditStatus = 0;
                    break;
                }
            }
        }

        bookIndexMapper.update(
            update(BookIndexDynamicSqlSupport.bookIndex)
                .set(BookIndexDynamicSqlSupport.indexName)
                .equalTo(indexName)
                .set(BookIndexDynamicSqlSupport.wordCount)
                .equalTo(newWordCount)
                .set(BookIndexDynamicSqlSupport.bookPrice)
                .equalTo(bookPrice)
                .set(BookIndexDynamicSqlSupport.contentHash)
                .equalTo(contentHash)
                .set(BookIndexDynamicSqlSupport.simHash)
                .equalTo(simHash)
                .set(BookIndexDynamicSqlSupport.auditStatus)
                .equalTo(auditStatus)
                .set(BookIndexDynamicSqlSupport.updateTime)
                .equalTo(currentDate)
                .where(BookIndexDynamicSqlSupport.id, isEqualTo(indexId))
                .build().render(RenderingStrategies.MYBATIS3));

        bookContentMapper.update(
            update(BookContentDynamicSqlSupport.bookContent)
                .set(BookContentDynamicSqlSupport.content)
                .equalTo(content)
                .where(BookContentDynamicSqlSupport.indexId, isEqualTo(indexId))
                .build().render(RenderingStrategies.MYBATIS3));

        int aggregateWordCount = Math.max(0,
            Objects.requireNonNullElse(lockedBook.getWordCount(), 0) - oldWordCount + newWordCount);
        if (indexId.equals(lockedBook.getLastIndexId())) {
            bookMapper.update(update(BookDynamicSqlSupport.book)
                .set(BookDynamicSqlSupport.wordCount).equalTo(aggregateWordCount)
                .set(BookDynamicSqlSupport.lastIndexName).equalTo(indexName)
                .set(BookDynamicSqlSupport.lastIndexUpdateTime).equalTo(currentDate)
                .set(BookDynamicSqlSupport.updateTime).equalTo(currentDate)
                .where(id, isEqualTo(bookId))
                .build().render(RenderingStrategies.MYBATIS3));
        } else {
            bookMapper.update(update(BookDynamicSqlSupport.book)
                .set(BookDynamicSqlSupport.wordCount).equalTo(aggregateWordCount)
                .set(BookDynamicSqlSupport.updateTime).equalTo(currentDate)
                .where(id, isEqualTo(bookId))
                .build().render(RenderingStrategies.MYBATIS3));
        }

        BookContentHistory history = new BookContentHistory();
        history.setBookId(bookId);
        history.setIndexId(indexId);
        history.setVersionNum(nextVersion);
        history.setIndexName(indexName);
        history.setContent(content);
        history.setWordCount(newWordCount);
        history.setContentHash(contentHash);
        history.setModifiedBy(authorId);
        history.setModifiedType((byte) 1);
        history.setChangeReason("Cập nhật nội dung chương");
        history.setCreateTime(currentDate);
        bookContentHistoryMapper.insertSelective(history);
    }

    @Override
    public void updateBookPic(Long bookId, String bookPic, Long authorId) {
        collaborationService.requirePermission(authorId, bookId, BookPermission.EDIT_BOOK);
        if (bookPic == null || !LOCAL_COVER_PATTERN.matcher(bookPic).matches()) {
            throw new BusinessException(ResponseStatus.FILE_NOT_IMAGE);
        }
        bookMapper.update(update(book)
            .set(picUrl)
            .equalTo(bookPic)
            .set(BookDynamicSqlSupport.coverAuditStatus)
            .equalTo((byte) 0)
            .set(BookDynamicSqlSupport.coverAuditReason)
            .equalToNull()
            .set(updateTime)
            .equalTo(new Date())
            .where(id, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public String queryAiGenPic(Long bookId) {
        return cacheService.get(CacheKey.AI_GEN_PIC + bookId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addBookCommentReply(Long userId, BookCommentReply commentReply) {
        commentReply.setReplyContent(richTextSanitizer.sanitizeText(commentReply.getReplyContent()));
        //Tăng phản hồi
        commentReply.setCreateUserId(userId);
        commentReply.setCreateTime(new Date());
        bookCommentReplyMapper.insertSelective(commentReply);
        //Tăng số phản hồi bình luận
        bookCommentMapper.addReplyCount(commentReply.getCommentId());
    }

    @Override
    public PageBean<BookCommentReplyVO> listCommentReplyByPage(Long userId, Long commentId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        PageBean<BookCommentReplyVO> pageBean = PageBuilder.build(
            bookCommentReplyMapper.listCommentReplyByPage(userId, commentId));
        pageBean.getList().forEach(commentReply -> {
            commentReply.setLikesCount(likeService.getReplyLikesCount(commentReply.getId()));
            commentReply.setUnLikesCount(likeService.getReplyUnLikesCount(commentReply.getId()));
        });
        return pageBean;
    }

    @Override
    public BookComment getBookComment(Long commentId) {
        return bookCommentMapper.selectByPrimaryKey(commentId).orElse(null);
    }

    @Override
    public List<BookContentHistory> listChapterHistory(Long indexId, Long authorId) {
        requireChapterOwnership(indexId, authorId);
        SelectStatementProvider selectStatement = select(BookContentHistoryDynamicSqlSupport.bookContentHistory.allColumns())
            .from(BookContentHistoryDynamicSqlSupport.bookContentHistory)
            .where(BookContentHistoryDynamicSqlSupport.indexId, isEqualTo(indexId))
            .orderBy(BookContentHistoryDynamicSqlSupport.versionNum.descending())
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return bookContentHistoryMapper.selectMany(selectStatement);
    }

    @Override
    public Map<String, Object> compareChapterHistory(Long indexId, Integer v1, Integer v2, Long authorId) {
        requireChapterOwnership(indexId, authorId);
        SelectStatementProvider selectStatement1 = select(BookContentHistoryDynamicSqlSupport.bookContentHistory.allColumns())
            .from(BookContentHistoryDynamicSqlSupport.bookContentHistory)
            .where(BookContentHistoryDynamicSqlSupport.indexId, isEqualTo(indexId))
            .and(BookContentHistoryDynamicSqlSupport.versionNum, isEqualTo(v1))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        BookContentHistory h1 = bookContentHistoryMapper.selectOne(selectStatement1).orElse(null);

        SelectStatementProvider selectStatement2 = select(BookContentHistoryDynamicSqlSupport.bookContentHistory.allColumns())
            .from(BookContentHistoryDynamicSqlSupport.bookContentHistory)
            .where(BookContentHistoryDynamicSqlSupport.indexId, isEqualTo(indexId))
            .and(BookContentHistoryDynamicSqlSupport.versionNum, isEqualTo(v2))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        BookContentHistory h2 = bookContentHistoryMapper.selectOne(selectStatement2).orElse(null);

        Map<String, Object> result = new HashMap<>(4);
        result.put("v1", h1);
        result.put("v2", h2);
        return result;
    }

    private void requireChapterOwnership(Long indexId, Long authorId) {
        BookIndex index = bookIndexMapper.selectByPrimaryKey(indexId).orElse(null);
        if (index == null) {
            throw new IllegalArgumentException("Không tìm thấy chương");
        }
        collaborationService.requirePermission(authorId, index.getBookId(), BookPermission.MANAGE_CHAPTERS);
    }
}
