import com.opencsv.CSVWriter;
import features.FeatureCalculator;
import labeling.Labeling;
import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class Main {

    public static LinkedHashMap<CompositeKey, String> merge(List<CompositeKey> allClasses, List<CompositeKey> buggyClasses) {
        LinkedHashMap<CompositeKey, String> result = new LinkedHashMap<>();

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
        Git git = Git.open(new File("repository/.git"));

        List<Ref> call = git.tagList().call();
        LinkedHashMap<CompositeKey, Integer> sizes = FeatureCalculator.calculateSize(git, call);
        LinkedHashMap<CompositeKey, Integer> locTouched = FeatureCalculator.calculateLocTouched(git, call);
        LinkedHashMap<CompositeKey, Integer> numberOfRevision = FeatureCalculator.calculateNumberOfRevisions(git, call);
        LinkedHashMap<CompositeKey, Integer> numberOfAuthors = FeatureCalculator.calculateNumberOfAuthors(git,call);
        LinkedHashMap<CompositeKey, Integer> locAdded = FeatureCalculator.calculateLocAdded(git, call);
        LinkedHashMap<CompositeKey, Integer> maxLocAdded = FeatureCalculator.calculateMaxLocAdded(git, call);
        LinkedHashMap<CompositeKey, Integer> avgLocAdded = FeatureCalculator.calculateAverageLocAdded(git, call);
        LinkedHashMap<CompositeKey, Integer> churnMap = FeatureCalculator.calculateChurn(git, call);
        LinkedHashMap<CompositeKey, Integer> maxChurnMap = FeatureCalculator.calculateMaxChurn(git, call);

        List<CompositeKey> classNames = new ArrayList<>(sizes.keySet());
        List<CompositeKey> buggyClasses = Labeling.affectedVersionLabeling(git);
        LinkedHashMap<CompositeKey, String> buggyness = Main.merge(classNames, buggyClasses);

        FileWriter fileWriter = new FileWriter("output.csv");
        CSVWriter writer = new CSVWriter(fileWriter);
        String [] header = {"release", "class_name", "size", "LOC_touched", "NR", "NAuth", "LOC_added", "MAX_LOC_added", "AVG_LOC_added", "churn", "MAX_churn", "buggy"};
        writer.writeNext(header);

        Set<CompositeKey> keys = sizes.keySet();
        for(CompositeKey key : keys) {
            String size = String.valueOf(sizes.get(key));
            String loc_touched = String.valueOf(locTouched.get(key));
            String nr = String.valueOf(numberOfRevision.get(key));
            String nAuth = String.valueOf(numberOfAuthors.get(key));
            String loc_added = String.valueOf(locAdded.get(key));
            String max_loc_added = String.valueOf(maxLocAdded.get(key));
            String avg_loc_added = String.valueOf(avgLocAdded.get(key));
            String churn = String.valueOf(churnMap.get(key));
            String maxChurn = String.valueOf(maxChurnMap.get(key));
            String buggy = buggyness.get(key);
            writer.writeNext(new String[] {key.getRelease(), key.getClassName(), size, loc_touched, nr,
                    nAuth, loc_added, max_loc_added, avg_loc_added, churn, maxChurn, buggy});
        }
    }
}
