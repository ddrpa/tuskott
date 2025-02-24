package cc.ddrpa.tuskott.tus.provider;

public interface LockProvider {

    /**
     * 获取锁
     *
     * @param id
     * @return
     */
    boolean acquire(String id);

    /**
     * 释放锁
     *
     * @param id
     */
    void release(String id);
}