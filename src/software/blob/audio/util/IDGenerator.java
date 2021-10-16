package software.blob.audio.util;

/**
 * Incrementing long-based ID generator
 */
public class IDGenerator {

    private long id;

    public IDGenerator() {
        this(0);
    }

    public IDGenerator(long id) {
        setID(id);
    }

    /**
     * Set the current ID for this generator
     * @param id ID number
     */
    public void setID(long id) {
        this.id = id;
    }

    /**
     * Create a new ID
     * @return ID number
     */
    public long createID() {
        return id++;
    }
}
