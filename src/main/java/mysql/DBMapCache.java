package mysql;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.NonNull;

public abstract class DBMapCache<T, U> extends DBCache {

    private final LoadingCache<T, U> cache;

    protected CacheBuilder<Object, Object> getCacheBuilder() {
        return CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10));
    }

    protected U process(T t) throws Exception {
        return DBMapCache.this.load(t);
    }

    protected DBMapCache() {
        cache = getCacheBuilder().build(
                new CacheLoader<>() {
                    @Override
                    public U load(@NonNull T t) throws Exception {
                        return process(t);
                    }
                }
        );
    }

    protected abstract U load(T t) throws Exception;

    public U retrieve(T t) {
        try {
            return cache.get(t);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected LoadingCache<T, U> getCache() {
        return cache;
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

}
