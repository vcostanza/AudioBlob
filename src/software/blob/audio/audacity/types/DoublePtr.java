package software.blob.audio.audacity.types;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Simulates a *double in C
 */
public class DoublePtr implements Iterable<Double> {

    protected double[] data;
    protected int offset;

    public DoublePtr(double[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public DoublePtr(double[] data) {
        this(data, 0);
    }

    public DoublePtr(int length) {
        this(new double[length]);
    }

    public DoublePtr(DoublePtr other, int offset) {
        this(other.data, other.offset + offset);
    }

    public int remaining() {
        return data.length - offset;
    }

    public void set(int i, double value) {
        this.data[i + offset] = value;
    }

    public void add(int i, double value) {
        this.data[i + offset] += value;
    }

    public void multiply(int i, double value) {
        this.data[i + offset] *= value;
    }

    public void fill(double value, int length) {
        Arrays.fill(data, offset, offset + length, value);
    }

    public void fill(double value) {
        fill(value, remaining());
    }

    public void fill(int fromIndex, int toIndex, double value) {
        Arrays.fill(data, offset + fromIndex, offset + toIndex, value);
    }

    public double get(int i) {
        return this.data[i + offset];
    }

    public void copy(double[] src, int srcPos, int len) {
        System.arraycopy(src, srcPos, data, offset, len);
    }

    public void copy(double[] src, int len) {
        copy(src, 0, len);
    }

    public void copy(DoublePtr src, int len) {
        System.arraycopy(src.data, src.offset, this.data, this.offset, len);
    }

    public void copyInto(double[] dest, int srcPos, int destPos, int len) {
        System.arraycopy(data, offset + srcPos, dest, destPos, len);
    }

    public void copyInto(double[] dest, int destPos, int len) {
        copyInto(dest, 0, destPos, len);
    }

    public void copyInto(double[] dest, int len) {
        copyInto(dest, 0, len);
    }

    public void increment() {
        offset++;
    }

    public void incrementSet(int value) {
        set(0, value);
        increment();
    }

    @Override
    public Iterator<Double> iterator() {
        return new Iterator<Double>() {
            int i = 0;
            @Override
            public boolean hasNext() {
                return i < data.length;
            }
            @Override
            public Double next() {
                return get(i++);
            }
        };
    }
}
