package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.provider.LockProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class MemoryLocker implements LockProvider {

    private static final Set<String> lockSet = new ConcurrentSkipListSet<>();

    @Override
    public synchronized boolean acquire(String id) {
        return !lockSet.contains(id);
    }

    @Override
    public void release(String id) {
        lockSet.remove(id);
    }
}