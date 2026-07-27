package com.java2nb.novel.core.payment;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentAdapterFactory {

    private final Map<Byte, PaymentAdapter> adapterMap;
    private final Map<String, PaymentAdapter> nameMap;

    public PaymentAdapterFactory(List<PaymentAdapter> adapters) {
        Map<Byte, PaymentAdapter> mapByCode = new HashMap<>();
        Map<String, PaymentAdapter> mapByName = new HashMap<>();
        if (adapters != null) {
            for (PaymentAdapter adapter : adapters) {
                mapByCode.put(adapter.getChannelCode(), adapter);
                if (adapter.getChannelName() != null) {
                    mapByName.put(adapter.getChannelName().toUpperCase(), adapter);
                }
            }
        }
        this.adapterMap = Collections.unmodifiableMap(mapByCode);
        this.nameMap = Collections.unmodifiableMap(mapByName);
    }

    public PaymentAdapter getAdapter(byte channelCode) {
        PaymentAdapter adapter = adapterMap.get(channelCode);
        if (adapter == null) {
            throw new IllegalArgumentException("Không tìm thấy PaymentAdapter cho mã kênh: " + channelCode);
        }
        return adapter;
    }

    public Optional<PaymentAdapter> findAdapter(byte channelCode) {
        return Optional.ofNullable(adapterMap.get(channelCode));
    }

    public PaymentAdapter getAdapter(String channelName) {
        if (channelName == null) {
            throw new IllegalArgumentException("Tên kênh thanh toán không được null");
        }
        PaymentAdapter adapter = nameMap.get(channelName.trim().toUpperCase());
        if (adapter == null) {
            throw new IllegalArgumentException("Không tìm thấy PaymentAdapter cho kênh: " + channelName);
        }
        return adapter;
    }

    public Map<Byte, PaymentAdapter> getAllAdapters() {
        return adapterMap;
    }
}
