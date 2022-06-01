package labeling;

import utils.MapUtils;

import java.util.Map;
import java.util.Set;

public class ReleaseKeeper {
    private Map<Tag, Integer> releaseMap;
    private static ReleaseKeeper instance = null;

    private ReleaseKeeper() {    }

    public static ReleaseKeeper getInstance() {
        if(instance == null) {
            instance = new ReleaseKeeper();
        }
        return instance;
    }

    public void setReleaseMap(Map<Tag, Integer> releaseMap) {
        this.releaseMap = releaseMap;
    }

    public Map<Tag,Integer> getReleaseMap() {
        return this.releaseMap;
    }

    public Tag getTagFromId(Integer id) {
        return MapUtils.getKeyByValue(releaseMap, id);
    }

    public Integer getIdFromTag(Tag tag) {
        return this.releaseMap.get(tag);
    }

    public Set<Tag> getReleaseKeySet() {
        return this.releaseMap.keySet();
    }

    public Integer getIdFromTagName(String tagName) {
        for(Tag tag : this.getReleaseKeySet()) {
            if(tag.getTagName().equals(tagName)) {
                return this.getIdFromTag(tag);
            }
        }
        return null;
    }
}
