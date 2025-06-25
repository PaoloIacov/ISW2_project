package proportion;

import lombok.extern.slf4j.Slf4j;
import manager.CommitManager;
import manager.ReleasesManager;
import manager.TicketsManager;
import model.Release;
import model.Ticket;

import java.io.IOException;
import java.util.List;

@Slf4j
public class InjectedVersionTest {

    public static void main(String[] args) throws IOException {
        // Inizializza il TicketManager con filtro standard (bug fixed)
        TicketsManager ticketsManager = new TicketsManager();

        // Usa CommitManager per popolare anche i commit e applicare il filtro
        CommitManager commitManager = new CommitManager(ticketsManager);
        commitManager.getCommitsWithTickets();

        ReleasesManager releasesManager = ticketsManager.getReleasesManager();
        List<Release> allReleases = releasesManager.getReleases();

        List<Ticket> allFilteredTickets = ticketsManager.getTickets();

        // Filtra i ticket che hanno almeno una AV
        List<Ticket> ticketsWithAV = allFilteredTickets.stream()
                .filter(t -> t.getAffectedVersions() != null && !t.getAffectedVersions().isEmpty())
                .toList();

        log.info("🔍 Trovati {} ticket con almeno una Affected Version (AV).", ticketsWithAV.size());

        // Calcola le IV a partire dalle AV
        VersionResolver.resolveInjectedVersionsFromAffected(ticketsWithAV, allReleases);

        // Filtra i ticket che hanno ottenuto una IV
        List<Ticket> withIV = ticketsWithAV.stream()
                .filter(t -> t.getInjected() != null)
                .toList();

        log.info("✅ Injected Versions calcolate per {} ticket.", withIV.size());

        // Stampa IV con System.out
        System.out.println("\n--- IV ESTRAZIONI ---");
        withIV.forEach(t ->
                System.out.printf("Ticket %s → IV: %s%n", t.getKey(), t.getInjected().getName()));

        // Chiudi repo git
        commitManager.close();
    }
}
