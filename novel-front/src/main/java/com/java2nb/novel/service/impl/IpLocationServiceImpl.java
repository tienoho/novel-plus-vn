package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Lớp triển khai IpLocationService
 *
 * @author xiongxiaoyang
 * @date 2025/6/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpLocationServiceImpl implements IpLocationService {

    private static final Map<String, String> CHINA_REGIONS = Map.ofEntries(
        Map.entry("北京市", "Bắc Kinh"), Map.entry("天津市", "Thiên Tân"),
        Map.entry("上海市", "Thượng Hải"), Map.entry("重庆市", "Trùng Khánh"),
        Map.entry("河北省", "Hà Bắc"), Map.entry("山西省", "Sơn Tây"),
        Map.entry("辽宁省", "Liêu Ninh"), Map.entry("吉林省", "Cát Lâm"),
        Map.entry("黑龙江省", "Hắc Long Giang"), Map.entry("江苏省", "Giang Tô"),
        Map.entry("浙江省", "Chiết Giang"), Map.entry("安徽省", "An Huy"),
        Map.entry("福建省", "Phúc Kiến"), Map.entry("江西省", "Giang Tây"),
        Map.entry("山东省", "Sơn Đông"), Map.entry("河南省", "Hà Nam"),
        Map.entry("湖北省", "Hồ Bắc"), Map.entry("湖南省", "Hồ Nam"),
        Map.entry("广东省", "Quảng Đông"), Map.entry("海南省", "Hải Nam"),
        Map.entry("四川省", "Tứ Xuyên"), Map.entry("贵州省", "Quý Châu"),
        Map.entry("云南省", "Vân Nam"), Map.entry("陕西省", "Thiểm Tây"),
        Map.entry("甘肃省", "Cam Túc"), Map.entry("青海省", "Thanh Hải"),
        Map.entry("台湾省", "Đài Loan"), Map.entry("内蒙古自治区", "Nội Mông"),
        Map.entry("广西壮族自治区", "Quảng Tây"), Map.entry("西藏自治区", "Tây Tạng"),
        Map.entry("宁夏回族自治区", "Ninh Hạ"), Map.entry("新疆维吾尔自治区", "Tân Cương"),
        Map.entry("香港", "Hồng Kông"), Map.entry("香港特别行政区", "Hồng Kông"),
        Map.entry("澳门", "Ma Cao"), Map.entry("澳门特别行政区", "Ma Cao")
    );

    private final Searcher searcher;

    private final Messages messages;

    @Override
    public String getLocation(String ip) {
        try {
            // Ví dụ dữ liệu nguồn: "quốc_gia|0|tỉnh|thành_phố|nhà_mạng"
            String region = searcher.search(ip);
            log.info("IP: {}, khu vực nguồn: {}", ip, region);
            String[] regions = region.split("\\|");
            if (regions.length > 0) {
                // Quốc gia
                String country = regions[0];
                if ("0".equals(country)) {
                    //Với IP nội bộ, lấy IP công khai của máyP
                    String publicIp = IpUtil.getPublicIP();
                    log.info("IP nội bộ: {}, IP công khai của máy: {}", ip, publicIp);
                    if (StringUtils.hasText(publicIp)) {
                        return getLocation(publicIp);
                    }
                } else if ("中国".equals(country)) {
                    //Nếu là Trung Quốc, ưu tiên trả tỉnh (regions[2])）
                    if (regions.length <= 2) {
                        // Nếu thiếu dữ liệu, trả quốc gia
                        return messages.get("location.china");
                    }
                    String province = regions[2];
                    if (!StringUtils.hasText(province) || "0".equals(province)) {
                        // Nếu tỉnh trống hoặc không rõ, trả quốc gia
                        return messages.get("location.china");
                    }
                    return CHINA_REGIONS.getOrDefault(province, messages.get("location.china"));
                } else {
                    // Ngoài Trung Quốc, trả tên quốc gia
                    return containsHan(country) ? messages.get("location.unknown") : country;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return messages.get("location.unknown");
    }

    private boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint ->
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

}
