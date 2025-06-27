package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementazione della metrica LOCAdded che calcola il numero di righe di codice
 * aggiunte a un metodo durante la sua evoluzione nelle diverse versioni.
 */
public class LOCAdded implements CodeMetric {

    private final Path historyDir;
    private Map<String, Integer> locAddedCache;

    /**
     * Costruttore che inizializza il calcolatore di LOCAdded
     *
     * @param historyDir la directory contenente le versioni precedenti del codice
     */
    public LOCAdded(Path historyDir) {
        this.historyDir = historyDir;
        this.locAddedCache = new HashMap<>();
    }

    @Override
    public String getName() {
        return "LOCAdded";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se la cache è vuota, popolala
        if (locAddedCache.isEmpty()) {
            calculateLocAddedForAllMethods(root);
        }

        // Costruisce la chiave di identificazione del metodo
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Restituisce il valore di linee aggiunte o 0 se il metodo non è presente nella cache
        return locAddedCache.getOrDefault(methodKey, 0);
    }

    /**
     * Calcola le linee di codice aggiunte per tutti i metodi analizzando le diverse versioni
     *
     * @param currentRoot la radice del progetto corrente
     */
    private void calculateLocAddedForAllMethods(Path currentRoot) {
        try {
            // Ottieni tutte le directory di versioni precedenti ordinate cronologicamente
            List<Path> versionDirs = Files.list(historyDir)
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());

            // Aggiungi anche la versione corrente
            versionDirs.add(currentRoot);

            // Mantieni traccia delle dimensioni dei metodi nelle diverse versioni
            Map<String, Integer> previousVersionLOC = new HashMap<>();

            for (Path versionDir : versionDirs) {
                Map<String, Integer> currentVersionLOC = extractMethodSizes(versionDir);

                // Confronta con la versione precedente per calcolare le linee aggiunte
                for (Map.Entry<String, Integer> entry : currentVersionLOC.entrySet()) {
                    String methodKey = entry.getKey();
                    int currentLOC = entry.getValue();

                    if (previousVersionLOC.containsKey(methodKey)) {
                        int previousLOC = previousVersionLOC.get(methodKey);

                        // Se il metodo è cresciuto, conteggia le linee aggiunte
                        if (currentLOC > previousLOC) {
                            int added = currentLOC - previousLOC;
                            locAddedCache.put(
                                methodKey,
                                locAddedCache.getOrDefault(methodKey, 0) + added
                            );
                        }
                    } else {
                        // Metodo nuovo, tutte le linee sono considerate aggiunte
                        locAddedCache.put(methodKey, currentLOC);
                    }
                }

                // Aggiorna per la prossima iterazione
                previousVersionLOC = new HashMap<>(currentVersionLOC);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Estrae le dimensioni (in LOC) di tutti i metodi da una specifica versione
     *
     * @param versionDir directory della versione da analizzare
     * @return mappa che associa ogni metodo alla sua dimensione in LOC
     */
    private Map<String, Integer> extractMethodSizes(Path versionDir) throws IOException {
        Map<String, Integer> methodSizes = new HashMap<>();

        // Trova tutti i file Java
        List<Path> javaFiles = Files.walk(versionDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path file : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString() + ".")
                        .orElse("");

                // Analizza tutti i metodi nel file
                cu.findAll(MethodDeclaration.class).forEach(md -> {
                    // Estrai il nome della classe contenente
                    String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                            .map(c -> c.getNameAsString())
                            .orElse("Unknown");

                    // Crea la chiave del metodo
                    String methodKey = packageName + className + "." + md.getNameAsString();

                    // Calcola le linee di codice per questo metodo
                    int loc = countLOC(md);
                    methodSizes.put(methodKey, loc);
                });
            } catch (Exception e) {
                // Ignora i file che non possono essere analizzati
            }
        }

        return methodSizes;
    }

    /**
     * Conta le linee di codice (non vuote) in un metodo
     *
     * @param method la dichiarazione del metodo da analizzare
     * @return il numero di linee di codice non vuote
     */
    private int countLOC(MethodDeclaration method) {
        if (method.getBody().isEmpty()) {
            return 0;
        }

        String code = method.getBody().get().toString();
        String[] lines = code.split("\n");

        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("{") && !trimmed.equals("}")) {
                count++;
            }
        }

        return count;
    }
}
