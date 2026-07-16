package com.java2nb.novel.core.schedule;


import com.java2nb.novel.core.config.AuthorIncomeProperties;
import com.java2nb.novel.core.utils.DateUtil;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.entity.AuthorIncome;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
public class MonthIncomeStaSchedule {

    private final AuthorService authorService;

    private final BookService bookService;

    private final AuthorIncomeProperties authorIncomeConfig;

    /**
     * Thống kê dữ liệu tháng trước lúc 2 giờ ngày đầu tháng
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void statistics() {

        //Lấy thời điểm bắt đầu và kết thúc tháng trước
        Date startTime = DateUtil.getLastMonthStartTime();
        Date endTime = DateUtil.getLastMonthEndTime();

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

                long totalPreTaxIncome = 0L;
                long totalAfterTaxIncome = 0L;
                for (Book book : books) {

                    Long bookId = book.getId();

                    //3. Nếu chưa lưu thống kê thu nhập tháng, lưu theo từng tác phẩm
                    Long monthIncome = authorService.queryTotalAccount(userId, bookId, startTime, endTime);

                    BigDecimal monthIncomeShare = new BigDecimal(monthIncome)
                        .multiply(authorIncomeConfig.getShareProportion());
                    long preTaxIncome = monthIncomeShare
                        .multiply(authorIncomeConfig.getExchangeProportion())
                        .multiply(new BigDecimal(100))
                        .longValue();

                    totalPreTaxIncome += preTaxIncome;

                    long afterTaxIncome = monthIncomeShare
                        .multiply(authorIncomeConfig.getTaxRate())
                        .multiply(authorIncomeConfig.getExchangeProportion())
                        .multiply(new BigDecimal(100))
                        .longValue();

                    totalAfterTaxIncome += afterTaxIncome;

                    //4. Kiểm tra thống kê thu nhập tháng đã được lưu hay chưa
                    if (monthIncome > 0 && !authorService.queryIsStatisticsMonth(bookId, endTime)) {
                        AuthorIncome authorIncome = new AuthorIncome();
                        authorIncome.setAuthorId(authorId);
                        authorIncome.setUserId(userId);
                        authorIncome.setBookId(bookId);
                        authorIncome.setPreTaxIncome(preTaxIncome);
                        authorIncome.setAfterTaxIncome(afterTaxIncome);
                        authorIncome.setIncomeMonth(endTime);
                        authorIncome.setCreateTime(new Date());

                        authorService.saveAuthorIncomeSta(authorIncome);
                    }


                }

                if (totalPreTaxIncome > 0 && !authorService.queryIsStatisticsMonth(authorId, 0L, endTime)) {

                    AuthorIncome authorIncome = new AuthorIncome();
                    authorIncome.setAuthorId(authorId);
                    authorIncome.setUserId(userId);
                    authorIncome.setBookId(0L);
                    authorIncome.setPreTaxIncome(totalPreTaxIncome);
                    authorIncome.setAfterTaxIncome(totalAfterTaxIncome);
                    authorIncome.setIncomeMonth(endTime);
                    authorIncome.setCreateTime(new Date());

                    authorService.saveAuthorIncomeSta(authorIncome);
                }


            }

        } while (needAuthorNumber == realAuthorNumber);


    }

}
