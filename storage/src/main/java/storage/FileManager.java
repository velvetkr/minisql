package storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileManager {
    private final String filename;

    public FileManager(String filename) {
        this.filename = filename;
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new StorageException("cannot create file " + filename, e);
        }
    }

    public String getFilename() {
        return filename;
    }

    public byte[] readPage(int pageId) {
        if (pageId < 0 || pageId >= numPages()) {
            throw new PageNotFoundException("page " + pageId + " out of range [0, " + numPages() + ")");
        }
        byte[] buf = new byte[Constants.PAGE_SIZE];
        try (RandomAccessFile f = new RandomAccessFile(filename, "r")) {
            f.seek((long) pageId * Constants.PAGE_SIZE);
            f.readFully(buf);
        } catch (IOException e) {
            throw new StorageException("read page " + pageId + " failed", e);
        }
        return buf;
    }

    public void writePage(int pageId, byte[] data) {
        if (pageId < 0) {
            throw new PageNotFoundException("invalid page id: " + pageId);
        }
        if (data.length != Constants.PAGE_SIZE) {
            throw new IllegalArgumentException("data length must be " + Constants.PAGE_SIZE + ", got " + data.length);
        }
        try (RandomAccessFile f = new RandomAccessFile(filename, "rw")) {
            f.seek((long) pageId * Constants.PAGE_SIZE);
            f.write(data);
        } catch (IOException e) {
            throw new StorageException("write page " + pageId + " failed", e);
        }
    }

    public int numPages() {
        try {
            long size = Files.size(Paths.get(filename));
            return (int) (size / Constants.PAGE_SIZE);
        } catch (IOException e) {
            throw new StorageException("cannot stat " + filename, e);
        }
    }
}
