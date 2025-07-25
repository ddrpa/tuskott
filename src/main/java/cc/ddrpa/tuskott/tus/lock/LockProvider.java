package cc.ddrpa.tuskott.tus.lock;

public interface LockProvider {

    boolean acquire(String id);

    void release(String id);
}