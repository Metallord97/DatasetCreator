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

public class ProportionLabeling {
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

        LinkedHashMap<Tag, List<Integer>> pMap = new LinkedHashMap<>();
        LinkedHashMap<Tag, Integer> release = GitUtils.getReleaseDate(git);
        Set<Tag> keySet = release.keySet();
        for(Tag key : keySet) {
            List<Integer> pValues = new ArrayList<>();
            pMap.put(key, pValues);
            System.out.println(key.getTagDate() + " " + key.getTagName() + " "+ release.get(key));
        }
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            JSONObject fields = issue.getJSONObject("fields");
            String tickedID = issue.get("key").toString();
            JSONArray versions = fields.getJSONArray("versions");
            String resolutionDate = fields.get("resolutiondate").toString();
            String created =  fields.get("created").toString();
            if(versions.length() == 0) continue;
            Date resDate = ParseUtils.convertToDate(resolutionDate);
            Date creationDate = ParseUtils.convertToDate(created);
            Integer injectedVersion = ProportionLabeling.getInjectedVersion(release, versions);
            Integer openingVersion = ProportionLabeling.getNextVersion(release, creationDate);
            Integer fixedVersion = ProportionLabeling.getNextVersion(release, resDate);
            System.out.println("Creation Date: " + creationDate + " Resolution date: " + resDate);
            System.out.println(tickedID + ": Injected Version: " + injectedVersion + " Opening Version: " + openingVersion + " Fixed Version: " + fixedVersion);

            if(injectedVersion < openingVersion && openingVersion < fixedVersion) {
                Integer p = (fixedVersion - injectedVersion) / (fixedVersion - openingVersion);
                Tag key = MapUtils.getKeyByValue(release, fixedVersion);
                pMap.get(key).add(p);
            }

        }
        int counter = 1;
        Set<Tag> versionSet = pMap.keySet();
        for(Tag version : versionSet) {
            System.out.println(counter + ": " + version.getTagName() + " " + version.getTagDate() + ": " + pMap.get(version).toString());
            counter += 1;
        }
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
        counter = 0;
        Set<Tag> keys = pValue.keySet();
        for(Tag version : keys) {
            System.out.println(counter + ": " + version.getTagName() + " " + version.getTagDate() + ": " + pValue.get(version));
            counter += 1;
        }
    }

    public Integer computePredictedIV(Git git, Date creationTickedDate, Date fixedTickedDate) throws GitAPIException, IOException {
        LinkedHashMap<Tag, Integer> releases = GitUtils.getReleaseDate(git);
        Integer FV = ProportionLabeling.getNextVersion(releases, fixedTickedDate);
        Integer OV = ProportionLabeling.getNextVersion(releases, creationTickedDate);
        Integer P = this.getP(fixedTickedDate);

        if(Objects.equals(FV, OV)) {
            System.out.println("Opening Version: " + OV + " Fixed Version: " + FV);
            return (FV - P);
        }
        else {
            System.out.println("Opening Version: " + OV + " Fixed Version: " + FV + " P: " + P);
            return (FV - (FV - OV) * P);
        }
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


    public static Integer getInjectedVersion(LinkedHashMap<Tag, Integer> release, JSONArray affectedVersions) {
        /* L'injected versione è la affected version più vecchia */
        Set<Tag> keySet = release.keySet();
        Integer injectedVersion = Integer.MAX_VALUE;

        for(int i = 0; i <affectedVersions.length(); i++) {

            String affectedVersion = affectedVersions.getJSONObject(i).get("name").toString();
            for(Tag key : keySet) {
                if(key.getTagName().contains(affectedVersion) && release.get(key) < injectedVersion) {
                    injectedVersion = release.get(key);
                }
            }

        }

        return injectedVersion;
    }

    public static Integer getNextVersion(LinkedHashMap<Tag, Integer> release, Date tickedDate) {
        Integer nextVersion = release.entrySet().iterator().next().getValue();
        /* La opening version la trovo controllando quale versione viene subito dopo la creazione del ticket */

        Set<Tag> dateSet = release.keySet();
        for(Tag tag: dateSet) {
            if(tag.getTagDate().after(tickedDate)) {
                nextVersion = release.get(tag);
                break;
            }
        }

        return nextVersion;
    }

}
