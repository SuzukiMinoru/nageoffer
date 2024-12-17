package com.nageoffer.juc.threadpool.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorsThreadPool {

    public static void main(String[] args) {
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        ThreadPoolExecutor singleThreadExecutor = (ThreadPoolExecutor) Executors.newSingleThreadExecutor();
        ThreadPoolExecutor cachedThreadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();


        try {
            threadPool.execute(() -> {
                System.out.println("执行");
            });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
}
