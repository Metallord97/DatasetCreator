package features;

import labeling.ReleaseKeeper;
import labeling.Tag;
import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import utils.GitUtils;
import utils.SourceCodeLineCounter;
import utils.StringUtils;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FeatureCalculator {
    private static final Logger LOGGER = Logger.getLogger(FeatureCalculator.class.getName());

    private FeatureCalculator() {}

    /**
     * Dato in input il repository, la lista delle release e il nome del progetto calcola il LOC per ogni classe raggruppando per chiave (projectName, release, className)
     * @param git
     * @return Questo metodo ritorna una LinkedHashMap contenente la coppia (key, value) dove key è la tripla (projectName, release, className) e value è il LOC
     * @throws IOException
     * @throws GitAPIException
     */
    public static Map<CompositeKey, Integer> calculateSize(Git git) throws GitAPIException, IOException {
        LOGGER.log(Level.INFO, "Computing sizes...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        /* Per ogni release prendo l'ultimo commit relativo a quella release.
        *  Poi eseguo una ricerca sull'albero cercando i file java
        *  Se li trovo calcolo il LOC di quel file e li aggiungo alla Mappa*/
        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null) break;
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
                            CompositeKey key = new CompositeKey(ReleaseKeeper.getInstance().getIdFromTag(release), path);
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

    public static Map<CompositeKey, Integer> calculateLocTouched (Git git) throws IOException, GitAPIException {
        LOGGER.log(Level.INFO, "Computing LOC_TOUCHED...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> updatedLocTouched = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);
        /* Per ogni release itero su tutti i commit, per ogni commit prendo i file java che sono stati toccati dal quel commit
           per ogni file controllo quante righe sono state aggiunte e rimosse aggiornando sempre updatedLocTouched
           dopodichè inserisco in feature i loc touched di quella classe nella determinata release
         */
        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for (RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateLocTouchedUtils(updatedLocTouched, diffs, diffFormatter);
            }

            /* Ora mi trovo a fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, updatedLocTouched, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateNumberOfRevisions (Git git) throws IOException, GitAPIException {
        LOGGER.log(Level.INFO, "Computing NR...");

        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> numberOfRevision = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);
        /*
        Per ogni release scorro tutti i commit e per ognuno prendo le classi java modificate
        tengo un contatore per ogni classe che segna quante volte la classe è stata modificata nella release
         */
        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for (RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateNumberOfRevisionsUtils(numberOfRevision, diffs);
            }

            /* qui mi trovo a fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, numberOfRevision, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateNumberOfAuthors (Git git) throws IOException, GitAPIException {
        LOGGER.log(Level.INFO, "Computing NAuth...");

        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, List<String>> authorsMap = new LinkedHashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);
        /* Per ogni release prendo tutti i commit
        *  per ogni commit controllo chi ha fatto quel commit e quali file ha modificato
        *  creo una associazione nome_file->(lista di autori) e li conto a fine di ogni release */
        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for (RevCommit commit : commits) {
                if (commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                String author = commit.getAuthorIdent().getName();
                List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateNumberOfAuthorsUtils(authorsMap, diffs, author);
            }

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

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateLocAdded (Git git) throws IOException, GitAPIException {
        LOGGER.log(Level.INFO, "Computing LOC_added...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> updatedLocAdded = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);
        /* Per ogni release itero su tutti i commit
        *  Per ogni commit mi prendo i file modificati
        *  Per ogni file mi calcolo quante linee sono state aggiunte */
        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for (RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateLocAddedUtils(updatedLocAdded, diffs, diffFormatter);
            }

            /* Ora mi trovo a fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, updatedLocAdded, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateMaxLocAdded (Git git) throws GitAPIException, IOException {
        LOGGER.log(Level.INFO, "Computing MAX_LOC_ADDED...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> maxLocAdded = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);

        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for (RevCommit commit: commits) {
                if (commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateMaxLocAddedUtils(maxLocAdded, diffEntries, diffFormatter);
            }

            /* Fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, maxLocAdded, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateAverageLocAdded(Git git) throws GitAPIException, IOException {
        LOGGER.log(Level.INFO, "Computing AVG_LOC_ADDED...");

        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, List<Integer>> avgLocAdded = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);

        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for(RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateAverageLocAddedUtils(avgLocAdded, diffEntries, diffFormatter);
            }

            /* qui mi trovo a fine release */
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

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateChurn(Git git) throws GitAPIException, IOException {
        LOGGER.log(Level.INFO, "Computing churn...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> churnMap = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);

        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for(RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateChurnUtils(churnMap, diffEntries, diffFormatter);
            }

            /* Qui mi trovo a fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, churnMap, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

    public static Map<CompositeKey, Integer> calculateMaxChurn(Git git) throws GitAPIException, IOException {
        LOGGER.log(Level.INFO, "Computing MAX_churn...");
        Map<CompositeKey, Integer> feature = new LinkedHashMap<>();
        Map<String, Integer> churnMap = new HashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);

        for(Tag release : ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null || classList == null) break;

            for(RevCommit commit : commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffEntries = diffFormatter.scan(prevCommit, commit);
                FeatureCalculatorUtils.calculateMaxChurnUtils(churnMap, diffEntries, diffFormatter);
            }

            /* qui mi trovo a fine release */
            FeatureCalculatorUtils.addResultSetOfTheRelease(feature, churnMap, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
        }

        return feature;
    }

}
