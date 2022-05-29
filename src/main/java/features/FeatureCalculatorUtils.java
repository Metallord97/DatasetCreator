package features;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FeatureCalculatorUtils {
    public static InputStream readFileFromCommit (Repository repository, String commitID, String filepath) throws IOException {
        InputStream inputStream;
        ObjectId commit = repository.resolve(commitID);
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit revCommit = revWalk.parseCommit(commit);
            RevTree revTree = revCommit.getTree();
            System.out.println("having tree: " + revTree);

            try(TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(revTree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filepath));
                if (!treeWalk.next()) {
                    throw new IllegalStateException("Did not find expected file:" + filepath);
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                loader.copyTo(byteArrayOutputStream);
                inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            }
            revWalk.dispose();
        }
        return inputStream;
    }

    /**
     *
     * @param path
     * @return questo metodo ritorna True se la stringa passata in input è un file con estensione .java e non contiene la sottostringa "/test" o "Test"
     */
    public static boolean isPathValid (String path) {
        String extension = StringUtils.getExtension(path);
        return (extension.equals("java") && !StringUtils.hasMatchingSubstring(path, "/test", "Test"));
    }

    public static Iterable<RevCommit> getAllCommitsOfARelease(Git git, Ref from, Ref to) throws IncorrectObjectTypeException, MissingObjectException, GitAPIException {
        ObjectId fromId = from.getPeeledObjectId();
        ObjectId toId = to.getPeeledObjectId();
        return git.log().addRange(fromId, toId).call();
    }

    public static List<String> getAllFileOfTheRelease(Git git, Ref from, Ref to) throws IOException, GitAPIException {
        List<String> classList = new ArrayList<>();
        Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, from, to);
        RevCommit lastCommit = null;
        for(RevCommit commit : commits) {
            lastCommit = commit;
        }
        if (lastCommit != null) {
            ObjectId treeId = lastCommit.getTree().getId();
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.reset(treeId);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (FeatureCalculatorUtils.isPathValid(path)) {
                        classList.add(path);
                    }
                }
            }
        }

        return classList;
    }

}
