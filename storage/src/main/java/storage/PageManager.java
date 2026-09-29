package storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class PageManager {
    private static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    private final FileManager fm;

    public PageManager(FileManager fm) {
        this.fm = fm;
        if (fm.numPages() == 0) {
            writeSuperBlock(Constants.FREE_LIST_END);
        } else {
            readHead();
        }
    }

    private int readHead() {
        byte[] page = fm.readPage(Constants.SUPER_BLOCK_PAGE_ID);
        ByteBuffer bb = ByteBuffer.wrap(page).order(ORDER);
        byte[] magic = new byte[4];
        bb.get(magic);
        bb.getInt(); // version
        int head = bb.getInt();
        if (!Arrays.equals(magic, Constants.MAGIC)) {
            throw new PageCorruptedException("bad magic in " + fm.getFilename());
        }
        return head;
    }

    private void writeSuperBlock(int head) {
        byte[] page = new byte[Constants.PAGE_SIZE];
        ByteBuffer bb = ByteBuffer.wrap(page).order(ORDER);
        bb.put(Constants.MAGIC);
        bb.putInt(Constants.VERSION);
        bb.putInt(head);
        fm.writePage(Constants.SUPER_BLOCK_PAGE_ID, page);
    }

    public int allocatePage() {
        int head = readHead();
        if (head != Constants.FREE_LIST_END) {
            int nextFree = ByteBuffer.wrap(fm.readPage(head)).order(ORDER).getInt();
            writeSuperBlock(nextFree);
            fm.writePage(head, new byte[Constants.PAGE_SIZE]);
            return head;
        }
        int pageId = fm.numPages();
        fm.writePage(pageId, new byte[Constants.PAGE_SIZE]);
        return pageId;
    }

    public void freePage(int pageId) {
        if (pageId <= Constants.SUPER_BLOCK_PAGE_ID || pageId >= fm.numPages()) {
            throw new PageNotFoundException("cannot free page " + pageId);
        }
        int head = readHead();
        byte[] page = new byte[Constants.PAGE_SIZE];
        ByteBuffer.wrap(page).order(ORDER).putInt(head);
        fm.writePage(pageId, page);
        writeSuperBlock(pageId);
    }

    public byte[] readPage(int pageId) {
        return fm.readPage(pageId);
    }

    public void writePage(int pageId, byte[] data) {
        fm.writePage(pageId, data);
    }

    public int numPages() {
        return fm.numPages();
    }
}
