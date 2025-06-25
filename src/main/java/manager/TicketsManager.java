package manager;

import config.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import model.Release;
import model.Ticket;
import model.TicketFilter;
import model.enums.ResolutionType;
import model.enums.TicketStatus;
import model.enums.TicketType;
import org.apache.logging.log4j.core.Version;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.JSONUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class TicketsManager{

    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").appendOffset("+HHMM", "Z").toFormatter();
    private static final int PAGE_SIZE = 100;

    private final String projectName;
    private final String baseUrl;
    private final JSONUtils jsonUtils;

    @Getter
    private final List<Ticket> tickets;
    private final List<Ticket> ticketsWithNoFixRelease;
    @Getter
    private final ReleasesManager releasesManager;

    public TicketsManager() {
        this.projectName = PropertiesManager.getInstance().getProperty("info.name");
        this.baseUrl = PropertiesManager.getInstance().getProperty("info.jira.baseUrl");
        this.jsonUtils = new JSONUtils();

        this.releasesManager = new ReleasesManager();
        releasesManager.getReleasesInfo();

        this.tickets = new ArrayList<>();
        this.ticketsWithNoFixRelease = new ArrayList<>();
    }

    public void clear() {
        this.tickets.clear();
        this.ticketsWithNoFixRelease.clear();
    }

    public void retrieveTickets() {
        this.retrieveTickets(new TicketFilter());
    }

    /**
     * Retrieves all tickets corresponding to the filter
     *
     * @param ticketFilter the ticket's filter
     */
    public void retrieveTickets(TicketFilter ticketFilter) {
        int i = 0;
        int j;
        int total = 1;
        String baseUrl = buildUrlFromFilter(ticketFilter);
        String url;
        // Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 100 at a time, so must do this multiple times if bugs > 100
            url = String.format(baseUrl, i, PAGE_SIZE);
            JSONObject json;
            try {
                json = jsonUtils.readJsonFromUrl(url);
            } catch (IOException e) {
                log.error("Unable to retrieve tickets IDs: {}", e.getMessage());
                return;
            }

            // Retrieves the "issues" array
            JSONArray issues = json.getJSONArray("issues");

            // Set the total number of issues found
            if (json.getInt("total") != total) {
                total = json.getInt("total");
                log.info("Total number of issues: {}", total);
            }

            // For each retrieved issue, adds a ticket to the list
            for (j = 0; j < issues.length(); j++) {
                // Iterate through each ticket
                JSONObject ticketJson = issues.getJSONObject(j);
                tickets.add(getTicketFromJson(ticketJson));
            }
            i += j;
        } while (i < total);

        log.info("Number of valid tickets found: {}", tickets.size());
        if (!ticketsWithNoFixRelease.isEmpty()) {
            // Output tickets with no fix Release
            log.warn("Warning: the following {} tickets were found with no fix Releases", ticketsWithNoFixRelease.size());
            for (Ticket ticket : ticketsWithNoFixRelease)
                log.warn(ticket.getKey());
        }
    }

    public void setFixVersionToTickets() {
        tickets.stream().filter(t -> t.getFixed() == null).forEach(ticket -> {
            if (ticket.getAssociatedCommits() != null && !ticket.getAssociatedCommits().isEmpty()) {

                // Gets the last commit date and retrieves the first version released after
                // that date: this is then set as the fix version
                // Not checking for NullPointerException because of the control on the ticket's associated commits
                LocalDate lastCommitDate = ticket.getLastCommit().getCommitDate();
                Release fixVersion = getFirstVersionAfterDate(lastCommitDate);
                if (fixVersion != null) ticket.setFixed(fixVersion);
                else log.warn("No version exists after the last commit date for ticket {}.", ticket.getKey());
            } else log.warn("Ticket {} has no associated commits", ticket.getKey());
        });
    }

    /**
     * Parses a JSON to recover the ticket fields
     *
     * @param ticketJson the JSON of the ticket
     * @return a ticket object
     */
    private Ticket getTicketFromJson(JSONObject ticketJson) {
        JSONObject fields = ticketJson.getJSONObject("fields");

        // Parsing date di apertura e chiusura
        LocalDate issuedDate = LocalDateTime.parse(fields.getString("created"), formatter).toLocalDate();
        LocalDate closedDate = fields.opt("resolutiondate") != null && !fields.get("resolutiondate").toString().equals("null")
                ? LocalDateTime.parse(fields.getString("resolutiondate"), formatter).toLocalDate()
                : LocalDateTime.parse(fields.getString("updated"), formatter).toLocalDate();

        // Parsing tipo e stato del ticket
        TicketType issueType = TicketType.from(fields.getJSONObject("issuetype").getString("name"));
        TicketStatus status = TicketStatus.fromString(fields.getJSONObject("status").getString("name"));

        // Parsing dell'assignee (opzionale)
        String assignee = "";
        if (fields.has("assignee") && !fields.isNull("assignee")) {
            assignee = fields.getJSONObject("assignee").optString("name", "");
        }

        // Parsing della resolution (opzionale)
        ResolutionType resolutionType = null;
        if (fields.has("resolution") && !fields.isNull("resolution")) {
            resolutionType = ResolutionType.fromResolution(fields.getJSONObject("resolution").getString("name"));
        }

        // Crea il ticket base
        Ticket ticket = new Ticket(
                ticketJson.getString("id"),
                ticketJson.getString("key"),
                issuedDate,
                closedDate,
                issueType,
                status,
                assignee
        );
        ticket.setResolution(resolutionType);

        // Fix Version
        Release fixRelease = getFixReleaseFromTicketJson(ticketJson);
        ticket.setFixed(fixRelease);
        if (fixRelease == null) {
            ticketsWithNoFixRelease.add(ticket);
        }

        // Affected Versions
        JSONArray affectedArray = fields.optJSONArray("versions");
        List<Release> allReleases = releasesManager.getReleases();
        List<Release> affectedVersions = new ArrayList<>();

        if (affectedArray != null) {
            for (int i = 0; i < affectedArray.length(); i++) {
                String id = affectedArray.getJSONObject(i).optString("id", null);
                if (id != null) {
                    allReleases.stream()
                            .filter(r -> r.getId().equals(id))
                            .findFirst()
                            .ifPresent(affectedVersions::add);
                }
            }
        }

        ticket.setAffectedVersions(affectedVersions);

        return ticket;
    }


    private Release getFixReleaseFromTicketJson(JSONObject ticketJson) {
        JSONArray fixReleasesArray = ticketJson.getJSONObject("fields").getJSONArray("fixVersions");

        if (fixReleasesArray == null || fixReleasesArray.isEmpty()) {
            return null;
        }

        List<Release> releases = releasesManager.getReleases();
        List<Release> ticketFixReleases = null;
        Release fixRelease = null;

        for (int i = 0; i < fixReleasesArray.length(); i++) {
            JSONObject fixReleaseObj = fixReleasesArray.getJSONObject(i);
            String releaseId = fixReleaseObj.getString("id");

            // Using `new ArrayList<>` to create a mutable list
            ticketFixReleases = new ArrayList<>(releases.stream().filter(v -> v.getId().equals(releaseId)).toList());
        }

        if (ticketFixReleases != null && !ticketFixReleases.isEmpty()) {
            if (ticketFixReleases.size() > 1)
                ticketFixReleases.sort((v1, v2) -> v2.getReleaseDate().compareTo(v1.getReleaseDate()));
            fixRelease = ticketFixReleases.get(0);
        }

        return fixRelease;
    }

    public Release getFirstVersionAfterDate(LocalDate date) {
        return releasesManager.getReleases().stream()
                .filter(r -> !r.getReleaseDate().isBefore(date))  // cioè: r.date >= date
                .min(Comparator.comparing(Release::getReleaseDate))  // la più vicina
                .orElse(null);
    }



    /**
     * Builds a URL to query the Jira REST API according to some filters
     *
     * @param ticketFilter the filter with fields
     * @return the URL with filters set
     */
    private String buildUrlFromFilter(TicketFilter ticketFilter) {
        StringBuilder url = new StringBuilder(baseUrl + "search?jql=project=\"" + projectName + "\"");

        if (ticketFilter.getStatuses() != null && !ticketFilter.getStatuses().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (TicketStatus status : ticketFilter.getStatuses()) {
                if (!first) url.append("OR");
                url.append("\"status\"=\"").append(status.getStatus()).append("\"");
                first = false;
            }
            url.append(")");
        }

        if (ticketFilter.getTypes() != null && !ticketFilter.getTypes().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (TicketType type : ticketFilter.getTypes()) {
                if (!first) url.append("OR");
                url.append("\"issueType\"=\"").append(type).append("\"");
                first = false;
            }
            url.append(")");
        }

        if (ticketFilter.getResolutions() != null && !ticketFilter.getResolutions().isEmpty()) {
            url.append("AND(");
            boolean first = true;
            for (ResolutionType type : ticketFilter.getResolutions()) {
                if (!first) url.append("OR");
                url.append("\"resolution\"=\"").append(type).append("\"");
                first = false;
            }
            url.append(")");
        }

        url.append("&startAt=%d&maxResults=%d");

        return url.toString();
    }

}
