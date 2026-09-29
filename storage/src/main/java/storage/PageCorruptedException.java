package storage;

public class PageCorruptedException extends StorageException {
    public PageCorruptedException(String message) {
        super(message);
    }
}
