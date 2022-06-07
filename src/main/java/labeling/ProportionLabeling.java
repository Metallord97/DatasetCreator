package labeling;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.GitUtils;
import utils.MapUtils;
import utils.ParseUtils;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProportionLabeling {
    private static final Logger LOGGER = Logger.getLogger(ProportionLabeling.class.getName());
    private final LinkedHashMap<Tag, Integer> pValue;

    private static ProportionLabeling instance = null;

    private ProportionLabeling () {
        pValue = new LinkedHashMap<>();
    }

    public static ProportionLabeling getInstance() {
        if(instance == null) {
            instance = new ProportionLabeling();
        }
        return instance;
    }

    public void incrementalProportion(Git git, JSONArray issues) throws GitAPIException, IOException, ParseException {
        /* Per ogni issue(bug fixato) ho la lista delle affected version (se presenti), la data di creazione del ticket e la data di risoluzione
           Tramite le affected version posso risalire all'Injected Version (Affected Version più vecchia)
           Tramite la data di creazione del ticket posso risalire all'Opening version
           Tramite la data di risoluzione posso risalire alla Fixed Versions
        *  Calcolo quindi P per ogni bug, e quindi mi tengo per ogni versione una lista dei P calcolati
           Alla fine faccio la media per ogni versione */

        Map<Tag, List<Integer>> pMap = new LinkedHashMap<>();
        Map<Tag, Integer> release = GitUtils.getReleaseDate(git);
        Set<Tag> keySet = release.keySet();
        for(Tag key : keySet) {
            List<Integer> pValues = new ArrayList<>();
            pMap.put(key, pValues);
        }
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            JSONObject fields = issue.getJSONObject("fields");
            JSONArray versions = fields.getJSONArray("versions");
            String resolutionDate = fields.get("resolutiondate").toString();
            String created =  fields.get("created").toString();
            if(versions.length() == 0) continue;
            Date resDate = ParseUtils.convertToDate(resolutionDate);
            Date creationDate = ParseUtils.convertToDate(created);
            Integer injectedVersion = ProportionLabeling.getInjectedVersion( versions);
            Integer openingVersion = ProportionLabeling.getNextVersion(creationDate);
            Integer fixedVersion = ProportionLabeling.getNextVersion(resDate);

            if(injectedVersion < openingVersion && openingVersion < fixedVersion) {
                Integer p = (fixedVersion - injectedVersion) / (fixedVersion - openingVersion);
                Tag key = MapUtils.getKeyByValue(release, fixedVersion);
                pMap.get(key).add(p);
            }

        }

        Set<Tag> versionSet = pMap.keySet();
        int total = 0;
        int bugCounter = 0;
        for (Tag version:versionSet) {
            if(!pMap.get(version).isEmpty()) {
                for(Integer num : pMap.get(version)) {
                    total = total + num;
                    bugCounter ++;
                }
                pValue.put(version, total / bugCounter);
            }
        }
    }

    public Integer computePredictedIV(Git git, Date creationTickedDate, Date fixedTickedDate) throws GitAPIException, IOException {
        Integer fixedVersion = ProportionLabeling.getNextVersion( fixedTickedDate);
        Integer openingVersion = ProportionLabeling.getNextVersion(creationTickedDate);
        Integer p = this.getP(fixedTickedDate);
        int predictedIV;

        if(Objects.equals(fixedVersion, openingVersion)) {
            predictedIV = fixedVersion - p;
        }
        else {
            predictedIV = (fixedVersion - (fixedVersion - openingVersion) * p);
        }
        LOGGER.log(Level.INFO, () -> "Opening Version: " + openingVersion  + " Fixed Version: " + fixedVersion + " PredictedIV: " + predictedIV);
        return predictedIV;
    }

    public Integer getP(Date revisionDate) {
        Set<Tag> tagSet = pValue.keySet();
        for(Tag tag: tagSet) {
            if(revisionDate.after(tag.getTagDate())) {
                continue;
            }
            return pValue.get(tag);
        }
        return 1;
    }


    public static Integer getInjectedVersion(JSONArray affectedVersions) {
        /* L'injected versione è la affected version più vecchia */
        Integer injectedVersion = Integer.MAX_VALUE;

        for(int i = 0; i <affectedVersions.length(); i++) {

            String affectedVersion = affectedVersions.getJSONObject(i).get("name").toString();
            for(Tag key : ReleaseKeeper.getInstance().getReleaseKeySet()) {
                if(key.getTagName().contains(affectedVersion) && ReleaseKeeper.getInstance().getIdFromTag(key) < injectedVersion) {
                    injectedVersion = ReleaseKeeper.getInstance().getIdFromTag(key);
                }
            }

        }

        return injectedVersion;
    }

    public static Integer getNextVersion(Date tickedDate) {
        Integer nextVersion = ReleaseKeeper.getInstance().getReleaseMap().entrySet().iterator().next().getValue();
        /* La opening version la trovo controllando quale versione viene subito dopo la creazione del ticket */

        for(Tag tag: ReleaseKeeper.getInstance().getReleaseKeySet()) {
            if(tag.getTagDate().after(tickedDate)) {
                nextVersion = ReleaseKeeper.getInstance().getIdFromTag(tag);
                break;
            }
        }

        return nextVersion;
    }

}
