package manager;


import config.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import model.Release;
import org.json.JSONArray;
import utils.JSONUtils;


import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
public class ReleasesManager{

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String projectName;

    private final String url;

    @Getter
    private final List<Release> releases;

    private final JSONUtils jsonUtils;

    public ReleasesManager() {
        this.projectName = PropertiesManager.getInstance().getProperty("info.name").toUpperCase(Locale.ROOT);
        String baseUrl = PropertiesManager.getInstance().getProperty("info.jira.baseUrl");

        this.url = String.format(baseUrl + "project/%s/versions", projectName);

        this.releases = new ArrayList<>();
        this.jsonUtils = new JSONUtils();
    }

    /**
     * Retrieves all the releases for the project. Ignores releases with missing dates
     */
    public void getReleasesInfo() {
        getReleasesInfo(1.0);
    }

    /**
     * Retrieves the first percentage-% of the releases for the project. Ignores releases with missing dates
     */
    public void getReleasesInfo(double percentage) {
        JSONArray releases;
        try {
            releases = jsonUtils.readJsonArrayFromUrl(url);
        } catch (IOException e) {
            log.error("Unable to retrieve Releases from {} : {}", url, e.getMessage());
            return;
        }

        this.releases.clear();
        // Takes the "Releases" field from the JSON
        int releasesNumber = releases.length();
        int releasesToBeConsidered = (int) Math.ceil(releasesNumber * percentage);
        String name;
        String id;
        boolean released = false;
        for (int i = 0; i < releasesToBeConsidered; i++) {
            name = id = "";
            if (releases.getJSONObject(i).has("releaseDate")) {
                if (releases.getJSONObject(i).has("name")) name = releases.getJSONObject(i).get("name").toString();
                if (releases.getJSONObject(i).has("id")) id = releases.getJSONObject(i).get("id").toString();
                if (releases.getJSONObject(i).has("released"))
                    released = releases.getJSONObject(i).getBoolean("released");
                this.releases.add(new Release(id, name, LocalDate.parse(releases.getJSONObject(i).getString("releaseDate")), released));
            } else {
                log.error("Release {} has no release date", id);
            }
        }

        // Order releases by date
        this.releases.sort(Comparator.comparing(Release::getReleaseDate));

        for (Release availableRelease : this.releases) {
            log.info("Available release: {}", availableRelease.getName());
        }
    }

    /**
     * Writes release info to a CSV file named "ProjectNameReleaseInfo.csv"
     */
    public void outputReleaseInfo() {
        // Name of CSV for output
        String outFileName = projectName + "ReleaseInfo.csv";
        try (FileWriter fileWriter = new FileWriter(outFileName)) {
            // Set CSV header
            fileWriter.append("Index;Release ID;Release Name;Date");
            // Writes the CSV content
            Release release;
            for (int i = 0; i < releases.size(); i++) {
                fileWriter.append("\n");
                release = releases.get(i);
                fileWriter.append(String.valueOf(i + 1));
                fileWriter.append(";");  // Modificato da ',' a ';'
                fileWriter.append(release.getId());
                fileWriter.append(";");  // Modificato da ',' a ';'
                fileWriter.append(release.getName());
                fileWriter.append(";");  // Modificato da ',' a ';'
                fileWriter.append(release.getReleaseDate().format(DATE_FORMATTER));
            }
        } catch (Exception e) {
            System.err.println("Error while writing on CSV: " + e.getMessage());
        }
    }
}
