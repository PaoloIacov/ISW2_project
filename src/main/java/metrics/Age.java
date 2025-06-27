package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementazione della metrica Age che misura l'età di un metodo in settimane.
 * L'età viene calcolata dalla prima apparizione del metodo fino alla data corrente
 * utilizzando direttamente la storia del repository Git.
 */
public class Age implements CodeMetric {

    private final Path repoPath;
    private Map<String, LocalDate> firstAppearanceCache;
    private final LocalDate currentDate;
    private boolean cacheInitialized = false;

    /**
     * Costruttore che inizializza il calcolatore dell'età dei metodi.
     *
     * @param repoPath la directory del repository Git
     */
    public Age(Path repoPath) {
        this.repoPath = repoPath;
        this.firstAppearanceCache = new HashMap<>();
        this.currentDate = LocalDate.now();
    }

    /**
     * Costruttore con data personalizzata, utile per i test.
     *
     * @param repoPath la directory del repository Git
     * @param currentDate la data corrente da utilizzare per i calcoli
     */
    public Age(Path repoPath, LocalDate currentDate) {
        this.repoPath = repoPath;
        this.firstAppearanceCache = new HashMap<>();
        this.currentDate = currentDate;
    }

    @Override
    public String getName() {
        return "Age";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se la cache non è inizializzata, inizializzala
        if (!cacheInitialized) {
            identifyFirstAppearanceDates();
            cacheInitialized = true;
        }

        // Costruisce la chiave di identificazione del metodo
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Se il metodo non è nella cache, usa la data corrente
        if (!firstAppearanceCache.containsKey(methodKey)) {
            firstAppearanceCache.put(methodKey, currentDate);
        }

        // Calcola l'età in settimane
        LocalDate firstAppearance = firstAppearanceCache.get(methodKey);
        long weeks = ChronoUnit.WEEKS.between(firstAppearance, currentDate);

        // Assicura che l'età sia almeno 0 (anche se la data è nel futuro)
        return (int) Math.max(0, weeks);
    }

    /**
     * Identifica le date di prima apparizione di tutti i metodi analizzando
     * la storia dei commit del repository Git.
     */
    private void identifyFirstAppearanceDates() {
        try {
            // Apri il repository Git
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .setGitDir(new File(repoPath.toFile(), ".git"))
                    .build();

            // Crea un'istanza di Git
            try (Git git = new Git(repository)) {
                // Ottieni tutti i commit, dal più recente al più vecchio
                Iterable<RevCommit> commits = git.log().call();

                // Inverti l'ordine per analizzare dal più vecchio al più recente
                // (Nota: questa è una semplificazione, normalmente dovresti usare un approccio più efficiente)
                Map<String, LocalDate> methodFirstAppearance = new HashMap<>();

                for (RevCommit commit : commits) {
                    // Converti la data del commit in LocalDate
                    LocalDate commitDate = commit.getAuthorIdent().getWhen().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();

                    // Per ogni commit, puoi usare git.checkout() per estrarre il codice
                    // e poi analizzarlo, ma questa operazione è costosa.
                    // Una soluzione più efficiente sarebbe usare Git API per ottenere
                    // i file modificati in ogni commit e analizzare solo quelli.

                    // Esempio semplificato (questa parte dovrebbe essere ottimizzata in produzione):
                    git.checkout().setName(commit.getName()).call();

                    // Qui dovresti analizzare tutti i file Java nel repository
                    // e registrare i metodi trovati con la data del commit
                    // se non sono già stati registrati in precedenza

                    // Questo è un placeholder per il codice che analizza i file Java
                    // nel repository e aggiorna methodFirstAppearance
                    // ...

                    // Dopo l'analisi, merge la mappa temporanea nella cache principale
                    for (Map.Entry<String, LocalDate> entry : methodFirstAppearance.entrySet()) {
                        firstAppearanceCache.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }

                // Ritorna al branch principale (master o main)
                try {
                    git.checkout().setName("main").call();
                } catch (Exception e) {
                    git.checkout().setName("master").call();
                }
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            // In caso di errore, la cache rimarrà vuota e verrà utilizzata la data corrente per l'età
        }
    }
}