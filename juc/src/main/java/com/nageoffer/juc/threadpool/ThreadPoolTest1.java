package com.nageoffer.juc.threadpool;

import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  线程池的两大应用场景:
 *  1. 快速响应用户请求
 *  2. 快速处理批量任务
 *
 * 线程池的构造方法如下:
 *  ThreadPoolExecutor(
 *     int corePoolSize,               // 核心线程数
 *     int maximumPoolSize,           // 最大线程数
 *     long keepAliveTime,            // 空闲线程存活时间
 *     TimeUnit unit,                 // 时间单位
 *     BlockingQueue<Runnable> workQueue, // 工作队列 (阻塞队列)
 *     ThreadFactory threadFactory,   // 线程工厂
 *     RejectedExecutionHandler handler // 拒绝策略
 * )
 *
 * 阻塞队列的特点:
 *   当队列为空时，消费者线程会被阻塞，直到有新的元素可供消费。
 *   当队列已满时，生产者线程会被阻塞，直到队列有空间。
 *
 *
 */
public class ThreadPoolTest1 {

    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            6,
            9,
            1024,
            TimeUnit.SECONDS,
            new SynchronousQueue<>());

    @SneakyThrows
    public static void main(String[] args) {
        //串行化
        long startTime1 = System.currentTimeMillis();
        getProductInventory();
        getProductPromotions();
        getProductReviews();
        System.out.println("串行化-查询商品详情耗时：" + (System.currentTimeMillis() - startTime1));

        //使用线程池
        long startTime2 = System.currentTimeMillis();
        List<Future<Object>> results = new ArrayList<>();
        Future<Object> getProductInventory = threadPoolExecutor.submit(ThreadPoolTest1::getProductInventory);
        results.add(getProductInventory);
        Future<Object> getProductPromotions = threadPoolExecutor.submit(ThreadPoolTest1::getProductPromotions);
        results.add(getProductPromotions);
        Future<Object> getProductReviews = threadPoolExecutor.submit(ThreadPoolTest1::getProductReviews);
        results.add(getProductReviews);
        results.add(getProductReviews);
        for (Future<Object> result : results) {
            result.get();
        }
        System.out.println("线程池-查询商品详情耗时：" + (System.currentTimeMillis() - startTime2));
        // 优雅编码，这里如果不进行停止线程池，测试方法不能主动结束
        results.add(getProductReviews);
        for (Future<Object> result : results) {
            result.get();
        }
        System.out.println("查询商品详情耗时：" + (System.currentTimeMillis() - startTime2));
        // 优雅编码，这里如果不进行停止线程池，测试方法不能主动结束
        threadPoolExecutor.shutdown();
    }

    /**
     * 获取商品库存信息
     */
    @SneakyThrows
    private static Object getProductInventory() {
        Thread.sleep(50);
        return new Object();
    }

    /**
     * 获取商品优惠信息
     */
    @SneakyThrows
    private static Object getProductPromotions() {
        Thread.sleep(80);
        return new Object();
    }

    /**
     * 获取商品评论信息
     */
    @SneakyThrows
    private static Object getProductReviews() {
        Thread.sleep(50);
        return new Object();
    }
}
