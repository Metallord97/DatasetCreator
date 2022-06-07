package features;

import labeling.ReleaseKeeper;
import labeling.Tag;
import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import utils.GitUtils;
import utils.SourceCodeLineCounter;
import utils.StringConstant;
import utils.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FeatureCalculatorUtils {
    private FeatureCalculatorUtils() {}


    /**
     *
     * @param path
     * @return questo metodo ritorna True se la stringa passata in input è un file con estensione .java e non contiene la sottostringa "/test" o "Test"
     */
    public static boolean isPathValid (String path) {
        String extension = StringUtils.getExtension(path);
        return (extension.equals("java") && !StringUtils.hasMatchingSubstring(path, "/test", "Test"));
    }

    public static Iterable<RevCommit> getAllCommitsOfARelease(Git git, Integer releaseId) throws IOException, GitAPIException {
        Tag fromTag = ReleaseKeeper.getInstance().getTagFromId(releaseId);
        Ref from = git.getRepository().exactRef(StringConstant.REFS_TAGS + fromTag.getTagName());
        Tag toTag = ReleaseKeeper.getInstance().getTagFromId(releaseId + 1);
        if(toTag == null) return null;
        Ref to = git.getRepository().exactRef(StringConstant.REFS_TAGS + toTag.getTagName());
        ObjectId fromId = GitUtils.getObjectIdFromRef(from);
        ObjectId toId = GitUtils.getObjectIdFromRef(to);
        return git.log().addRange(fromId, toId).call();
    }

    public static List<String> getAllFileOfTheRelease(Git git, Integer releaseId) throws IOException, GitAPIException {
        List<String> classList = new ArrayList<>();
        Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releaseId);
        if(commits == null) return Collections.emptyList();
        RevCommit last = null;
        for(RevCommit commit : commits) {
            last = commit;
        }
        if (last != null) {
            ObjectId treeId = last.getTree().getId();
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.reset(treeId);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String filePath = treeWalk.getPathString();
                    if (FeatureCalculatorUtils.isPathValid(filePath)) {
                        classList.add(filePath);
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
            int locAdded = FeatureCalculatorUtils.calculateLocAddedInAClass(editList);
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
            int locAdded = FeatureCalculatorUtils.calculateLocAddedInAClass(editList);
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
            int locAdded = FeatureCalculatorUtils.calculateLocAddedInAClass(editList);
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

    private static int calculateLocAddedInAClass(EditList editList) {
        int locAdded = 0;
        for(Edit edit:editList) {
            if(edit.getType().equals(Edit.Type.INSERT)) {
                locAdded = locAdded + edit.getLengthA() + edit.getLengthB();
            }
        }
        return locAdded;
    }

    public static void calculateChurnUtils(Map<String, Integer> churnMap, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for(DiffEntry diffEntry : diffs) {
            String path = diffEntry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();

            int churn = FeatureCalculatorUtils.calculateChurnOfAClass(editList);
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

    public static void calculateMaxChurnUtils(Map<String, Integer> maxChurnMap, List<DiffEntry> diffs, DiffFormatter diffFormatter) throws IOException {
        for(DiffEntry diffEntry: diffs) {
            String path = diffEntry.getNewPath();
            if(!FeatureCalculatorUtils.isPathValid(path)) continue;
            String className = StringUtils.getFileName(path);
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            int churn = FeatureCalculatorUtils.calculateChurnOfAClass(editList);
            /* Se la mappa non contiene la classe la aggiungo per la prima volta */
            if(!maxChurnMap.containsKey(className)) {
                maxChurnMap.put(className, churn);
            }
            /* altrimenti aggiorno il valore se necessario */
            else {
                if(churn > maxChurnMap.get(className)) {
                    maxChurnMap.put(className, churn);
                }
            }
        }
    }

    private static int calculateChurnOfAClass(EditList editList) {
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
        return (locAdded - locDeleted);
    }

    public static void addResultSetOfTheRelease (Map<CompositeKey, Integer> feature, Map<String, Integer> featureOverRelease, List<String> classList, Integer releaseId) {
        for(String element: classList) {
            String className = StringUtils.getFileName(element);
            Integer featureOverReleaseValue = featureOverRelease.get(className);
            if(featureOverReleaseValue != null) {
                CompositeKey key = new CompositeKey(releaseId, element);
                feature.put(key, featureOverReleaseValue);
            }
        }
    }

    public static void addResultNAuth(Map<CompositeKey, Integer> feature, List<String> classList, Map<String, List<String>> authorsMap, Tag release) {
        /* A fine release per ogni file java mi vado a prendere il valore associato nella Map
         *  Se esiste allora metto i dati nella nuova mappa (release, class_name)->NAuth */
        for(String element : classList) {
            String className = StringUtils.getFileName(element);
            List<String> authors = authorsMap.get(className);
            if (authors != null) {
                CompositeKey key = new CompositeKey(ReleaseKeeper.getInstance().getIdFromTag(release), element);
                feature.put(key, authors.size());
            }
        }
    }

    public static void addResultAvgLocAdded(Map<CompositeKey, Integer> feature, List<String> classList, Map<String, List<Integer>> avgLocAdded, Tag release) {
        for (String file:classList) {
            String className = StringUtils.getFileName(file);
            List<Integer> avg = avgLocAdded.get(className);
            if(avg != null) {
                Integer locAdded = avgLocAdded.get(className).get(0);
                Integer numberOfRevisions = avgLocAdded.get(className).get(1);
                Integer avgLocAddedPerRevision = locAdded / numberOfRevisions;
                CompositeKey key = new CompositeKey(ReleaseKeeper.getInstance().getIdFromTag(release), file);
                feature.put(key, avgLocAddedPerRevision);
            }

        }
    }
}
