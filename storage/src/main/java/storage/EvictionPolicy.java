package storage;

/**
 * 替换策略：只负责"决定淘汰谁"，不关心页的数据和磁盘细节。
 * BufferPool 持有帧（pageId -> Entry），顺序维护全部委托给本接口。
 */
public interface EvictionPolicy {

    /** 命中一个已在池中的页。 */
    void onAccess(int pageId);

    /** 一个新页被装入池中。 */
    void onInsert(int pageId);

    /** 一个页离开池（被淘汰或被释放）。 */
    void onRemove(int pageId);

    /** 选出应淘汰的页号；调用前须保证池非空。 */
    int pickVictim();

    /** 策略名（用于统计与日志展示）。 */
    String name();
}
