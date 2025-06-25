package proportion;

import lombok.extern.slf4j.Slf4j;
import manager.CommitManager;
import manager.ReleasesManager;
import manager.TicketsManager;
import model.Release;
import model.Ticket;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ProportionCalculatorTest {

    public static void main(String[] args) throws IOException {
        // Step 1: Inizializza manager e recupera ticket filtrati con commit associati
        TicketsManager ticketsManager = new TicketsManager();
        CommitManager commitManager = new CommitManager(ticketsManager);
        commitManager.getCommitsWithTickets(); // Questo imposta anche le fix version

        ReleasesManager releasesManager = ticketsManager.getReleasesManager();
        List<Release> releases = releasesManager.getReleases();
        List<Ticket> tickets = ticketsManager.getTickets();

        // Step 2: Calcola opening version per ogni ticket
        Map<String, LocalDate> releaseDates = releases.stream()
                .collect(Collectors.toMap(
                        Release::getName,
                        Release::getReleaseDate,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        for (Ticket ticket : tickets) {
            String ov = VersionResolver.resolveOpeningVersion(ticket.getKey(), ticket.getIssueDate(), releaseDates);
            if (ov != null) {
                Release openingRelease = releases.stream()
                        .filter(r -> r.getName().equals(ov))
                        .findFirst()
                        .orElse(null);
                ticket.setOpening(openingRelease);
            }
        }

        // Step 3: Calcola IV da AV (più vicina a sinistra rispetto ad AV più vecchia)
        VersionResolver.resolveInjectedVersionsFromAffected(tickets, releases);

        // Step 4: Proportion con EXCLUDE_4_0_0
        System.out.println("\n=== STRATEGY: EXCLUDE_4_0_0 ===");
        ProportionCalculator.applyProportionEstimation(tickets, releases, ProportionCalculator.Strategy.EXCLUDE_4_0_0);

        // Step 5: Reset delle IV stimate solo da proportion (non quelle da AV)
        tickets.stream()
                .filter(t -> t.getInjected() != null && t.getAffectedVersions() == null)
                .forEach(t -> t.setInjected(null));

        // Step 6: Proportion con FORCE_IV_TO_4_0_0
        System.out.println("\n=== STRATEGY: FORCE_IV_TO_4_0_0 ===");
        ProportionCalculator.applyProportionEstimation(tickets, releases, ProportionCalculator.Strategy.FORCE_IV_TO_4_0_0);

        commitManager.close();
    }
}
