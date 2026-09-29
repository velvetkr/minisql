package storage;

public class PageNotFoundException extends StorageException {
    public PageNotFoundException(String message) {
        super(message);
    }
}
