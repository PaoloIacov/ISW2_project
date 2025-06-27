package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Implementazione della metrica Churn che calcola le modifiche di un metodo
 * confrontando il suo codice con versioni precedenti.
 */
public class Churn implements CodeMetric {

    private final Path historyDir;
    private final Map<String, Integer> churnCache;
    private final Map<String, String> previousVersionHashes;

    /**
     * Costruttore che inizializza il calcolatore di churn
     *
     * @param historyDir la directory contenente le versioni precedenti del codice
     */
    public Churn(Path historyDir) {
        this.historyDir = historyDir;
        this.churnCache = new HashMap<>();
        this.previousVersionHashes = new HashMap<>();
    }

    @Override
    public String getName() {
        return "Churn";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se la cache è vuota, popolala
        if (churnCache.isEmpty()) {
            calculateChurnForAllMethods(root);
        }

        // Costruisce la chiave di identificazione del metodo
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Restituisce il valore di churn o 0 se il metodo non è presente nella cache
        return churnCache.getOrDefault(methodKey, 0);
    }

    /**
     * Calcola il churn per tutti i metodi analizzando le diverse versioni
     *
     * @param currentRoot la radice del progetto corrente
     */
    private void calculateChurnForAllMethods(Path currentRoot) {
        try {
            // Ottieni tutte le directory di versioni precedenti ordinate
            List<Path> versionDirs = Files.list(historyDir)
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());

            // Aggiungi anche la versione corrente
            versionDirs.add(currentRoot);

            for (Path versionDir : versionDirs) {
                // Analizza tutti i file Java in questa versione
                processSingleVersion(versionDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Elabora una singola versione del codebase
     */
    private void processSingleVersion(Path versionDir) throws IOException {
        // Trova tutti i file Java
        List<Path> javaFiles = Files.walk(versionDir)
                .filter(p -> p.toString().endsWith(".java"))
                .toList();

        // Estrai e analizza i metodi da ogni file
        for (Path file : javaFiles) {
            try {
                // Estrai i metodi dal file corrente
                Map<String, String> methodSources = extractMethodsFromFile(file, versionDir);

                // Confronta con versioni precedenti e aggiorna il churn
                for (Map.Entry<String, String> entry : methodSources.entrySet()) {
                    String methodKey = entry.getKey();
                    String methodSource = entry.getValue();
                    String currentHash = calculateHash(methodSource);

                    // Se questo metodo esiste già in una versione precedente,
                    // controlla se è cambiato
                    if (previousVersionHashes.containsKey(methodKey)) {
                        String previousHash = previousVersionHashes.get(methodKey);
                        if (!previousHash.equals(currentHash)) {
                            // Il metodo è cambiato, incrementa il churn
                            churnCache.put(methodKey, churnCache.getOrDefault(methodKey, 0) + 1);
                        }
                    }

                    // Aggiorna l'hash per la prossima iterazione
                    previousVersionHashes.put(methodKey, currentHash);
                }
            } catch (Exception e) {
                // Ignora i file che non possono essere analizzati
            }
        }
    }

    /**
     * Estrae i metodi da un file Java
     */
    private Map<String, String> extractMethodsFromFile(Path file, Path versionRoot) {
        Map<String, String> methods = new HashMap<>();

        // Qui dovresti utilizzare JavaParser per estrarre i metodi
        // Questo è solo uno scheletro, nella tua implementazione reale
        // potresti riutilizzare la logica di SourceCodeExtractor

        // Esempio semplificato di estrazione metodi:
        try {
            com.github.javaparser.StaticJavaParser.parse(file)
                .findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
                .forEach(md -> {
                    String className = getFullClassName(md, file);
                    String methodName = md.getNameAsString();
                    String methodKey = className + "." + methodName;
                    methods.put(methodKey, md.toString());
                });
        } catch (Exception e) {
            // Ignora i file che non possono essere parsificati
        }

        return methods;
    }

    /**
     * Ottiene il nome completo della classe contenente il metodo
     */
    private String getFullClassName(com.github.javaparser.ast.body.MethodDeclaration md, Path file) {
        // Questo è un metodo semplificato per ottenere il nome completo della classe
        // Nella tua implementazione reale dovresti combinare il package e il nome della classe

        try {
            String packageName = md.findCompilationUnit().get()
                .getPackageDeclaration()
                .map(pkg -> pkg.getNameAsString() + ".")
                .orElse("");

            String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .map(cls -> cls.getNameAsString())
                .orElse("UnknownClass");

            return packageName + className;
        } catch (Exception e) {
            // Fallback: usa il nome del file
            String fileName = file.getFileName().toString();
            return fileName.substring(0, fileName.lastIndexOf("."));
        }
    }

    /**
     * Calcola un hash del codice sorgente del metodo
     */
    private String calculateHash(String methodSource) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(methodSource.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback: usa un hashcode semplice
            return String.valueOf(methodSource.hashCode());
        }
    }
}