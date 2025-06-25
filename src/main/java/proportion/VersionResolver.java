package proportion;

import lombok.extern.slf4j.Slf4j;
import model.CommitInfo;
import model.Release;
import model.Ticket;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
public class VersionResolver {

    private VersionResolver() {
        // Prevent instantiation
    }

    /**
     * Risolve la Fix Version come nome release.
     * Stampa solo se è stata stimata.
     */
    public static String resolveFixVersion(String ticketKey,
                                           List<? extends CommitInfo> commits,
                                           Map<String, LocalDate> releaseDates,
                                           Boolean ticketHadFixed) {
        if (commits == null || commits.isEmpty()) return null;

        LocalDate latestCommitDate = commits.stream()
                .map(CommitInfo::getCommitDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (latestCommitDate == null) return null;

        for (Map.Entry<String, LocalDate> entry : releaseDates.entrySet()) {
            if (!entry.getValue().isBefore(latestCommitDate)) {
                if (ticketHadFixed != null && !ticketHadFixed) {
                    System.out.printf("[FV STIMATA] %s → %s (ref: %s)%n", ticketKey, entry.getKey(), latestCommitDate);
                }
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Risolve la Opening Version come nome release.
     */
    public static String resolveOpeningVersion(String ticketKey,
                                               LocalDate ticketIssuedDate,
                                               Map<String, LocalDate> releaseDates) {

        String openingVersion = null;
        LocalDate latestReleaseDate = null;

        for (Map.Entry<String, LocalDate> entry : releaseDates.entrySet()) {
            LocalDate releaseDate = entry.getValue();
            if (!releaseDate.isAfter(ticketIssuedDate) &&
                    (latestReleaseDate == null || releaseDate.isAfter(latestReleaseDate))) {
                latestReleaseDate = releaseDate;
                openingVersion = entry.getKey();
            }
        }

        // Output compatto solo se non trovata
        if (openingVersion == null) {
            System.out.printf("[OV MANCANTE] %s → Nessuna release <= %s%n", ticketKey, ticketIssuedDate);
        }

        return openingVersion;
    }

    /**
     * Assegna la Injected Version come la più vicina release < AV.
     */
    public static void resolveInjectedVersionsFromAffected(List<Ticket> tickets, List<Release> allReleases) {
        int extractedIVCount = 0;
        int notFoundIVCount = 0;
        int noReleaseBeforeAVCount = 0;

        for (Ticket ticket : tickets) {
            List<Release> affected = ticket.getAffectedVersions();
            if (affected == null || affected.isEmpty()) continue;

            Release earliestAV = affected.stream()
                    .min(Comparator.comparing(Release::getReleaseDate))
                    .orElse(null);
            if (earliestAV == null) continue;

            Release injected = allReleases.stream()
                    .filter(r -> r.getReleaseDate().isBefore(earliestAV.getReleaseDate()))
                    .max(Comparator.comparing(Release::getReleaseDate))
                    .orElse(null);

            if (injected != null) {
                ticket.setInjected(injected);
                extractedIVCount++;
            } else {
                notFoundIVCount++;
                if ("4.0.0".equals(earliestAV.getName())) {
                    noReleaseBeforeAVCount++;
                }
            }
        }

        // Log finale compatto
        log.info("📍 [IV SUMMARY] ✅ Calcolate: {}  ⚠️ Non calcolabili: {}  📌 AV=4.0.0: {}",
                extractedIVCount, notFoundIVCount, noReleaseBeforeAVCount);
    }
}
