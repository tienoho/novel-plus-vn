package com.java2nb.novel.core.schedule;


import com.java2nb.novel.core.utils.DateUtil;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.entity.AuthorIncomeDetail;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Tác vụ thống kê thu nhập hằng ngày của tác giả
 *
 * @author cd
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyIncomeStaSchedule {

    private final AuthorService authorService;

    private final UserService userService;

    private final BookService bookService;


    /**
     * Thống kê dữ liệu ngày trước vào 0 giờ mỗi ngày
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void statistics() {

        //Lấy ngày giờ hôm qua
        Date yesterday = DateUtil.getYesterday();
        //Lấy thời điểm bắt đầu hôm qua
        Date startTime = DateUtil.getDateStartTime(yesterday);
        //Lấy thời điểm kết thúc hôm qua
        Date endTime = DateUtil.getDateEndTime(yesterday);

        //Số tác giả mỗi lần truy vấn
        int needAuthorNumber = 10;
        //Số tác giả thực tế truy vấn được
        int realAuthorNumber;
        //Khoảng thời gian yêu cầu tối đa mỗi lần truy vấn
        Date maxAuthorCreateTime = new Date();
        do {
            //1. Truy vấn danh sách tác giả
            List<Author> authors = authorService.queryAuthorList(needAuthorNumber, maxAuthorCreateTime);
            realAuthorNumber = authors.size();
            for (Author author : authors) {
                maxAuthorCreateTime = author.getCreateTime();
                Long authorId = author.getId();
                Long userId = author.getUserId();
                //2. Truy vấn tác phẩm của tác giả
                List<Book> books = bookService.queryBookList(authorId);

                int buyTotalMember = 0;
                int buyTotalCount = 0;
                int buyTotalAccount = 0;
                List<Long> bookIds = new ArrayList<>(books.size());
                for (Book book : books) {

                    Long bookId = book.getId();

                    //3. Truy vấn số người đăng ký hôm qua cho tác phẩm của tác giả
                    int buyMember = userService.queryBuyMember(bookId, startTime, endTime);

                    int buyCount = 0;

                    int buyAccount = 0;


                    if (buyMember > 0) {
                        //4. Truy vấn số lượt đăng ký hôm qua cho tác phẩm của tác giả
                        buyCount = userService.queryBuyCount(bookId, startTime, endTime);
                        //5. Truy vấn tổng Xu đăng ký hôm qua cho tác phẩm của tác giả
                        buyAccount = userService.queryBuyAccount(bookId, startTime, endTime);
                    }

                    //6. Kiểm tra dữ liệu thu nhập hôm qua của tác phẩm đã được lưu hay chưa
                    boolean isStatistics = authorService.queryIsStatisticsDaily(bookId, yesterday);
                    if (!isStatistics) {
                        //7. Nếu chưa lưu thu nhập hôm qua, lưu thống kê theo từng tác phẩm
                        AuthorIncomeDetail authorIncomeDetail = new AuthorIncomeDetail();
                        authorIncomeDetail.setAuthorId(authorId);
                        authorIncomeDetail.setUserId(userId);
                        authorIncomeDetail.setBookId(bookId);
                        authorIncomeDetail.setIncomeDate(yesterday);
                        authorIncomeDetail.setIncomeNumber(buyMember);
                        authorIncomeDetail.setIncomeCount(buyCount);
                        authorIncomeDetail.setIncomeAccount(buyAccount);
                        authorIncomeDetail.setCreateTime(new Date());
                        authorService.saveDailyIncomeSta(authorIncomeDetail);
                    }


                    buyTotalCount += buyCount;
                    buyTotalAccount += buyAccount;
                    bookIds.add(bookId);

                }

                //8. Kiểm tra dữ liệu thu nhập hôm qua của mọi tác phẩm đã được lưu hay chưa
                boolean isStatistics = authorService.queryIsStatisticsDaily(authorId,0L, yesterday);
                if (!isStatistics) {
                    if (buyTotalCount > 0) {
                        //Nếu tổng lượt đăng ký lớn hơn 0 thì số người đăng ký cũng lớn hơn 0
                        buyTotalMember = userService.queryBuyTotalMember(bookIds, startTime, endTime);
                    }

                    //9. Lưu thống kê thu nhập hôm qua cho mọi tác phẩm của tác giả
                    AuthorIncomeDetail authorIncomeAllDetail = new AuthorIncomeDetail();
                    authorIncomeAllDetail.setAuthorId(authorId);
                    authorIncomeAllDetail.setUserId(userId);
                    authorIncomeAllDetail.setBookId(0L);
                    authorIncomeAllDetail.setIncomeDate(yesterday);
                    authorIncomeAllDetail.setIncomeNumber(buyTotalMember);
                    authorIncomeAllDetail.setIncomeCount(buyTotalCount);
                    authorIncomeAllDetail.setIncomeAccount(buyTotalAccount);
                    authorIncomeAllDetail.setCreateTime(new Date());
                    authorService.saveDailyIncomeSta(authorIncomeAllDetail);
                }

            }

        } while (needAuthorNumber == realAuthorNumber);


    }

}
