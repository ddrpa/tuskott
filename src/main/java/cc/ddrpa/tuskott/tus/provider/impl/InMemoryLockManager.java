package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.provider.LockManager;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class InMemoryLockManager implements LockManager {

    private static final Set<String> lockSet = new ConcurrentSkipListSet<>();

    @Override
    public synchronized boolean acquireLock(String id) {
        return !lockSet.contains(id);
    }

    @Override
    public void releaseLock(String id) {
        lockSet.remove(id);
    }
}