package com.nageoffer.juc.threadpool;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolStateExample {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

        // 提交一些任务
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 打印线程池状态
        System.out.println("Is Shutdown: " + executor.isShutdown());
        System.out.println("Is Terminating: " + executor.isTerminating());
        System.out.println("Is Terminated: " + executor.isTerminated());

        // 关闭线程池
        executor.shutdown();
        System.out.println("Is Shutdown: " + executor.isShutdown());

        // 等待线程池终止
        if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
            System.out.println("Is Terminated: " + executor.isTerminated());
        }

        // 尝试在 TIDYING 或 TERMINATED 状态提交新任务
        try {
            executor.execute(() -> System.out.println("New task"));
        } catch (RejectedExecutionException e) {
            System.out.println("Task rejected: " + e.getMessage());
        }
    }
}
