package cc.ddrpa.tuskott.tus.provider;

public interface LockManager {

    boolean acquireLock(String id);

    void releaseLock(String id);
}