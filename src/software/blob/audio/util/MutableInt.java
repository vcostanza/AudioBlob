package software.blob.audio.util;

/**
 * An integer that can be modified without changing the instance
 */
public class MutableInt {

    private int value;

    public MutableInt(int value) {
        this.value = value;
    }

    public MutableInt() {
        this(0);
    }

    public int get() {
        return value;
    }

    public void set(int value) {
        this.value = value;
    }

    public int increment() {
        return ++value;
    }

    public int decrement() {
        return --value;
    }
}
