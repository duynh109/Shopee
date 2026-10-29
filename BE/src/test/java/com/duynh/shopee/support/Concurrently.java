package com.duynh.shopee.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Chạy n việc cùng một lúc để thử tranh chấp (hai người mua món cuối, hai request đổi cùng một token...).
 * - Mỗi việc chạy trên một luồng riêng; các luồng chờ ở vạch xuất phát rồi được thả ra cùng lúc, nên request
 *   đến server gần như đồng thời — giống người thật bấm cùng lúc hơn là gửi lần lượt trong vòng lặp.
 * - Trả về kết quả theo đúng thứ tự việc (việc thứ i ở vị trí i).
 */
public final class Concurrently {
    private Concurrently() {
    }

    public static <T> List<T> run(int n, IntFunction<T> task) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.apply(index);
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }
}
