package proportion;

import lombok.extern.slf4j.Slf4j;
import manager.CommitManager;
import manager.ReleasesManager;
import manager.TicketsManager;
import model.CommitInfo;
import model.Release;
import model.Ticket;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FixVersionTest {

    public static void main(String[] args) {
        try {
            // STEP 1 - Inizializza managers
            TicketsManager ticketsManager = new TicketsManager();
            CommitManager commitManager = new CommitManager(ticketsManager);
            commitManager.getCommitsWithTickets();

            ReleasesManager releasesManager = ticketsManager.getReleasesManager();
            List<Release> releases = releasesManager.getReleases();

            // Mappa nome → data
            Map<String, LocalDate> releaseDates = releases.stream()
                    .collect(Collectors.toMap(
                            Release::getName,
                            Release::getReleaseDate,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new
                    ));

            List<Ticket> tickets = ticketsManager.getTickets();

            int total = tickets.size();
            int withFV = 0;
            int estimatedFV = 0;
            int notEstimable = 0;
            int counter = 1;

            for (Ticket ticket : tickets) {
                if (ticket.getFixed() != null) {
                    System.out.printf("%d. FV nota per ticket %s → %s%n", counter++, ticket.getKey(), ticket.getFixed().getName());
                    withFV++;
                    continue;
                }

                List<CommitInfo> commits = ticket.getAssociatedCommits();
                if (commits == null || commits.isEmpty()) {
                    log.warn("❌ Nessun commit associato a ticket {}", ticket.getKey());
                    notEstimable++;
                    continue;
                }

                LocalDate latestCommitDate = commits.stream()
                        .map(CommitInfo::getCommitDate)
                        .max(LocalDate::compareTo)
                        .orElse(null);

                boolean hasReleaseAfterCommit = releaseDates.values().stream()
                        .anyMatch(date -> !date.isBefore(latestCommitDate));

                if (!hasReleaseAfterCommit) {
                    log.warn("❌ Nessuna release successiva al commit per ticket {}", ticket.getKey());
                    notEstimable++;
                    continue;
                }

                String estimated = VersionResolver.resolveFixVersion(
                        ticket.getKey(),
                        commits,
                        releaseDates,
                        false
                );

                if (estimated != null) {
                    System.out.printf("%d. FV stimata per ticket %s → %s%n", counter++, ticket.getKey(), estimated);
                    estimatedFV++;
                } else {
                    log.warn("❌ Impossibile stimare FixVersion per ticket {} con referenceDate {}", ticket.getKey(), latestCommitDate);
                    notEstimable++;
                }
            }

            log.info("✅ Ticket totali analizzati: {}", total);
            log.info("📌 Ticket con FixVersion già nota: {}", withFV);
            log.info("🧩 Ticket con FixVersion stimata: {}", estimatedFV);
            log.info("⚠️  Ticket con FixVersion NON stimabile: {}", notEstimable);

            commitManager.close();
        } catch (IOException e) {
            System.err.println("Errore durante l'esecuzione: " + e.getMessage());
        }
    }
}
