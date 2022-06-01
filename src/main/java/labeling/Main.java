package labeling;

import com.opencsv.CSVWriter;
import features.FeatureCalculator;
import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import utils.GitUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static Map<CompositeKey, String> merge(List<CompositeKey> allClasses, List<CompositeKey> buggyClasses) {
        Map<CompositeKey, String> result = new LinkedHashMap<>();

        for(CompositeKey className : allClasses) {
            if(buggyClasses.contains(className)) {
                result.put(className, "yes");
            }
            else {
                result.put(className, "no");
            }
        }

        return result;
    }

    public static void main (String [] args) throws GitAPIException, IOException, ParseException {
        for(Project project : Project.values()) {
            LOGGER.log(Level.INFO, "Scanning project {0}...", project.label.toUpperCase());
            Git git = Git.open(new File(project.label + "/.git"));
            ReleaseKeeper.getInstance().setReleaseMap(GitUtils.getReleaseDate(git));
            Map<CompositeKey, Integer> sizes = FeatureCalculator.calculateSize(git);
            Map<CompositeKey, Integer> locTouched = FeatureCalculator.calculateLocTouched(git);
            Map<CompositeKey, Integer> numberOfRevision = FeatureCalculator.calculateNumberOfRevisions(git);
            Map<CompositeKey, Integer> numberOfAuthors = FeatureCalculator.calculateNumberOfAuthors(git);
            Map<CompositeKey, Integer> locAdded = FeatureCalculator.calculateLocAdded(git);
            Map<CompositeKey, Integer> maxLocAdded = FeatureCalculator.calculateMaxLocAdded(git);
            Map<CompositeKey, Integer> avgLocAdded = FeatureCalculator.calculateAverageLocAdded(git);
            Map<CompositeKey, Integer> churnMap = FeatureCalculator.calculateChurn(git);
            Map<CompositeKey, Integer> maxChurnMap = FeatureCalculator.calculateMaxChurn(git);

            List<CompositeKey> classNames = new ArrayList<>(sizes.keySet());
            List<CompositeKey> buggyClasses = Labeling.affectedVersionLabeling(git, project.label.toUpperCase());
            Map<CompositeKey, String> buggyness = Main.merge(classNames, buggyClasses);

            FileWriter fileWriter = new FileWriter(project.label + "_dataset.csv");
            CSVWriter writer = new CSVWriter(fileWriter, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END);
            String [] header = {"release", "class_name", "size", "LOC_touched", "NR", "NAuth", "LOC_added", "MAX_LOC_added", "AVG_LOC_added", "churn", "MAX_churn", "buggy"};
            writer.writeNext(header);

            Set<CompositeKey> keys = sizes.keySet();
            for(CompositeKey key : keys) {
                String size = String.valueOf(sizes.get(key));
                String locTouchedForThisClass = String.valueOf(locTouched.get(key));
                String nr = String.valueOf(numberOfRevision.get(key));
                String nAuth = String.valueOf(numberOfAuthors.get(key));
                String locAddedForThisClass = String.valueOf(locAdded.get(key));
                String maxLocAddedForThisClass = String.valueOf(maxLocAdded.get(key));
                String avgLocAddedForThisClass = String.valueOf(avgLocAdded.get(key));
                String churn = String.valueOf(churnMap.get(key));
                String maxChurn = String.valueOf(maxChurnMap.get(key));
                String buggy = buggyness.get(key);
                writer.writeNext(new String[] {String.valueOf(key.getRelease()), key.getClassName(), size, locTouchedForThisClass, nr,
                        nAuth, locAddedForThisClass, maxLocAddedForThisClass, avgLocAddedForThisClass, churn, maxChurn, buggy});
            }
        }


    }
}
