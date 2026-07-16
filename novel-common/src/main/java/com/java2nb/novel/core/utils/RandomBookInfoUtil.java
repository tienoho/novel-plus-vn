package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.i18n.Messages;

import java.util.Random;

/**
 * Tiện ích tạo ngẫu nhiên thông tin tác phẩm
 * @author Administrator
 */
public class RandomBookInfoUtil {

    /**
     * Lấy số lượt truy cập theo điểm đánh giá
     * */
    public static Long getVisitCountByScore(Float score){
        Long visitCount ;

        if(score > 9){
            visitCount = 100000 + new Random(100000).nextLong();
        }else if(score > 8){
            visitCount = 10000 + new Random(10000).nextLong();
        }else if(score > 7){
            visitCount = 1000 + new Random(1000).nextLong();
        }else if(score > 6){
            visitCount = 100 + new Random(100).nextLong();
        }else{
            visitCount = new Random(100).nextLong();
        }


        return  visitCount;

    }

    /**
     * Lấy điểm đánh giá theo số lượt truy cập
     * */
    public static Float getScoreByVisitCount(Long visitCount){
        Float score;
        if(visitCount>100000) {
            score = 8.9f;
        }else if(visitCount>10000){
            score = 8.0f+(visitCount/10000)*0.1f;
        }else if(visitCount>1000){
            score = 7.0f+(visitCount/1000)*0.1f;
        }else if(visitCount>100){
            score = 6.0f+(visitCount/100)*0.1f;
        }else{
            score = 6.0f;
        }
        return score;
    }
    /**
     * Lấy tên danh mục
     * */
    public static String getCatNameById(Integer catId) {
        String catName = Messages.getDefault("book.category.other");

        switch (catId) {
            case 1: {
                catName = Messages.getDefault("book.category.fantasy");
                break;
            }
            case 2: {
                catName = Messages.getDefault("book.category.wuxia");
                break;
            }
            case 3: {
                catName = Messages.getDefault("book.category.urbanRomance");
                break;
            }
            case 4: {
                catName = Messages.getDefault("book.category.historyMilitary");
                break;
            }
            case 5: {
                catName = Messages.getDefault("book.category.scifiSupernatural");
                break;
            }
            case 6: {
                catName = Messages.getDefault("book.category.gameSports");
                break;
            }
            case 7: {
                catName = Messages.getDefault("book.category.women");
                break;
            }
            case 8: {
                catName = Messages.getDefault("book.category.lightNovel");
                break;
            }
            case 9: {
                catName = Messages.getDefault("book.category.comic");
                break;
            }
            default: {
                break;
            }


        }
        return catName;
    }
}
