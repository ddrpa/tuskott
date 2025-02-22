package cc.ddrpa.tuskott.tus.provider;

public interface LockProvider {

    boolean acquire(String id);

    void release(String id);
}