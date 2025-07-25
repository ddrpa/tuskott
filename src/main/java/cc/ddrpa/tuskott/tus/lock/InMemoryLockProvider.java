package cc.ddrpa.tuskott.tus.lock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 提供一个基于内存数据结构的锁实现
 */
public class InMemoryLockProvider implements LockProvider {

    private final Set<String> lockSet;

    public InMemoryLockProvider(Map<String, Object> properties) {
        this.lockSet = new ConcurrentSkipListSet<>();
    }

    @Override
    public synchronized boolean acquire(String id) {
        return !lockSet.contains(id);
    }

    @Override
    public void release(String id) {
        lockSet.remove(id);
    }
}