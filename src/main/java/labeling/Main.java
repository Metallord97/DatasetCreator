package labeling;

import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
import features.Feature;
import features.FeatureCalculator;
import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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

    public static String getValueToWrite(Integer intValue) {
        if(intValue == null) {
            return "0";
        } else {
            return String.valueOf(intValue);
        }
    }

    public static void main (String [] args) throws GitAPIException, IOException, ParseException {
        for(Project project : Project.values()) {
            LOGGER.log(Level.INFO, "Scanning project {0}...", project.label.toUpperCase());
            Git git = Git.open(new File(project.label + "/.git"));
            ReleaseKeeper.getInstance().setReleaseMap(GitUtils.getReleaseDate(git));
            Map<CompositeKey, Integer> sizes = FeatureCalculator.computeFeature(git, Feature.SIZE);
            Map<CompositeKey, Integer> locTouched = FeatureCalculator.computeFeature(git, Feature.LOC_TOUCHED);
            Map<CompositeKey, Integer> numberOfRevision = FeatureCalculator.computeFeature(git, Feature.NR);
            Map<CompositeKey, Integer> numberOfAuthors = FeatureCalculator.computeFeature(git, Feature.NAUTH);
            Map<CompositeKey, Integer> locAdded = FeatureCalculator.computeFeature(git, Feature.LOC_ADDED);
            Map<CompositeKey, Integer> maxLocAdded = FeatureCalculator.computeFeature(git, Feature.MAX_LOC_ADDED);
            Map<CompositeKey, Integer> avgLocAdded = FeatureCalculator.computeFeature(git, Feature.AVG_LOC_ADDED);
            Map<CompositeKey, Integer> churnMap = FeatureCalculator.computeFeature(git, Feature.CHURN);
            Map<CompositeKey, Integer> maxChurnMap = FeatureCalculator.computeFeature(git, Feature.MAX_CHURN);

            List<CompositeKey> classNames = new ArrayList<>(sizes.keySet());
            List<CompositeKey> buggyClasses = Labeling.affectedVersionLabeling(git, project.label.toUpperCase());
            Map<CompositeKey, String> buggyness = Main.merge(classNames, buggyClasses);

            FileWriter fileWriter = new FileWriter(project.label + "_dataset.csv");
            CSVWriter writer = new CSVWriter(fileWriter, ICSVWriter.DEFAULT_SEPARATOR, ICSVWriter.NO_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER, ICSVWriter.RFC4180_LINE_END);
            String [] header = {"release", "class_name", "size", "LOC_touched", "NR", "NAuth", "LOC_added", "MAX_LOC_added", "AVG_LOC_added", "churn", "MAX_churn", "buggy"};
            writer.writeNext(header);

            Set<CompositeKey> keys = sizes.keySet();
            for(CompositeKey key : keys) {
                String size = getValueToWrite(sizes.get(key));
                String locTouchedForThisClass = getValueToWrite(locTouched.get(key));
                String nr = getValueToWrite(numberOfRevision.get(key));
                String nAuth = getValueToWrite(numberOfAuthors.get(key));
                String locAddedForThisClass = getValueToWrite(locAdded.get(key));
                String maxLocAddedForThisClass = getValueToWrite(maxLocAdded.get(key));
                String avgLocAddedForThisClass = getValueToWrite(avgLocAdded.get(key));
                String churn = getValueToWrite(churnMap.get(key));
                String maxChurn = getValueToWrite(maxChurnMap.get(key));
                String buggy = buggyness.get(key);
                writer.writeNext(new String[] {String.valueOf(key.getRelease()), key.getClassName(), size, locTouchedForThisClass, nr,
                        nAuth, locAddedForThisClass, maxLocAddedForThisClass, avgLocAddedForThisClass, churn, maxChurn, buggy});
            }
            writer.flush();

            Set<Tag> releases = ReleaseKeeper.getInstance().getReleaseKeySet();
            for(Tag release: releases) {
                LOGGER.log(Level.INFO, ()-> "Release Name: " + release.getTagName() + " Release Date: " + release.getTagDate() + " Release id: " + ReleaseKeeper.getInstance().getIdFromTag(release));
            }
        }


    }
}
