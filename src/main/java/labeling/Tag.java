package labeling;

import java.util.Date;

public class Tag {
    private Date tagDate;
    private String tagName;

    public Tag(Date tagDate, String tagName) {
        this.tagDate = tagDate;
        this.tagName = tagName;
    }

    public Date getTagDate() {
        return tagDate;
    }

    public void setTagDate(Date tagDate) {
        this.tagDate = tagDate;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
