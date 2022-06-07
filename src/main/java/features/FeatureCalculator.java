package features;

import labeling.ReleaseKeeper;
import labeling.Tag;
import mydatatype.CompositeKey;
import myexception.OutOfCaseException;
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

    public static Map<CompositeKey, Integer> computeFeature(Git git, final Feature feature) throws GitAPIException, IOException, OutOfCaseException {
        if(feature.equals(Feature.SIZE)) {
            return FeatureCalculator.calculateSize(git);
        }
        LOGGER.log(Level.INFO, () -> "Computing" + feature);
        Map<CompositeKey, Integer> column = new LinkedHashMap<>();
        Map<String, List<String>> authorsMap = new LinkedHashMap<>();
        DiffFormatter diffFormatter = GitUtils.getDiffFormatter(git);

        for(Tag release: ReleaseKeeper.getInstance().getReleaseKeySet()) {
            Map<String, Integer> featureOverRelease = new HashMap<>();
            Map<String, List<Integer>> avgLocAdded = new HashMap<>();
            Iterable<RevCommit> commits = FeatureCalculatorUtils.getAllCommitsOfARelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));
            if(commits == null) break;
            List<String> classList = FeatureCalculatorUtils.getAllFileOfTheRelease(git, ReleaseKeeper.getInstance().getIdFromTag(release));

            for(RevCommit commit: commits) {
                if(commit.getParentCount() == 0) continue;
                RevCommit prevCommit = commit.getParent(0);
                List<DiffEntry> diffs = diffFormatter.scan(prevCommit, commit);

                switch (feature) {
                    case LOC_TOUCHED:
                        FeatureCalculatorUtils.calculateLocTouchedUtils(featureOverRelease, diffs, diffFormatter);
                        break;
                    case NR:
                        FeatureCalculatorUtils.calculateNumberOfRevisionsUtils(featureOverRelease, diffs);
                        break;
                    case NAUTH:
                        String author = commit.getAuthorIdent().getName();
                        FeatureCalculatorUtils.calculateNumberOfAuthorsUtils(authorsMap, diffs, author);
                        break;
                    case LOC_ADDED:
                        FeatureCalculatorUtils.calculateLocAddedUtils(featureOverRelease, diffs, diffFormatter);
                        break;
                    case MAX_LOC_ADDED:
                        FeatureCalculatorUtils.calculateMaxLocAddedUtils(featureOverRelease, diffs, diffFormatter);
                        break;
                    case AVG_LOC_ADDED:
                        FeatureCalculatorUtils.calculateAverageLocAddedUtils(avgLocAdded, diffs, diffFormatter);
                        break;
                    case CHURN:
                        FeatureCalculatorUtils.calculateChurnUtils(featureOverRelease, diffs, diffFormatter);
                        break;
                    case MAX_CHURN:
                        FeatureCalculatorUtils.calculateMaxChurnUtils(featureOverRelease, diffs, diffFormatter);
                        break;
                    default:
                        throw new OutOfCaseException("Switch out of case");
                }

            }
            switch (feature) {
                case LOC_TOUCHED:
                case NR:
                case LOC_ADDED:
                case MAX_LOC_ADDED:
                case CHURN:
                case MAX_CHURN:
                    FeatureCalculatorUtils.addResultSetOfTheRelease(column, featureOverRelease, classList, ReleaseKeeper.getInstance().getIdFromTag(release));
                    break;
                case NAUTH:
                    FeatureCalculatorUtils.addResultNAuth(column, classList, authorsMap, release);
                    break;
                case AVG_LOC_ADDED:
                    FeatureCalculatorUtils.addResultAvgLocAdded(column, classList, avgLocAdded,release);
                    break;
                default:
                    throw new OutOfCaseException("Switch out of case");
            }
        }

        return column;
    }

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



}
