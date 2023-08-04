package net.neoforged.camelot.script.fs;

import net.neoforged.camelot.Database;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.db.transactionals.TricksDAO;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownServiceException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ScriptFileSystemProvider extends FileSystemProvider {

    public static ScriptFileSystemProvider provider() {
        return (ScriptFileSystemProvider) installedProviders().stream()
                .filter(it -> it instanceof ScriptFileSystemProvider)
                .findFirst().orElseThrow();
    }

    private final ScriptFileSystem fs = new ScriptFileSystem(this);

    @Override
    public String getScheme() {
        return "scriptfs";
    }

    public FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        return fs;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return fs;
    }

    @NotNull
    @Override
    public Path getPath(@NotNull URI uri) {
        return fs.getPath(uri.getPath());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        final String trickName = getTrickName(path);
        final String script = Database.main().withExtension(TricksDAO.class, db -> {
            final Integer trickId = db.getTrickByName(trickName);
            if (trickId == null) return null;
            final Trick trick = db.getTrick(trickId);
            if (trick == null) return null;
            return trick.script();
        });
        if (script == null) {
            throw new FileNotFoundException(path.toString());
        }
        return new ByteArrayChannel(script.getBytes(StandardCharsets.UTF_8), true);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return Collections.emptyIterator();
            }

            @Override
            public void close() throws IOException {

            }
        };
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return path.getFileSystem() == path2.getFileSystem() && path.toAbsolutePath().toString().equals(path2.toAbsolutePath().toString());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        for (final AccessMode mode : modes) {
            if (mode == AccessMode.WRITE) {
                throw new UnsupportedOperationException();
            }
        }
        final String trickName = getTrickName(path);
        if (Database.main().withExtension(TricksDAO.class, db -> db
                .getTrickByName(trickName) == null)) {
            throw new FileNotFoundException(path.toString());
        }
    }

    private String getTrickName(Path path) throws FileNotFoundException {
        final String name = path.toAbsolutePath().toString().substring(1); // First char of absolute paths is /
        if (name.endsWith(".js")) {
            return name.substring(0, name.length() - 3);
        }
        throw new FileNotFoundException(name);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return Map.of();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnknownServiceException();
    }

    public static class ByteArrayChannel implements SeekableByteChannel {

        private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
        private byte[] buf;

        /*
         * The current position of this channel.
         */
        private int pos;

        /*
         * The index that is one greater than the last valid byte in the channel.
         */
        private int last;

        private boolean closed;
        private final boolean readonly;

        /*
         * Creates a ByteArrayChannel with its 'pos' at 0 and its 'last' at buf's end.
         * Note: no defensive copy of the 'buf', used directly.
         */
        ByteArrayChannel(byte[] buf, boolean readonly) {
            this.buf = buf;
            this.pos = 0;
            this.last = buf.length;
            this.readonly = readonly;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public long position() throws IOException {
            beginRead();
            try {
                ensureOpen();
                return pos;
            } finally {
                endRead();
            }
        }

        @Override
        public SeekableByteChannel position(long pos) throws IOException {
            beginWrite();
            try {
                ensureOpen();
                if (pos < 0 || pos >= Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Illegal position " + pos);
                this.pos = Math.min((int)pos, last);
                return this;
            } finally {
                endWrite();
            }
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            beginWrite();
            try {
                ensureOpen();
                if (pos == last)
                    return -1;
                final int n = Math.min(dst.remaining(), last - pos);
                dst.put(buf, pos, n);
                pos += n;
                return n;
            } finally {
                endWrite();
            }
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            if (readonly) throw new NonWritableChannelException();
            ensureOpen();
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (readonly) throw new NonWritableChannelException();
            beginWrite();
            try {
                ensureOpen();
                final int n = src.remaining();
                ensureCapacity(pos + n);
                src.get(buf, pos, n);
                pos += n;
                if (pos > last) {
                    last = pos;
                }
                return n;
            } finally {
                endWrite();
            }
        }

        @Override
        public long size() throws IOException {
            beginRead();
            try {
                ensureOpen();
                return last;
            } finally {
                endRead();
            }
        }

        @Override
        public void close() {
            if (closed) return;
            beginWrite();
            try {
                closed = true;
                buf = null;
                pos = 0;
                last = 0;
            } finally {
                endWrite();
            }
        }

        private void ensureOpen() throws IOException {
            if (closed) throw new ClosedChannelException();
        }

        final void beginWrite() {
            rwlock.writeLock().lock();
        }

        final void endWrite() {
            rwlock.writeLock().unlock();
        }

        private void beginRead() {
            rwlock.readLock().lock();
        }

        private void endRead() {
            rwlock.readLock().unlock();
        }

        private void ensureCapacity(int minCapacity) {
            // overflow-conscious code
            if (minCapacity - buf.length > 0) {
                grow(minCapacity);
            }
        }

        /**
         * The maximum size of array to allocate.
         * Some VMs reserve some header words in an array.
         * Attempts to allocate larger arrays may result in
         * OutOfMemoryError: Requested array size exceeds VM limit
         */
        private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        /**
         * Increases the capacity to ensure that it can hold at least the
         * number of elements specified by the minimum capacity argument.
         *
         * @param minCapacity the desired minimum capacity
         */
        private void grow(int minCapacity) {
            // overflow-conscious code
            int oldCapacity = buf.length;
            int newCapacity = oldCapacity << 1;
            if (newCapacity - minCapacity < 0)
                newCapacity = minCapacity;
            if (newCapacity - MAX_ARRAY_SIZE > 0)
                newCapacity = hugeCapacity(minCapacity);
            buf = Arrays.copyOf(buf, newCapacity);
        }

        private static int hugeCapacity(int minCapacity) {
            if (minCapacity < 0) // overflow
                throw new OutOfMemoryError("Required length exceeds implementation limit");
            return (minCapacity > MAX_ARRAY_SIZE) ?
                    Integer.MAX_VALUE :
                    MAX_ARRAY_SIZE;
        }
    }
}
