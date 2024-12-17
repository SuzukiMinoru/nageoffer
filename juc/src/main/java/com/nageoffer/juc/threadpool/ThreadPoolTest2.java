package com.nageoffer.juc.threadpool;
import lombok.SneakyThrows;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTest2 {
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            10,
            10,
            1024,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100000));

    public static void main(String[] args) {
        List<String> phones = List.of("1560116xxxx", "1560116xxxx", "1560116xxxx", "1560116xxxx");
        for (String phone : phones) {
            sendPhoneSms(phone);
        }
    }

    /**
     * 发送手机短信流程
     */
    @SneakyThrows
    private static void sendPhoneSms(String phone) {
        Thread.sleep(50);
    }
}
