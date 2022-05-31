package mydatatype;

public class CompositeKey {

    private final String release;
    private final String className;

    public CompositeKey(String release, String className) {
        this.release = release;
        this.className = className;
    }

    @Override
    public String toString() {
        return "(" + this.release + ", " + this.className + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((release == null) ? 0 : release.hashCode());
        result = prime * result + ((className == null) ? 0 : className.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        CompositeKey other = (CompositeKey) obj;
        if (this.release == null) {
            if (other.release != null)
                return false;
        } else if (!this.release.equals(other.release))
            return false;
        if (this.className == null) {
            if (other.className != null)
                return false;
        } else if (!this.className.equals(other.className))
            return false;
        
        return true;
    }


    public String getRelease() {
        return release;
    }

    public String getClassName() {
        return className;
    }
}
