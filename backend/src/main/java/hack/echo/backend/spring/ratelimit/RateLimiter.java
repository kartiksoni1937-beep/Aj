package hack.echo.backend.spring.ratelimit;

import hack.echo.backend.spring.task.Task;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter implements Task {

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        BucketEntry entry = buckets.computeIfAbsent(key, ignoredKey -> {
            long ttlMillis = (long) Math.max(window.toMillis() * 1.1, TimeUnit.MINUTES.toMillis(5));
            return new BucketEntry(createBucket(limit, window), ttlMillis);
        });

        entry.lastAccess = System.currentTimeMillis();
        return entry.bucket.tryConsume(1);
    }

    private Bucket createBucket(int limit, Duration window) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillIntervally(limit, window)
                        .build())
                .build();
    }

    @Override
    public void cleanup() {
        buckets.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().lastAccess > e.getValue().ttlMillis);
    }

    private static class BucketEntry {

        final Bucket bucket;
        final long ttlMillis;
        volatile long lastAccess;

        BucketEntry(Bucket bucket, long ttlMillis) {
            this.bucket = bucket;
            this.ttlMillis = ttlMillis;
            this.lastAccess = System.currentTimeMillis();
        }
    }
}