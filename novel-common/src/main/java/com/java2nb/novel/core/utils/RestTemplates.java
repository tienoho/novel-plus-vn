package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.config.HttpProxyProperties;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

@Component
public class RestTemplates {

    private static HttpProxyProperties httpProxyProperties;

    RestTemplates(HttpProxyProperties properties) {
        httpProxyProperties = properties;
    }

    @SneakyThrows
    public static RestTemplate newInstance(String charset) {

        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        //Bỏ qua chứng chỉ
        SSLContext sslContext = SSLContexts.custom()
            .loadTrustMaterial(null, acceptingTrustStrategy)
            .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", csf)
            .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);

        //Số kết nối tối đa của pool; 0 là không giới hạn nhưng cần cân nhắc rò rỉ kết nối
        connectionManager.setMaxTotal(1000);
        //Số kết nối tối đa mỗi route; nếu chỉ gọi một địa chỉ có thể đặt bằng tổng kết nối tối đa
        connectionManager.setDefaultMaxPerRoute(300);

        HttpClientBuilder clientBuilder = HttpClients.custom();
        // Tắt quản lý Cookie
        clientBuilder.disableCookieManagement();
        if (Objects.nonNull(httpProxyProperties) && Boolean.TRUE.equals(httpProxyProperties.getEnabled())) {
            HttpHost proxy = new HttpHost(httpProxyProperties.getIp(), httpProxyProperties.getPort());
            clientBuilder.setProxy(proxy);
            if (StringUtils.isNotBlank(httpProxyProperties.getUsername()) && StringUtils.isNotBlank(
                httpProxyProperties.getPassword())) {
                // Tạo CredentialsProvider và thêm thông tin xác thực proxy
                BasicCredentialsProvider provider = new BasicCredentialsProvider();
                UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                    httpProxyProperties.getUsername(), httpProxyProperties.getPassword().toCharArray());
                provider.setCredentials(new AuthScope(null, -1), credentials);
                clientBuilder.setDefaultCredentialsProvider(provider);
            }
        }
        CloseableHttpClient httpClient = clientBuilder.setConnectionManager(connectionManager)
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory();

        requestFactory.setHttpClient(httpClient);
        requestFactory.setConnectionRequestTimeout(3000);
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(30000);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        List<HttpMessageConverter<?>> list = restTemplate.getMessageConverters();
        for (HttpMessageConverter<?> httpMessageConverter : list) {
            if (httpMessageConverter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) httpMessageConverter).setDefaultCharset(Charset.forName(charset));
                break;
            }
        }
        return restTemplate;
    }

}
