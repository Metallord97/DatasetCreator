package features;

import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
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
import utils.StringConstant;
import utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FeatureCalculatorUtils {
    private FeatureCalculatorUtils() {}

    public static InputStream readFileFromCommit (Repository repository, String commitID, String filepath) throws IOException {
        InputStream inputStream;
        ObjectId commit = repository.resolve(commitID);
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit revCommit = revWalk.parseCommit(commit);
            RevTree revTree = revCommit.getTree();

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

    public static void calculateLocTouchedUtils(Map<String, Integer> updatedLocTouched, List<DiffEntry> diffEntries, DiffFormatter diffFormatter) throws IOException {
        for (DiffEntry entry : diffEntries) {
            String path = entry.getNewPath();
            if( !FeatureCalculatorUtils.isPathValid(path) ) continue;
            EditList editList = diffFormatter.toFileHeader(entry).toEditList();
            String className = StringUtils.getFileName(path);
            int locTouched = 0;
            /* Calcolo le LOC touched per questo file */
            for (Edit edit : editList) {
                locTouched = locTouched + edit.getLengthA() + edit.getLengthB();
            }
            int updatedLocTouch;
            /* Se updateLocTouched non contiene già il file lo aggiungo per la prima volta */
            if (!updatedLocTouched.containsKey(className)) {
                updatedLocTouched.put(className, locTouched);
            }
            /* altrimenti aggiorno il valore */
            else {
                updatedLocTouch = updatedLocTouched.get(className) + locTouched;
                updatedLocTouched.put(className, updatedLocTouch);
            }
        }
    }

    public static void calculateNumberOfRevisionsUtils(Map<String, Integer> numberOfRevision, List<DiffEntry> diffs) {
        for (DiffEntry entry : diffs) {
            String path = entry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            /* Se revisionCounter non contiene già il file lo aggiungo per la prima volta */
            if(!numberOfRevision.containsKey(className)) {
                numberOfRevision.put(className, 1);
            }
            /* Altrimenti aggiorno il valore */
            else {
                numberOfRevision.put(className, numberOfRevision.get(className) + 1);
            }
        }
    }

    public static void calculateNumberOfAuthorsUtils(Map<String, List<String>> authorsMap, List<DiffEntry> diffs, String author) {
        for (DiffEntry entry : diffs) {
            String path = entry.getNewPath();
            if( !FeatureCalculatorUtils.isPathValid(path) ) continue;
            String className = StringUtils.getFileName(path);
            /* Controllo se il file non è già presente in authorsMap
                 in questo caso aggiungo il file per la prima volta */
            if(!authorsMap.containsKey(className)) {
                List<String> authors = new ArrayList<>();
                authors.add(author);
                authorsMap.put(className, authors);
            }
            /* altrimenti aggiorno authorsMap se necessario */
            else {
                List<String> authors = authorsMap.get(className);
                if(!authors.contains(author)) {
                    authors.add(author);
                }
            }
        }
    }

    public static void calculateLocAddedUtils(Map<String, Integer> updatedLocAdded, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for (DiffEntry entry : diffs) {
            String path = entry.getNewPath();
            if( !FeatureCalculatorUtils.isPathValid(path) ) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(entry).toEditList();
            int locAdded = 0;
            for (Edit edit : editList) {
                if (edit.getType().equals(Edit.Type.INSERT)) {
                    locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
                }
            }
            /* Se updatedLocAdded non contiene già il file lo aggiungo per la prima volta */
            if(!updatedLocAdded.containsKey(className)) {
                updatedLocAdded.put(className, locAdded);
            }
            /* Altrimenti aggiorno il valore */
            else {
                int updatedLoc = updatedLocAdded.get(className) + locAdded;
                updatedLocAdded.put(className, updatedLoc);
            }
        }
    }

    public static void calculateMaxLocAddedUtils(Map<String, Integer> maxLocAdded, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for (DiffEntry diffEntry : diffs) {
            String path = diffEntry.getNewPath();
            if (! FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            int locAdded = 0;
            for(Edit edit : editList) {
                if (edit.getType().equals(Edit.Type.INSERT)) {
                    locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
                }
            }
            /* Se la mappa non contiene già il file lo aggiungo per la prima volta */
            if(!maxLocAdded.containsKey(className)) {
                maxLocAdded.put(className, locAdded);
            }
            /* altrimenti aggiorno il valore se necessario */
            else {
                if(locAdded > maxLocAdded.get(className)) {
                    maxLocAdded.put(className, locAdded);
                }
            }
        }
    }

    public static void calculateAverageLocAddedUtils(Map<String, List<Integer>> avgLocAdded, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for(DiffEntry diffEntry : diffs) {
            String path = diffEntry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            int locAdded = 0;
            for(Edit edit:editList) {
                if(edit.getType().equals(Edit.Type.INSERT)) {
                    locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
                }
            }
            /* se la mappa non contiene il file lo aggiungo per la prima volta */
            if(!avgLocAdded.containsKey(className)) {
                List<Integer> avgLocAddedList = new ArrayList<>();
                avgLocAddedList.add(locAdded);
                avgLocAddedList.add(1);
                avgLocAdded.put(className, avgLocAddedList);
            }
            /* altrimento aggiorno il valore */
            else {
                avgLocAdded.get(className).set(0, avgLocAdded.get(className).get(0) + locAdded);
                avgLocAdded.get(className).set(1 , avgLocAdded.get(className).get(1) + 1);
            }
        }
    }

    public static void calculateChurnUtils(Map<String, Integer> churnMap, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for(DiffEntry diffEntry : diffs) {
            String path = diffEntry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            int locAdded = 0;
            int locDeleted = 0;

            for(Edit edit : editList) {
                if(edit.getType().equals(Edit.Type.INSERT)) {
                    locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
                }
                if (edit.getType().equals(Edit.Type.DELETE)) {
                    locDeleted = locDeleted + edit.getLengthA() + edit.getLengthB();
                }
            }
            int churn = locAdded - locDeleted;
            /* se la mappa non contiene il file lo aggiungo per la prima volta */
            if(!churnMap.containsKey(className)) {
                churnMap.put(className, churn);
            }
            /* altrimenti aggiorno la mappa */
            else {
                int totalChurn = churnMap.get(className) + churn;
                churnMap.put(className, totalChurn);
            }
        }
    }

    public static void calculateMaxChurnUtils(Map<String, Integer> churnMap, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for(DiffEntry diffEntry: diffs) {
            String path = diffEntry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            int locAdded = 0;
            int locDeleted = 0;
            for(Edit edit : editList) {
                if(edit.getType().equals(Edit.Type.INSERT)) {
                    locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
                }
                if (edit.getType().equals(Edit.Type.DELETE)) {
                    locDeleted = locDeleted + edit.getLengthA() + edit.getLengthB();
                }
            }
            int churn = locAdded - locDeleted;
            /* Se la mappa non contiene la classe la aggiungo per la prima volta */
            if(!churnMap.containsKey(className)) {
                churnMap.put(className, churn);
            }
            /* altrimenti aggiorno il valore se necessario */
            else {
                if(churn > churnMap.get(className)) {
                    churnMap.put(className, churn);
                }
            }
        }
    }

    public static void addResultSetOfTheRelease (Map<CompositeKey, Integer> feature, Map<String, Integer> featureOverRelease, List<String> classList, String releaseName) {
        for(String element: classList) {
            String className = StringUtils.getFileName(element);
            Integer featureOverReleaseValue = featureOverRelease.get(className);
            if(featureOverReleaseValue != null) {
                CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releaseName, StringConstant.SUBSTRING_TO_REMOVE), element);
                feature.put(key, featureOverReleaseValue);
            }
        }
    }
}
