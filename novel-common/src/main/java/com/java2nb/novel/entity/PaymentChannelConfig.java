package com.java2nb.novel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentChannelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Byte channelCode;
    private String channelName;
    private Boolean isEnabled;
    private String configJson;
    private Date createTime;
    private Date updateTime;
}
