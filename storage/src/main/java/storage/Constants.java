package storage;

public final class Constants {
    public static final int PAGE_SIZE = 4096;
    public static final String DB_FILENAME = "data.db";
    public static final int DEFAULT_BUFFER_SIZE = 10;

    public static final int SUPER_BLOCK_PAGE_ID = 0;
    public static final byte[] MAGIC = {'S', 'T', 'O', 'R'};
    public static final int VERSION = 1;
    public static final int FREE_LIST_END = -1;

    private Constants() {
    }
}
