package com.java2nb.novel.service.impl;

import com.github.pagehelper.PageHelper;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.AuthorIncomeProperties;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.mapper.*;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.gamification.GamificationEventService;
import com.java2nb.novel.vo.BookReadHistoryVO;
import com.java2nb.novel.vo.BookShelfVO;
import com.java2nb.novel.vo.UserFeedbackVO;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.page.builder.pagehelper.PageBuilder;
import io.github.xxyopen.util.IdWorker;
import io.github.xxyopen.util.MD5Util;
import io.github.xxyopen.web.exception.BusinessException;
import io.github.xxyopen.web.util.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import static com.java2nb.novel.mapper.BookDynamicSqlSupport.id;
import static com.java2nb.novel.mapper.UserBookshelfDynamicSqlSupport.userBookshelf;
import static com.java2nb.novel.mapper.UserDynamicSqlSupport.*;
import static com.java2nb.novel.mapper.UserFeedbackDynamicSqlSupport.userFeedback;
import static com.java2nb.novel.mapper.UserReadHistoryDynamicSqlSupport.userReadHistory;
import static org.mybatis.dynamic.sql.SqlBuilder.*;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * @author 11797
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final FrontUserMapper userMapper;

    private final FrontUserBookshelfMapper userBookshelfMapper;

    private final FrontUserReadHistoryMapper userReadHistoryMapper;

    private final UserFeedbackMapper userFeedbackMapper;

    private final UserBuyRecordMapper userBuyRecordMapper;

    private final WalletLedgerService walletLedgerService;

    private final AuthorIncomeProperties authorIncomeProperties;

    private final BookIndexMapper bookIndexMapper;

    private final ChapterCommercialPolicyService chapterCommercialPolicyService;

    private final ReadingTicketService readingTicketService;

    private final GamificationEventService gamificationEventService;

    private final IdWorker idWorker = IdWorker.INSTANCE;


    @Override
    public UserDetails register(User user) {
        //Truy vấn trạng thái đăng ký tên người dùng
        SelectStatementProvider selectStatement = select(count(id))
            .from(UserDynamicSqlSupport.user)
            .where(username, isEqualTo(user.getUsername()))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        long count = userMapper.count(selectStatement);
        if (count > 0) {
            //Tên người dùng đã được đăng ký
            throw new BusinessException(ResponseStatus.USERNAME_EXIST);
        }
        User entity = new User();
        BeanUtils.copyProperties(user, entity);
        //Tạo bản ghi đăng ký trong cơ sở dữ liệu
        Long id = idWorker.nextId();
        entity.setId(id);
        entity.setNickName(entity.getUsername());
        Date currentDate = new Date();
        entity.setCreateTime(currentDate);
        entity.setUpdateTime(currentDate);
        entity.setPassword(MD5Util.MD5Encode(entity.getPassword(), Charsets.UTF_8.name()));
        userMapper.insertSelective(entity);
        //Tạo và trả về đối tượng UserDetail
        UserDetails userDetails = new UserDetails();
        userDetails.setId(id);
        userDetails.setUsername(entity.getUsername());
        userDetails.setNickName(entity.getNickName());
        return userDetails;
    }

    @Override
    public UserDetails login(User user) {
        //Truy vấn bản ghi theo tên đăng nhập và mật khẩu
        SelectStatementProvider selectStatement = select(id, username, nickName)
            .from(UserDynamicSqlSupport.user)
            .where(username, isEqualTo(user.getUsername()))
            .and(password, isEqualTo(MD5Util.MD5Encode(user.getPassword(), Charsets.UTF_8.name())))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<User> users = userMapper.selectMany(selectStatement);
        if (users.size() == 0) {
            throw new BusinessException(ResponseStatus.USERNAME_PASS_ERROR);
        }
        //Tạo và trả về đối tượng UserDetail
        UserDetails userDetails = new UserDetails();
        user = users.get(0);
        userDetails.setId(user.getId());
        userDetails.setNickName(user.getNickName());
        userDetails.setUsername(user.getUsername());
        return userDetails;
    }

    @Override
    public Boolean queryIsInShelf(Long userId, Long bookId) {
        SelectStatementProvider selectStatement = select(count(UserBookshelfDynamicSqlSupport.id))
            .from(userBookshelf)
            .where(UserBookshelfDynamicSqlSupport.userId, isEqualTo(userId))
            .and(UserBookshelfDynamicSqlSupport.bookId, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3);

        return userBookshelfMapper.count(selectStatement) > 0;
    }

    @Override
    public void addToBookShelf(Long userId, Long bookId, Long preContentId) {
        if (!queryIsInShelf(userId, bookId)) {
            UserBookshelf shelf = new UserBookshelf();
            shelf.setUserId(userId);
            shelf.setBookId(bookId);
            shelf.setPreContentId(preContentId);
            shelf.setCreateTime(new Date());
            userBookshelfMapper.insert(shelf);
        }

    }

    @Override
    public void removeFromBookShelf(Long userId, Long bookId) {
        DeleteStatementProvider deleteStatement = deleteFrom(userBookshelf)
            .where(UserBookshelfDynamicSqlSupport.userId, isEqualTo(userId))
            .and(UserBookshelfDynamicSqlSupport.bookId, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        userBookshelfMapper.delete(deleteStatement);

    }

    @Override
    public PageBean<BookShelfVO> listBookShelfByPage(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        return PageBuilder.build(userBookshelfMapper.listBookShelf(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addReadHistory(Long userId, Long bookId, Long preContentId) {

        Date currentDate = new Date();
        //Xóa lịch sử cũ của tác phẩm
        DeleteStatementProvider deleteStatement = deleteFrom(userReadHistory)
            .where(UserReadHistoryDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(UserReadHistoryDynamicSqlSupport.userId, isEqualTo(userId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        userReadHistoryMapper.delete(deleteStatement);

        //Thêm lịch sử mới của tác phẩm
        UserReadHistory userReadHistory = new UserReadHistory();
        userReadHistory.setBookId(bookId);
        userReadHistory.setUserId(userId);
        userReadHistory.setPreContentId(preContentId);
        userReadHistory.setCreateTime(currentDate);
        userReadHistory.setUpdateTime(currentDate);
        userReadHistoryMapper.insertSelective(userReadHistory);

        //Cập nhật lịch sử đọc trong tủ sách
        UpdateStatementProvider updateStatement = update(userBookshelf)
            .set(UserBookshelfDynamicSqlSupport.preContentId)
            .equalTo(preContentId)
            .set(UserBookshelfDynamicSqlSupport.updateTime)
            .equalTo(currentDate)
            .where(UserBookshelfDynamicSqlSupport.userId, isEqualTo(userId))
            .and(UserBookshelfDynamicSqlSupport.bookId, isEqualTo(bookId))
            .build()
            .render(RenderingStrategies.MYBATIS3);

        userBookshelfMapper.update(updateStatement);


    }

    @Override
    public void addFeedBack(Long userId, String content) {
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setCreateTime(new Date());
        userFeedbackMapper.insertSelective(feedback);
    }

    @Override
    public PageBean<UserFeedback> listUserFeedBackByPage(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        SelectStatementProvider selectStatement = select(UserFeedbackDynamicSqlSupport.content,
            UserFeedbackDynamicSqlSupport.createTime)
            .from(userFeedback)
            .where(UserFeedbackDynamicSqlSupport.userId, isEqualTo(userId))
            .orderBy(UserFeedbackDynamicSqlSupport.id.descending())
            .build()
            .render(RenderingStrategies.MYBATIS3);
        List<UserFeedback> userFeedbacks = userFeedbackMapper.selectMany(selectStatement);
        PageBean<UserFeedback> pageBean = PageBuilder.build(userFeedbacks);
        pageBean.setList(BeanUtil.copyList(userFeedbacks, UserFeedbackVO.class));
        return pageBean;
    }

    @Override
    public User userInfo(Long userId) {
        SelectStatementProvider selectStatement = select(username, nickName, userPhoto, userSex, accountBalance,
                dateOfBirth, isAgeVerified)
            .from(user)
            .where(id, isEqualTo(userId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        return userMapper.selectMany(selectStatement).stream().findFirst().orElse(null);
    }

    @Override
    public PageBean<BookReadHistoryVO> listReadHistoryByPage(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        return PageBuilder.build(userReadHistoryMapper.listReadHistory(userId));
    }

    @Override
    public void updateUserInfo(Long userId, User user) {
        boolean changesDateOfBirth = user.getDateOfBirth() != null;
        user.setIsAgeVerified(changesDateOfBirth ? (byte) 0 : null);
        user.setId(userId);
        user.setUpdateTime(new Date());
        userMapper.updateByPrimaryKeySelective(user);

    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        SelectStatementProvider selectStatement = select(password)
            .from(user)
            .where(id, isEqualTo(userId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        if (!userMapper.selectMany(selectStatement).get(0).getPassword()
            .equals(MD5Util.MD5Encode(oldPassword, Charsets.UTF_8.name()))) {
            throw new BusinessException(ResponseStatus.OLD_PASSWORD_ERROR);
        }
        UpdateStatementProvider updateStatement = update(user)
            .set(password)
            .equalTo(MD5Util.MD5Encode(newPassword, Charsets.UTF_8.name()))
            .where(id, isEqualTo(userId))
            .build()
            .render(RenderingStrategies.MYBATIS3);
        userMapper.update(updateStatement);

    }

    @Override
    public boolean queryIsBuyBookIndex(Long userId, Long bookIndexId) {

        return userBuyRecordMapper.count(c ->
            c.where(UserBuyRecordDynamicSqlSupport.userId, isEqualTo(userId))
                .and(UserBuyRecordDynamicSqlSupport.bookIndexId, isEqualTo(bookIndexId))) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyBookIndex(Long userId, Long authorId, UserBuyRecord buyRecord) {
        if (buyRecord == null || buyRecord.getBookIndexId() == null) {
            throw new IllegalArgumentException("Thiếu chương cần mua");
        }
        BookIndex lockedChapter = bookIndexMapper.lockById(buyRecord.getBookIndexId());
        if (lockedChapter == null) {
            throw new IllegalArgumentException("Không tìm thấy chương cần mua");
        }
        if (buyRecord.getBookId() == null || !buyRecord.getBookId().equals(lockedChapter.getBookId())) {
            throw new IllegalArgumentException("Chương không thuộc tác phẩm cần mua");
        }
        if (queryIsBuyBookIndex(userId, buyRecord.getBookIndexId())) {
            return;
        }
        Date now = new Date();
        if (readingTicketService.hasActiveChapterEntitlement(userId, buyRecord.getBookIndexId(), now)) {
            return;
        }
        if (!chapterCommercialPolicyService.evaluate(lockedChapter, false, now).purchaseRequired()) {
            return;
        }
        buyRecord.setBookIndexName(lockedChapter.getIndexName());
        buyRecord.setBuyAmount(lockedChapter.getBookPrice());
        if (buyRecord.getBuyAmount() == null || buyRecord.getBuyAmount() <= 0) {
            throw new IllegalArgumentException("Giá chương phải lớn hơn 0");
        }
        BigDecimal share = authorIncomeProperties.getShareProportion();
        if (share == null || share.signum() < 0 || share.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Tỷ lệ chia doanh thu tác giả không hợp lệ");
        }
        long authorAmount = BigDecimal.valueOf(buyRecord.getBuyAmount())
            .multiply(share)
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
        WalletPostResult postResult;
        try {
            postResult = walletLedgerService.purchaseChapter(userId, authorId, buyRecord.getBuyAmount(),
                authorAmount, String.valueOf(buyRecord.getBookIndexId()),
                "CHAPTER_PURCHASE:" + userId + ":" + buyRecord.getBookIndexId());
        } catch (InsufficientWalletBalanceException exception) {
            throw new BusinessException(ResponseStatus.USER_NO_BALANCE);
        }
        buyRecord.setUserId(userId);
        buyRecord.setCreateTime(new Date());
        try {
            userBuyRecordMapper.insertSelective(buyRecord);
        } catch (DuplicateKeyException exception) {
            if (postResult != WalletPostResult.ALREADY_POSTED) {
                throw exception;
            }
        }
        if (postResult == WalletPostResult.POSTED) {
            gamificationEventService.ingest("CHAPTER_PURCHASED",
                "GAMIFY:CHAPTER_PURCHASE:" + userId + ":" + buyRecord.getBookIndexId(),
                userId, buyRecord.getBookId(), buyRecord.getCreateTime(), null);
        }
    }

    @Override
    public int queryBuyMember(Long bookId, Date startTime, Date endTime) {
        return userMapper.selectStatistic(select(countDistinct(UserBuyRecordDynamicSqlSupport.userId))
            .from(UserBuyRecordDynamicSqlSupport.userBuyRecord)
            .where(UserBuyRecordDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isGreaterThanOrEqualTo(startTime))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isLessThanOrEqualTo(endTime))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public int queryBuyCount(Long bookId, Date startTime, Date endTime) {
        return userMapper.selectStatistic(select(count(UserBuyRecordDynamicSqlSupport.id))
            .from(UserBuyRecordDynamicSqlSupport.userBuyRecord)
            .where(UserBuyRecordDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isGreaterThanOrEqualTo(startTime))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isLessThanOrEqualTo(endTime))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public int queryBuyAccount(Long bookId, Date startTime, Date endTime) {
        return userMapper.selectStatistic(select(sum(UserBuyRecordDynamicSqlSupport.buyAmount))
            .from(UserBuyRecordDynamicSqlSupport.userBuyRecord)
            .where(UserBuyRecordDynamicSqlSupport.bookId, isEqualTo(bookId))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isGreaterThanOrEqualTo(startTime))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isLessThanOrEqualTo(endTime))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }

    @Override
    public int queryBuyTotalMember(List<Long> bookIds, Date startTime, Date endTime) {
        return userMapper.selectStatistic(select(countDistinct(UserBuyRecordDynamicSqlSupport.userId))
            .from(UserBuyRecordDynamicSqlSupport.userBuyRecord)
            .where(UserBuyRecordDynamicSqlSupport.bookId, isIn(bookIds))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isGreaterThanOrEqualTo(startTime))
            .and(UserBuyRecordDynamicSqlSupport.createTime, isLessThanOrEqualTo(endTime))
            .build()
            .render(RenderingStrategies.MYBATIS3));
    }


}
