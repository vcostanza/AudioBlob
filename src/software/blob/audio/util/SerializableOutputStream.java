package software.blob.audio.util;

import java.io.*;
/**
 * Input stream used for writing primitives into byte data
 */
public class SerializableOutputStream extends OutputStream {

    private final OutputStream stream;
    private final byte[] buf = new byte[8];

    public SerializableOutputStream(OutputStream stream) {
        this.stream = stream;
    }

    public SerializableOutputStream(File file) throws FileNotFoundException {
        this(new FileOutputStream(file));
    }

    public void writeShort(short v) throws IOException {
        buf[0] = (byte) ((v >> 8) & 0xFF);
        buf[1] = (byte) (v & 0xFF);
        write(buf, 0, 2);
    }

    public void writeInt(int v) throws IOException {
        buf[0] = (byte) ((v >> 24) & 0xFF);
        buf[1] = (byte) ((v >> 16) & 0xFF);
        buf[2] = (byte) ((v >> 8) & 0xFF);
        buf[3] = (byte) (v & 0xFF);
        write(buf, 0, 4);
    }

    public void writeLong(long v) throws IOException {
        buf[0] = (byte) ((v >> 56) & 0xFF);
        buf[1] = (byte) ((v >> 48) & 0xFF);
        buf[2] = (byte) ((v >> 40) & 0xFF);
        buf[3] = (byte) ((v >> 32) & 0xFF);
        buf[4] = (byte) ((v >> 24) & 0xFF);
        buf[5] = (byte) ((v >> 16) & 0xFF);
        buf[6] = (byte) ((v >> 8) & 0xFF);
        buf[7] = (byte) (v & 0xFF);
        write(buf, 0, 8);
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void write(int b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        stream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
