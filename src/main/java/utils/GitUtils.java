package utils;

import features.FeatureCalculatorUtils;
import labeling.Tag;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.*;

public class GitUtils {
    private GitUtils() {}
    public static List<Ref> getTagOrderedByDate(Git git) throws GitAPIException, IOException {
        List<Ref> orderedTagList = git.tagList().call();
        orderedTagList.remove(orderedTagList.size() - 1);
        int n = orderedTagList.size();

        for(int i = 1; i < n - 1; i++) {
            int min = i;
            for(int j = i+1; j < n; j++) {
                try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevCommit commitJ = revWalk.parseCommit(GitUtils.getObjectIdFromRef(orderedTagList.get(j)));
                    RevCommit commitMin = revWalk.parseCommit(GitUtils.getObjectIdFromRef(orderedTagList.get(min)));
                    Date dateJ = commitJ.getAuthorIdent().getWhen();
                    Date dateMin = commitMin.getAuthorIdent().getWhen();
                    if(dateJ.before(dateMin)) {
                        min = j;
                    }
                }
            }
            if(min != i) {
                Collections.swap(orderedTagList, min, i);
            }
        }
        return orderedTagList;
    }

    public static Map<Tag, Integer> getReleaseDate(Git git) throws GitAPIException, IOException {
        Map<Tag, Integer> release = new LinkedHashMap<>();
        List<Ref> tagList = GitUtils.getTagOrderedByDate(git);
        int counter = 1;

        for (Ref tag : tagList) {
            try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                RevCommit commit = revWalk.parseCommit(GitUtils.getObjectIdFromRef(tag));
                Date tagDate = commit.getAuthorIdent().getWhen();
                Tag key = new Tag(tagDate, StringUtils.removeSubstring(tag.getName(), "refs/tags/"));
                release.put(key, counter);
                counter += 1;
            }
        }
        return release;
    }

    public static List<String> getDiffClasses(Git git, String tickedID) throws GitAPIException, IOException {
        List<String> classList = new ArrayList<>();
        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFormatter.setRepository(git.getRepository());

        Iterable<RevCommit> commits = git.log().call();
        for(RevCommit commit : commits) {
            if (commit.getParentCount() == 0) continue;
            String shortMessage = commit.getShortMessage();
            if(shortMessage.contains(tickedID)) {
                List<DiffEntry> diffEntries = diffFormatter.scan(commit.getParent(0), commit);
                for (DiffEntry diffEntry : diffEntries) {
                    String path = diffEntry.getNewPath();
                    if(!FeatureCalculatorUtils.isPathValid(path)) continue;
                    classList.add(path);
                }
            }
        }

        return classList;
    }

    public static DiffFormatter getDiffFormatter(Git git) {
        try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(git.getRepository());
            return diffFormatter;
        }
    }

    public static ObjectId getObjectIdFromRef(Ref ref) {
        if(ref.getPeeledObjectId() != null) {
            /* Annotated Tag */
            return ref.getPeeledObjectId();
        }
        else {
            /* Lightweight Tag */
            return ref.getObjectId();
        }
    }
}
