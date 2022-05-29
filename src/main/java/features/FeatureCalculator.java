package features;

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
import org.eclipse.jgit.util.io.DisabledOutputStream;
import utils.SourceCodeLineCounter;
import utils.StringUtils;

import java.io.*;
import java.util.*;

public class FeatureCalculator {

    /**
     * Dato in input il repository, la lista delle release e il nome del progetto calcola il LOC per ogni classe raggruppando per chiave (projectName, release, className)
     * @param git
     * @param releases
     * @return Questo metodo ritorna una LinkedHashMap contenente la coppia (key, value) dove key è la tripla (projectName, release, className) e value è il LOC
     * @throws IOException
     * @throws GitAPIException
     */
    public static LinkedHashMap<CompositeKey, Integer> calculateSize(Git git, List<Ref> releases) throws GitAPIException, IOException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        /* Per ogni release prendo l'ultimo commit relativo a quella release.
        *  Poi eseguo una ricerca sull'albero cercando i file java
        *  Se li trovo calcolo il LOC di quel file e li aggiungo alla Mappa*/
        for (int i = 0; i < releases.size()-1; i++) {
            Iterable<RevCommit> log = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            RevCommit lastCommit = null;
            for(RevCommit commit : log) {
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
                            CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), path);
                            ObjectId objectId = treeWalk.getObjectId(0);
                            ObjectLoader loader = git.getRepository().open(objectId);
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            loader.copyTo(byteArrayOutputStream);
                            InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            int loc = SourceCodeLineCounter.getNumberOfLines(reader);
                            feature.put(key, loc);
                        }
                    }
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateLocTouched (Git git, List<Ref> release) throws IOException, GitAPIException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> updatedLocTouched = new HashMap<>();
        /* Per ogni release itero su tutti i commit, per ogni commit prendo i file java che sono stati toccati dal quel commit
           per ogni file controllo quante righe sono state aggiunte e rimosse aggiornando sempre updatedLocTouched
           dopodichè inserisco in feature i loc touched di quella classe nella determinata release
         */
        for (int i = 0; i < release.size()-1; i++) {
            Iterable<RevCommit> log = FeatureCalculatorUtils.getAllCommitsOfARelease(git, release.get(i), release.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, release.get(i), release.get(i+1));

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for (RevCommit commit : log) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                    for (DiffEntry entry : diffs) {
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
            }
            /* Ora mi trovo a fine release */
            for(String element: classList) {
                String className = StringUtils.getFileName(element);
                Integer locTouched = updatedLocTouched.get(className);
                if(locTouched != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(release.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, locTouched);
                }
            }

        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateNumberOfRevisions (Git git, List<Ref> release) throws IOException, GitAPIException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> numberOfRevision = new HashMap<>();

        /*
        Per ogni release scorro tutti i commit e per ognuno prendo le classi java modificate
        tengo un contatore per ogni classe che segna quante volte la classe è stata modificata nella release
         */
        for (int i = 0; i < release.size() -1; i++) {
            Iterable<RevCommit> log = FeatureCalculatorUtils.getAllCommitsOfARelease(git, release.get(i), release.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, release.get(i), release.get(i+1));

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for (RevCommit commit : log) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath();
                        if(!FeatureCalculatorUtils.isPathValid(path)) continue;
                        String className = StringUtils.getFileName(path);
                        /* Se revisionCounte non contiene già il file lo aggiungo per la prima volta */
                        if(!numberOfRevision.containsKey(className)) {
                            numberOfRevision.put(className, 1);
                        }
                        /* Altrimenti aggiorno il valore */
                        else {
                            numberOfRevision.put(className, numberOfRevision.get(className) + 1);
                        }
                    }
                }
            }
            /* qui mi trovo a fine release */
            for (String element : classList) {
                String className = StringUtils.getFileName(element);
                Integer NR = numberOfRevision.get(className);
                if(NR != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(release.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, NR);
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateNumberOfAuthors (Git git, List<Ref> releases) throws IOException, GitAPIException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> authorsMap = new LinkedHashMap<>();
        /* Per ogni release prendo tutti i commit
        *  per ogni commit controllo chi ha fatto quel commit e quali file ha modificato
        *  creo una associazione nome_file->(lista di autori) e li conto a fine di ogni release */
        for (int i = 0; i < releases.size()-1; i++) {
            Iterable<RevCommit> log = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for (RevCommit commit : log) {
                    if (commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath();
                        String author = commit.getAuthorIdent().getName();
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
            }
            /* A fine release per ogni file java mi vado a prendere il valore associato nella Map
            *  Se esiste allora metto i dati nella nuova mappa (release, class_name)->NAuth */
            for(String element : classList) {
                String className = StringUtils.getFileName(element);
                List<String> authors = authorsMap.get(className);
                if (authors != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, authors.size());
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateLocAdded (Git git, List<Ref> releases) throws IOException, GitAPIException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> updatedLocAdded = new HashMap<>();
        /* Per ogni release itero su tutti i commit
        *  Per ogni commit mi prendo i file modificati
        *  Per ogni file mi calcolo quante linee sono state aggiunte */
        for (int i = 0; i < releases.size() - 1; i++) {
            Iterable<RevCommit> log = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for (RevCommit commit : log) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
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
            }
            /* Ora mi trovo a fine release */
            for(String element: classList) {
                String className = StringUtils.getFileName(element);
                Integer locTouched = updatedLocAdded.get(className);
                if(locTouched != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, locTouched);
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateMaxLocAdded (Git git, List<Ref> releases) throws GitAPIException, IOException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> maxLocAdded = new HashMap<>();

        for (int i = 0; i < releases.size() - 1; i++) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));
            try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for (RevCommit commit: commits) {
                    if (commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                    for (DiffEntry diffEntry : diffEntries) {
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
                        /* Se la mappa non contiente già il file lo aggiungo per la prima volta */
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
            }
            /* Fine release */
            for (String element : classList) {
                String className = StringUtils.getFileName(element);
                Integer locAdded = maxLocAdded.get(className);
                if(locAdded != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, locAdded);
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateAverageLocAdded(Git git, List<Ref> releases) throws GitAPIException, IOException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, List<Integer>> avgLocAdded = new HashMap<>();

        for(int i = 0; i < releases.size() - 1; i++) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));
            try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for(RevCommit commit : commits) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                    for(DiffEntry diffEntry : diffEntries) {
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
            }
            /* qui mi trovo a fine release */
            for (String element:classList) {
                String className = StringUtils.getFileName(element);
                List<Integer> avg = avgLocAdded.get(className);
                if(avg != null) {
                    Integer locAdded = avgLocAdded.get(className).get(0);
                    Integer numberOfRevisions = avgLocAdded.get(className).get(1);
                    Integer avgLocAddedPerRevision = locAdded / numberOfRevisions;
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, avgLocAddedPerRevision);
                }

            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateChurn(Git git, List<Ref> releases) throws GitAPIException, IOException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> churnMap = new HashMap<>();

        for(int i = 0; i < releases.size() - 1; i++) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i +1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));
            try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for(RevCommit commit : commits) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                    for(DiffEntry diffEntry : diffEntries) {
                        String path = diffEntry.getNewPath();
                        if(!FeatureCalculatorUtils.isPathValid(path)) continue;
                        String className = StringUtils.getFileName(path);
                        EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
                        int locAdded = 0 , locDeleted = 0;

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
            }
            /* Qui mi trovo a fine release */
            for(String element : classList) {
                String className = StringUtils.getFileName(element);
                Integer churn = churnMap.get(className);
                if(churn != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, churn);
                }
            }
        }

        return feature;
    }

    public static LinkedHashMap<CompositeKey, Integer> calculateMaxChurn(Git git, List<Ref> releases) throws GitAPIException, IOException {
        LinkedHashMap<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> churnMap = new HashMap<>();

        for(int i = 0; i < releases.size() - 1; i++) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, releases.get(i), releases.get(i+1));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, releases.get(i), releases.get(i+1));
            try(DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(git.getRepository());
                for(RevCommit commit : commits) {
                    if(commit.getParentCount() == 0) continue;
                    RevCommit prevCommit = commit.getParent(0);
                    List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                    for(DiffEntry diffEntry: diffEntries) {
                        String path = diffEntry.getNewPath();
                        if(!FeatureCalculatorUtils.isPathValid(path)) continue;
                        String className = StringUtils.getFileName(path);
                        EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
                        int locAdded = 0, locDeleted = 0;
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
            }
            /* qui mi trovo a fine release */
            for(String element : classList) {
                String className = StringUtils.getFileName(element);
                Integer maxChurn = churnMap.get(className);
                if(maxChurn != null) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(releases.get(i).getName(), "refs/tags/"), element);
                    feature.put(key, maxChurn);
                }
            }

        }

        return feature;
    }

}
