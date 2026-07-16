package com.java2nb.common.utils;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Xử lý ngày
 */
public class DateUtils {
    private final static Logger logger = LoggerFactory.getLogger(DateUtils.class);
    public final static String YEAR_PATTERN = "yyyy";
    public final static String MONTH_PATTERN = "MM";
    public final static String DAY_PATTERN = "dd";
    /**
     * Định dạng ngày (yyyy-MM-dd)
     */
    public final static String DATE_PATTERN = "yyyy-MM-dd";
    /**
     * Định dạng thời gian (yyyy-MM-dd HH:mm:ss)
     */
    public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static String format(Date date) {
        return format(date, DATE_PATTERN);
    }

    public static String format(Date date, String pattern) {
        if (date != null) {
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            return df.format(date);
        }
        return null;
    }

    /**
     * Tính khoảng thời gian đến hiện tại, gần đúng
     *
     * @param date
     * @return
     */
    public static String getTimeBefore(Date date) {
        Date now = new Date();
        long l = now.getTime() - date.getTime();
        long day = l / (24 * 60 * 60 * 1000);
        long hour = (l / (60 * 60 * 1000) - day * 24);
        long min = ((l / (60 * 1000)) - day * 24 * 60 - hour * 60);
        long s = (l / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
        String r = "";
        if (day > 0) {
            r += day + Messages.getDefault("time.unit.day");
        } else if (hour > 0) {
            r += hour + Messages.getDefault("time.unit.hour");
        } else if (min > 0) {
            r += min + Messages.getDefault("time.unit.minute");
        } else if (s > 0) {
            r += s + Messages.getDefault("time.unit.second");
        }
        r += Messages.getDefault("time.ago");
        return r;
    }

    /**
     * Tính khoảng thời gian đến hiện tại, chính xác
     *
     * @param date
     * @return
     */
    public static String getTimeBeforeAccurate(Date date) {
        Date now = new Date();
        long l = now.getTime() - date.getTime();
        long day = l / (24 * 60 * 60 * 1000);
        long hour = (l / (60 * 60 * 1000) - day * 24);
        long min = ((l / (60 * 1000)) - day * 24 * 60 - hour * 60);
        long s = (l / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
        String r = "";
        if (day > 0) {
            r += day + Messages.getDefault("time.unit.day");
        }
        if (hour > 0) {
            r += hour + Messages.getDefault("time.unit.hour");
        }
        if (min > 0) {
            r += min + Messages.getDefault("time.unit.minute");
        }
        if (s > 0) {
            r += s + Messages.getDefault("time.unit.second");
        }
        r += Messages.getDefault("time.ago");
        return r;
    }

    /**
     * Lấy ngày cách đây một số ngày
     *
     * @param past
     * @return
     */
    @SneakyThrows
    public static String getPastDate(int past,Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) - past);
        Date today = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(today);
    }

    /**
     * Lấy tập ngày trong các ngày trước
     *
     * @param past
     * @return
     */
    public static List<String> getDateList(int past,Date date) {
        List<String> result = new ArrayList<>(past);
        for(int i = past - 1 ; i > 0 ; i--){
            result.add(getPastDate(i,date));
        }
        //Ngày hôm nay
        result.add(new SimpleDateFormat("yyyy-MM-dd").format(date));
        return result;

    }
}
