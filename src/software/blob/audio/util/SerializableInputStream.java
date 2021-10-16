package software.blob.audio.util;

import java.io.*;

/**
 * Input stream used for reading primitives from byte data
 */
public class SerializableInputStream extends InputStream {

    private final InputStream stream;
    private final byte[] buf = new byte[8];

    public SerializableInputStream(InputStream stream) {
        this.stream = stream;
    }

    public SerializableInputStream(File file) throws FileNotFoundException {
        this(new FileInputStream(file));
    }

    public short readShort() throws IOException {
        if (read(buf, 0, 2) < 2)
            throw new IOException("Failed to read short (not enough bytes)");
        return (short) (shift(buf[0], 8) | shift(buf[1], 0));
    }

    public int readInt() throws IOException {
        if (read(buf, 0, 4) < 4)
            throw new IOException("Failed to read integer (not enough bytes)");
        return (int) (shift(buf[0], 24)
                | shift(buf[1], 16)
                | shift(buf[2], 8)
                | shift(buf[3], 0));
    }

    public long readLong() throws IOException {
        int read = read(buf, 0, 8);
        if (read < 8)
            throw new IOException("Failed to read long (not enough bytes)");
        return shift(buf[0], 56)
                | shift(buf[1], 48)
                | shift(buf[2], 40)
                | shift(buf[3], 32)
                | shift(buf[4], 24)
                | shift(buf[5], 16)
                | shift(buf[6], 8)
                | shift(buf[7], 0);
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    private long shift(byte b, int amount) {
        long l = b;
        if (l < 0)
            l += 256;
        return l << amount;
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return stream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return stream.available();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        stream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    @Override
    public synchronized void reset() throws IOException {
        stream.reset();
    }
}
