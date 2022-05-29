package labeling;

import mydatatype.CompositeKey;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.GitUtils;
import utils.ParseUtils;
import utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class Labeling {
    private Labeling() {}
    /**
     *
     * @param git
     * @return  Questo metodo ritorna una lista di CompositeKey che contiene quindi le coppie (release, classe) buggy
     * @throws IOException
     * @throws GitAPIException
     * @throws ParseException
     */
    public static List<CompositeKey> affectedVersionLabeling (Git git) throws IOException, GitAPIException, ParseException {
        List<CompositeKey> buggyClasses = new ArrayList<>();
        Map<Tag, Integer> releases = GitUtils.getReleaseDate(git);
        JSONArray issues = RetrieveTicketsID.retrieveTicketIDs();
        ProportionLabeling proportionLabeling = ProportionLabeling.getInstance();
        proportionLabeling.incrementalProportion(git, issues);

        /* Mi prendo tutti i ticket e cerco su git il commit corrispondente
        *  Per i ticket che riportano l'affected version posso fare direttamente il labeling
        *  Se l'affected version non è riportata devo usare proportion */
        for(int i = 0; i < issues.length(); i++) {
            JSONObject jsonObject = issues.getJSONObject(i);
            String key = jsonObject.get("key").toString();
            JSONObject fields = jsonObject.getJSONObject("fields");
            JSONArray versionsJson = fields.getJSONArray("versions");
            String resolutionDate = fields.get("resolutiondate").toString();
            String created = fields.get("created").toString();
            if(versionsJson.length() == 0) {
                Integer predictedIV = proportionLabeling.computePredictedIV(git, ParseUtils.convertToDate(created), ParseUtils.convertToDate(resolutionDate));
                buggyClasses.addAll(Labeling.getAffectedVersions(git, ParseUtils.convertToDate(resolutionDate), predictedIV, key, releases));
            }
            else {
                List<String> versions = Labeling.jsonArrayToList(versionsJson, "name");
                buggyClasses.addAll(Labeling.simpleLabeling(git,versions, key));
            }

        }

        return buggyClasses;
    }

    /**
     * Prende come input un JSONArray e il nome del campo di interesse. Per ogni JSONObject nell'array
     * prende il campo selezionato e lo aggiunge alla lista.
     * @param jsonArray JSONArray che contiene JSONObject
     * @param fieldName nome del campo di interesse nel JSONObject
     * @return ritorna una lista di stringhe
     */
    public static List<String> jsonArrayToList(JSONArray jsonArray, String fieldName) {
        List<String> stringList = new ArrayList<>();

        for(int i = 0; i < jsonArray.length(); i++) {
            String entry = jsonArray.getJSONObject(i).get(fieldName).toString();
            stringList.add(entry);
        }

        return stringList;
    }

    /**
     * Prende come input un oggetto Git, l'id del ticked, e la lista delle release difettose.
     * Fa un diff sul repository per vedere quali classi sono state modificate dal relativo commit e le classifica come buggy.
     * @param git
     * @param versions
     * @param ticketID
     * @return Ritorna una lista di CompositeKey (release, classe) che sono buggy
     * @throws GitAPIException
     * @throws IOException
     */
    public static List<CompositeKey> simpleLabeling(Git git, List<String> versions, String ticketID) throws GitAPIException, IOException {
        List<CompositeKey> affectedVersion = new ArrayList<>();
        List<String> buggyClasses = GitUtils.getDiffClasses(git, ticketID);

        for(String version : versions) {
            for (String buggyClass : buggyClasses) {
                CompositeKey key = new CompositeKey("release-" + version, buggyClass);
                affectedVersion.add(key);
            }
        }

        return affectedVersion;
    }

    /**
     * Effettua prima una git diff sul commit con l'id del ticket per vedere quali classi sono state toccate da quel commit.
     * Poi calcola la fixed version e aggiunge alla lista delle affected version ogni release compresa tra predictedIV e fixedVersion
     * @param git oggetto che rappresenta il collegamento al repository git
     * @param fixedDate data della chiusura del ticket
     * @param predictedIV injected version predetta con proportion
     * @param ticketID id del ticket in jira
     * @param release mappa che associa ogni release con la sua data a un numero in ordine di uscita temporale
     * @return Ritorna una lista con le CompositeKey buggy
     * @throws GitAPIException
     * @throws IOException
     */
    public static List<CompositeKey> getAffectedVersions(Git git, Date fixedDate, Integer predictedIV, String ticketID, Map<Tag, Integer> release) throws GitAPIException, IOException {
        List<CompositeKey> affectedVersion = new ArrayList<>();
        List<String> buggyClasses = GitUtils.getDiffClasses(git, ticketID);

        int fixedVersion = ProportionLabeling.getNextVersion(release, fixedDate);
        Set<Tag> tagSet = release.keySet();
        for (Tag tag : tagSet) {
            if(release.get(tag) >= predictedIV && release.get(tag) < fixedVersion) {
                for(String buggyClass : buggyClasses) {
                    CompositeKey key = new CompositeKey(StringUtils.removeSubstring(tag.getTagName(), "refs/tags/"), buggyClass);
                    affectedVersion.add(key);
                }
            }
        }

        return affectedVersion;
    }

}
