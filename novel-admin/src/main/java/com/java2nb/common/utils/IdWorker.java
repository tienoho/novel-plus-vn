package com.java2nb.common.utils;


import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * <p>Tên: IdWorker.java</p>
 * <p>Mô tả: ID tăng phân tán</p>
 * <pre>
 *     Bản triển khai Snowflake của Twitter bằng Java
 * </pre>
 * Mã lõi nằm trong lớp IdWorker; cấu trúc nguyên lý bên dưới dùng 0 biểu diễn một bit và dấu gạch để tách từng phần:
 * 1||0---0000000000 0000000000 0000000000 0000000000 0 --- 00000 ---00000 ---000000000000
 * Trong chuỗi trên, bit đầu không dùng (có thể là bit dấu của long), 41 bit tiếp theo biểu diễn thời gian mili giây,
 * tiếp theo là 5 bit trung tâm dữ liệu và 5 bit ID máy (thực tế nhận diện luồng),
 * 12 bit cuối là bộ đếm trong mili giây hiện tại; tổng cộng 64 bit, kiểu Long.
 * Cách này tạo thứ tự tăng theo thời gian và tránh va chạm ID trong hệ phân tán nhờ trung tâm dữ liệu và ID máy,
 * đồng thời có hiệu năng cao; thử nghiệm cho thấy Snowflake tạo khoảng 260.000 ID mỗi giây.
 * <p>
 * ID 64 bit (42 thời gian mili giây + 5 ID máy + 5 mã nghiệp vụ + 12 bộ đếm)
 *
 */
public class IdWorker {
    // Mốc thời gian bắt đầu làm chuẩn, thường dùng thời điểm gần đây của hệ thống và không đổi sau khi xác định
    private final static long twepoch = 1288834974657L;
    // Số bit nhận diện máy
    private final static long workerIdBits = 5L;
    // Số bit nhận diện trung tâm dữ liệu
    private final static long datacenterIdBits = 5L;
    // Giá trị ID máy tối đa
    private final static long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // ID trung tâm dữ liệu tối đa
    private final static long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    // Phần tự tăng trong một mili giây
    private final static long sequenceBits = 12L;
    // Dịch trái ID máy 12 bit
    private final static long workerIdShift = sequenceBits;
    // Dịch trái ID trung tâm dữ liệu 17 bit
    private final static long datacenterIdShift = sequenceBits + workerIdBits;
    // Dịch trái thời gian mili giây 22 bit
    private final static long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    private final static long sequenceMask = -1L ^ (-1L << sequenceBits);
    /* Dấu thời gian tạo ID gần nhất */
    private static long lastTimestamp = -1L;
    // 0, kiểm soát đồng thời
    private long sequence = 0L;

    private final long workerId;
    // Phần ID nhận diện dữ liệu
    private final long datacenterId;

    public IdWorker(){
        this.datacenterId = getDatacenterId(maxDatacenterId);
        this.workerId = getMaxWorkerId(datacenterId, maxWorkerId);
    }
    /**
     * @param workerId
     * ID máy làm việc
     * @param datacenterId
     * Số thứ tự
     */
    public IdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }
    /**
     * Lấy ID tiếp theo
     *
     * @return
     */
    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        if (lastTimestamp == timestamp) {
            // Trong cùng mili giây thì tăng bộ đếm
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // Nếu bộ đếm trong mili giây hiện tại đã đầy, chờ sang mili giây tiếp theo
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        // Kết hợp các phần dịch bit để tạo và trả về ID cuối cùng
        long nextId = ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift) | sequence;

        return nextId;
    }

    private long tilNextMillis(final long lastTimestamp) {
        long timestamp = this.timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = this.timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * <p>
     * Lấy maxWorkerId
     * </p>
     */
    protected static long getMaxWorkerId(long datacenterId, long maxWorkerId) {
        StringBuffer mpid = new StringBuffer();
        mpid.append(datacenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (!name.isEmpty()) {
            /*
             * GET jvmPid
             */
            mpid.append(name.split("@")[0]);
        }
        /*
         * Lấy 16 bit thấp từ mã băm MAC + PID
         */
        return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }

    /**
     * <p>
     * Phần ID nhận diện dữ liệu
     * </p>
     */
    protected static long getDatacenterId(long maxDatacenterId) {
        long id = 0L;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 1])
                        | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                id = id % (maxDatacenterId + 1);
            }
        } catch (Exception e) {
            System.out.println(" getDatacenterId: " + e.getMessage());
        }
        return id;
    }


}
