package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.i18n.Messages;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Tiện ích ngày tháng
 * @author cd
 */
public class DateUtil {

    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * Lấy ngày giờ hôm qua
     * */
    public static Date getYesterday(){
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        return calendar.getTime();
    }

    /**
     * Lấy thời điểm bắt đầu ngày theo ngày chỉ định
     * */
    public static Date getDateStartTime(Date date){
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        /*
        * Calendar.HOUR_OF_DAY biểu thị giờ theo hệ 24 giờ, phạm vi 0-23;
        * Calendar.HOUR biểu thị giờ theo hệ 12 giờ, phạm vi 0-12; nửa đêm và trưa là 0;
        * Cần dùng cùng Calendar.AM_PM;
        * */
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTime();
    }

    /**
     * Lấy thời điểm cuối ngày theo ngày chỉ định
     * */
    public static Date getDateEndTime(Date date){
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY,23);
        calendar.set(Calendar.MINUTE,59);
        calendar.set(Calendar.SECOND,59);
        calendar.set(Calendar.MILLISECOND,999);
        return calendar.getTime();
    }

    /**
     * Lấy thời điểm bắt đầu tháng trước
     *
     * @return
     */
    public static Date getLastMonthStartTime(){
        // Lấy ngày hiện tại
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 0);
        calendar.add(Calendar.MONTH, -1);
        // Đặt ngày thành 1 để lấy ngày đầu tháng hiện tại
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    /**
     * Lấy thời điểm kết thúc tháng trước
     *
     * @return
     */
    public static Date getLastMonthEndTime(){
        // Lấy ngày hiện tại
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 0);
        calendar.add(Calendar.MONTH, -1);
        // Lấy ngày cuối tháng hiện tại
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);

        return calendar.getTime();
    }


    /**
     * Định dạng ngày
     * */
    public static String formatDate(Date date,String patten){

        return new SimpleDateFormat(patten).format(date);
    }


    /**
     * Định dạng ngày theo kiểu thời gian đã trôi qua
     * */
    public static String formatTimeAgo(Date date){
        if (date == null) {
            return null;
        }

        long now = new Date().getTime();
        long then = date.getTime();

        long diff = now - then;

        if (diff < 0) {
            // Thời gian trong tương lai
            DateUtil.formatDate(date, DateUtil.DATE_TIME_PATTERN);
        }

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long months = days / 30;
        long years = months / 12;

        if (seconds < 60) {
            return Messages.getDefault("time.justNow");
        } else if (minutes < 60) {
            return Messages.getDefault("time.minutesAgo", minutes);
        } else if (hours < 24) {
            return Messages.getDefault("time.hoursAgo", hours);
        } else if (days < 30) {
            return Messages.getDefault("time.daysAgo", days);
        } else if (months < 12) {
            return Messages.getDefault("time.monthsAgo", months);
        } else {
            return Messages.getDefault("time.yearsAgo", years);
        }
    }


    public static void main(String[] args) {
        System.out.println(formatDate(getYesterday(),DATE_TIME_PATTERN));
        System.out.println(formatDate(getDateStartTime(getYesterday()),DATE_TIME_PATTERN));
        System.out.println(formatDate(getDateEndTime(getYesterday()),DATE_TIME_PATTERN));
        System.out.println(formatDate(getLastMonthStartTime(),DATE_TIME_PATTERN));
        System.out.println(formatDate(getLastMonthEndTime(),DATE_TIME_PATTERN));
    }



}
