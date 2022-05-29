package utils;

import features.FeatureCalculatorUtils;
import labeling.Tag;
import org.apache.commons.lang3.builder.Diff;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.*;

public class GitUtils {
    public static List<Ref> getTagOrderedByDate(Git git) throws GitAPIException, IOException {
        List<Ref> orderedTagList = git.tagList().call();
        orderedTagList.remove(orderedTagList.size() - 1);
        int n = orderedTagList.size();

        for(int i = 1; i < n - 1; i++) {
            int min = i;
            for(int j = i+1; j < n; j++) {
                try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevCommit commitJ = revWalk.parseCommit(orderedTagList.get(j).getPeeledObjectId());
                    RevCommit commitMin = revWalk.parseCommit(orderedTagList.get(min).getPeeledObjectId());
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

    public static LinkedHashMap<Tag, Integer> getReleaseDate(Git git) throws GitAPIException, IOException {
        LinkedHashMap<Tag, Integer> release = new LinkedHashMap<>();
        List<Ref> tagList = GitUtils.getTagOrderedByDate(git);
        int counter = 1;

        for (Ref tag : tagList) {
            try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                RevCommit commit = revWalk.parseCommit(tag.getPeeledObjectId());
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
}
