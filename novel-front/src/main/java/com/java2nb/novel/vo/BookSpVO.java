package com.java2nb.novel.vo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

/**
 * Tham số tìm kiếm tác phẩm
 * @author 11797
 */
@Data
public class BookSpVO {

    @Size(max = 100, message = "{validation.search.keyword.length}")
    private String keyword;

    private Byte workDirection;

    private Integer catId;

    private Byte isVip;

    private Byte bookStatus;

    private Integer wordCountMin;

    private Integer wordCountMax;

    private Date updateTimeMin;

    private Long updatePeriod;

    @Pattern(regexp = "^(last_index_update_time|word_count|visit_count)$")
    private String sort;


}
