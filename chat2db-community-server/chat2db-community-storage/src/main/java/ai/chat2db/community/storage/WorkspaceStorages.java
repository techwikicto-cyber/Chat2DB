package ai.chat2db.community.storage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * One storage instance per workspace, built on first use and then kept.
 *
 * These classes hold their whole file in memory, so an instance per account is
 * the point rather than an overhead: it is what keeps one account's rows out of
 * another's map. Cached because rebuilding one means re-reading its file, and
 * keyed by directory rather than by account name so the shared workspace and a
 * named one are simply two different keys.
 */
public final class WorkspaceStorages<T> {

    /** Every registry, so migration can drop what it has invalidated in one call. */
    private static final List<WorkspaceStorages<?>> ALL = new CopyOnWriteArrayList<>();

    private final Function<String, T> factory;
    private final Map<String, T> byBasePath = new ConcurrentHashMap<>();

    public WorkspaceStorages(Function<String, T> factory) {
        this.factory = factory;
        ALL.add(this);
    }

    /**
     * Forgets every cached instance across every storage type. Called once the
     * workspace directories have been moved, since an instance built before the
     * move still holds the old contents and writes to a path that no longer
     * exists.
     */
    public static void evictEverything() {
        ALL.forEach(WorkspaceStorages::evictAll);
    }

    /** The instance for the workspace of the request being served. */
    public T current() {
        return forBasePath(WorkspaceScope.currentBasePath());
    }

    public T forBasePath(String basePath) {
        return byBasePath.computeIfAbsent(basePath, factory);
    }

    /**
     * Drops the cached instances, so the next call re-reads from disk. Used
     * after the workspace directories move underneath us during migration.
     */
    public void evictAll() {
        byBasePath.clear();
    }
}
