package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementazione della metrica MethodHistory
 * Conta il numero di volte in cui un metodo è stato modificato attraverso le diverse release
 * utilizzando direttamente Git invece di richiedere una directory storica separata.
 */
public class MethodHistory implements CodeMetric {

    private final Path repoPath;
    private Map<String, Integer> methodChangesCache;
    private boolean cacheInitialized = false;

    /**
     * Costruttore che inizializza la metrica
     *
     * @param repoPath percorso del repository Git
     */
    public MethodHistory(Path repoPath) {
        this.repoPath = repoPath;
        this.methodChangesCache = new HashMap<>();
    }

    @Override
    public String getName() {
        return "MethodHistory";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Inizializza la cache se non è stata ancora creata
        if (!cacheInitialized) {
            buildMethodHistoryMap();
            cacheInitialized = true;
        }

        // Costruisci la chiave del metodo (formato: ClasseCompleta.nomeMetodo)
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Restituisci il numero di cambiamenti per il metodo, o 0 se non presente
        return methodChangesCache.getOrDefault(methodKey, 0);
    }

    /**
     * Costruisce una mappa che contiene il numero di modifiche per ogni metodo
     * analizzando i commit del repository Git.
     */
    private void buildMethodHistoryMap() {
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

                // Per ogni metodo trovato, tieni traccia del codice nel commit precedente
                Map<String, String> previousVersionMethods = new HashMap<>();
                boolean firstCommit = true;

                for (RevCommit commit : commits) {
                    System.out.println("Analisi del commit: " + commit.getName());

                    // Estrai il commit
                    git.checkout().setName(commit.getName()).call();

                    // Aspetta che il filesystem si aggiorni
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Estrai tutti i metodi da questo commit
                    Map<String, String> currentVersionMethods = extractMethodsFromCurrentState();

                    // Salta il confronto per il primo commit analizzato (il più vecchio)
                    if (!firstCommit) {
                        // Confronta con la versione precedente
                        for (Map.Entry<String, String> entry : currentVersionMethods.entrySet()) {
                            String methodKey = entry.getKey();
                            String currentCode = entry.getValue();

                            // Se il metodo esisteva nella versione precedente e il codice è cambiato
                            if (previousVersionMethods.containsKey(methodKey) &&
                                    !previousVersionMethods.get(methodKey).equals(currentCode)) {
                                // Incrementa il contatore delle modifiche
                                methodChangesCache.put(methodKey, methodChangesCache.getOrDefault(methodKey, 0) + 1);
                            }
                        }
                    } else {
                        firstCommit = false;
                    }

                    // Aggiorna per la prossima iterazione
                    previousVersionMethods = currentVersionMethods;
                }

                // Ritorna al branch principale
                try {
                    git.checkout().setName("main").call();
                } catch (Exception e) {
                    git.checkout().setName("master").call();
                }
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }

    /**
     * Estrae tutti i metodi dallo stato corrente del repository
     */
    private Map<String, String> extractMethodsFromCurrentState() {
        Map<String, String> methods = new HashMap<>();

        try {
            // Trova tutti i file Java nel repository
            Files.walk(repoPath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(file.toFile());

                            // Ottieni il nome del pacchetto, se presente
                            String packageName = cu.getPackageDeclaration()
                                    .map(pd -> pd.getNameAsString() + ".")
                                    .orElse("");

                            // Cerca tutti i metodi nel file
                            cu.findAll(MethodDeclaration.class).forEach(md -> {
                                // Ottieni il nome della classe
                                String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                                        .map(c -> c.getNameAsString())
                                        .orElse("Unknown");

                                // Crea la chiave del metodo
                                String methodKey = packageName + className + "." + md.getNameAsString();
                                // Memorizza il codice del metodo
                                methods.put(methodKey, md.toString());
                            });
                        } catch (Exception e) {
                            // Ignora i file non parsificabili
                            System.err.println("Errore nell'analisi del file: " + file.toString() + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return methods;
    }
}