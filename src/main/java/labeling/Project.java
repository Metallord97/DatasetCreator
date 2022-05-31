package labeling;

public enum Project {
    BOOKKEEPER("bookkeeper"),
    ZOOKEEPER("zookeeper");

    public final String label;

    Project(String label) {
        this.label = label;
    }
}
