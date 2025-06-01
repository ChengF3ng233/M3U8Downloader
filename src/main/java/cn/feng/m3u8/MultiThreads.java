package cn.feng.m3u8;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ChengFeng
 * @since 2024/3/23
 **/
class MultiThreads {
    public static ExecutorService videoExecutor;
    public static ExecutorService tsExecutor;

    public static void shutdown() {
        videoExecutor.shutdown();
        tsExecutor.shutdown();
    }

    public static void launch(int videoThreads, int tsThreads) {
        videoExecutor = Executors.newFixedThreadPool(videoThreads);
        tsExecutor = Executors.newFixedThreadPool(tsThreads);
    }
}
