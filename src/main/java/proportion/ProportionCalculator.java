package proportion;

import lombok.extern.slf4j.Slf4j;
import model.Release;
import model.Ticket;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
public class ProportionCalculator {

    public enum Strategy {
        EXCLUDE_4_0_0,
        FORCE_IV_TO_4_0_0
    }

    public static void applyProportionEstimation(List<Ticket> tickets,
                                                 List<Release> releases,
                                                 Strategy strategy) {

        int forcedIVs = forceIVsIfNeeded(tickets, releases, strategy);

        double avgProportion = computeProportion(tickets, strategy);
        log.info("📊 Avg proportion ({}): {}", strategy.name(), String.format("%.2f", avgProportion));

        int estimatedIVs = estimateMissingIVs(tickets, releases, avgProportion);
        log.info("✅ IV stimate con strategy {}: {}", strategy.name(), estimatedIVs);

        if (strategy == Strategy.FORCE_IV_TO_4_0_0) {
            log.info("📌 IV forzate a 4.0.0 con strategy FORCE_IV_TO_4_0_0: {}", forcedIVs);
        }
    }

    private static int forceIVsIfNeeded(List<Ticket> tickets, List<Release> releases, Strategy strategy) {
        if (strategy != Strategy.FORCE_IV_TO_4_0_0) return 0;

        int forced = 0;
        Release baseIV = releases.stream()
                .filter(r -> "4.0.0".equals(r.getName()))
                .findFirst()
                .orElse(null);

        if (baseIV == null) return 0;

        for (Ticket ticket : tickets) {
            boolean hasAV = ticket.getAffectedVersions() != null && !ticket.getAffectedVersions().isEmpty();
            boolean hasIV = ticket.getInjected() != null;

            if (!hasIV && hasAV) {
                Release earliestAV = ticket.getAffectedVersions().stream()
                        .min(Comparator.comparing(Release::getReleaseDate))
                        .orElse(null);

                if (earliestAV != null && "4.0.0".equals(earliestAV.getName())) {
                    ticket.setInjected(baseIV);
                    System.out.printf("[IV FORZATA] Ticket %s → IV: 4.0.0 (AV iniziale: %s)%n",
                            ticket.getKey(), earliestAV.getName());
                    forced++;
                }
            }
        }

        return forced;
    }

    private static double computeProportion(List<Ticket> tickets, Strategy strategy) {
        List<Ticket> valid = tickets.stream()
                .filter(t -> t.getFixed() != null && t.getInjected() != null && t.getOpening() != null)
                .filter(t -> strategy != Strategy.EXCLUDE_4_0_0 || !"4.0.0".equals(t.getInjected().getName()))
                .filter(t -> {
                    long ovToFv = ChronoUnit.DAYS.between(t.getOpening().getReleaseDate(), t.getFixed().getReleaseDate());
                    long ivToFv = ChronoUnit.DAYS.between(t.getInjected().getReleaseDate(), t.getFixed().getReleaseDate());
                    return ovToFv > 0 && ivToFv > 0 && ivToFv <= ovToFv;
                })
                .toList();

        OptionalDouble avg = valid.stream()
                .mapToDouble(t -> {
                    long ovToFv = ChronoUnit.DAYS.between(t.getOpening().getReleaseDate(), t.getFixed().getReleaseDate());
                    long ivToFv = ChronoUnit.DAYS.between(t.getInjected().getReleaseDate(), t.getFixed().getReleaseDate());
                    return (double) ivToFv / ovToFv;
                })
                .average();

        return avg.orElse(0.5); // fallback default
    }

    private static int estimateMissingIVs(List<Ticket> tickets, List<Release> releases, double proportion) {
        int estimated = 0;

        for (Ticket ticket : tickets) {
            // Salta se ha già IV derivata da AV
            if (ticket.getInjected() != null && ticket.getAffectedVersions() != null && !ticket.getAffectedVersions().isEmpty())
                continue;

            // Deve avere OV e FV per stimare
            if (ticket.getFixed() == null || ticket.getOpening() == null)
                continue;

            long fvOv = ChronoUnit.DAYS.between(ticket.getOpening().getReleaseDate(), ticket.getFixed().getReleaseDate());
            if (fvOv <= 0) continue;

            long fvIv = (long) (proportion * fvOv);
            if (fvIv <= 0) continue;

            var estimatedDate = ticket.getFixed().getReleaseDate().minusDays(fvIv);

            Release bestMatch = releases.stream()
                    .filter(r -> !r.getReleaseDate().isAfter(estimatedDate))
                    .max(Comparator.comparing(Release::getReleaseDate))
                    .orElse(null);

            if (bestMatch != null) {
                ticket.setInjected(bestMatch);
                System.out.printf("[IV STIMATA] Ticket %s → IV: %s%n", ticket.getKey(), bestMatch.getName());
                estimated++;
            }
        }

        return estimated;
    }

}
