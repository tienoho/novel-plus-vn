package com.java2nb.novel.controller;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.AlipayProperties;
import com.java2nb.novel.core.utils.ThreadLocalUtil;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 11797
 */
@Controller
@RequestMapping("pay")
@RequiredArgsConstructor
@Slf4j
public class PayController extends BaseController {


    private final AlipayProperties alipayConfig;

    private final OrderService orderService;

    private final Messages messages;


    /**
     * Thanh toán Alipay
     */
    @SneakyThrows
    @PostMapping("aliPay")
    public void aliPay(Integer payAmount, HttpServletRequest request, HttpServletResponse httpResponse) {

        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            //Chưa đăng nhập, chuyển tới trang đăng nhập
            httpResponse.sendRedirect("/user/login.html?originUrl=/pay/index.html");
        } else {
            //Tạo đơn nạp Xu
            Long outTradeNo = orderService.createPayOrder((byte) 1, payAmount, userDetails.getId());
            //Lấy AlipayClient đã khởi tạo
            AlipayClient alipayClient = new DefaultAlipayClient(alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(), alipayConfig.getMerchantPrivateKey(), "json", alipayConfig.getCharset(),
                alipayConfig.getPublicKey(), alipayConfig.getSignType());
            String form;
            if (ThreadLocalUtil.getTemplateDir().contains("mobile")) {
                // Trang di động
                AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();
                alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());
                //Đặt URL trả về và thông báo trong tham số chung
                alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());
                /****** Tham số bắt buộc ******/
                JSONObject bizContent = new JSONObject();
                //Mã đơn của thương nhân, tự đặt và phải duy nhất
                bizContent.put("out_trade_no", outTradeNo);
                //Số tiền thanh toán tối thiểu 0,01 CNY
                bizContent.put("total_amount", payAmount);
                //Tiêu đề đơn hàng không được chứa ký tự đặc biệt
                bizContent.put("subject", messages.get("payment.alipay.subject"));

                /****** Tham số tùy chọn ******/
                //Thanh toán web di động mặc định dùng QUICK_WAP_WAY
                bizContent.put("product_code", "QUICK_WAP_WAY");

                alipayRequest.setBizContent(bizContent.toString());
                AlipayTradeWapPayResponse payResponse = alipayClient.pageExecute(alipayRequest);
                form = payResponse.getBody();
            } else {
                // Trang máy tính
                //Tạo request tương ứng với API
                AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
                alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());
                //Đặt URL trả về và thông báo trong tham số chung
                alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());
                //Điền tham số nghiệp vụ
                alipayRequest.setBizContent("{" +
                    "    \"out_trade_no\":\"" + outTradeNo + "\"," +
                    "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                    "    \"total_amount\":" + payAmount + "," +
                    "    \"subject\":\"" + messages.get("payment.alipay.subject") + "\"" +
                    "  }");
                //Gọi SDK để tạo biểu mẫu
                AlipayTradePagePayResponse payResponse = alipayClient.pageExecute(alipayRequest);
                form = payResponse.getBody();

            }

            httpResponse.setContentType("text/html;charset=utf-8");
            //Xuất trực tiếp biểu mẫu HTML hoàn chỉnh ra trang
            httpResponse.getWriter().write(form);
            httpResponse.getWriter().flush();
            httpResponse.getWriter().close();
        }


    }

    /**
     * Thông báo thanh toán Alipay
     */
    @SneakyThrows
    @RequestMapping("aliPay/notify")
    public void aliPayNotify(HttpServletRequest request, HttpServletResponse httpResponse) {

        PrintWriter out = httpResponse.getWriter();

        //Lấy thông tin POST từ Alipay
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                    : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        //Xác minh chữ ký
        boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getPublicKey(),
            alipayConfig.getCharset(), alipayConfig.getSignType());

        if (signVerified) {
            //Xác minh thành công
            //Mã đơn thương nhân
            String outTradeNo = new String(request.getParameter("out_trade_no").getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);

            //Mã giao dịch Alipay
            String tradeNo = new String(request.getParameter("trade_no").getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);

            //Trạng thái giao dịch
            String tradeStatus = new String(request.getParameter("trade_status").getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);

            if ("TRADE_SUCCESS".equals(tradeStatus)) {
                //Thanh toán thành công
                orderService.updatePayOrder(Long.parseLong(outTradeNo), tradeNo, 1);
            }

            out.println("success");

        } else {//Xác minh thất bại
            out.println("fail");

        }

    }

}
