package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementazione della metrica NAuthors che calcola il numero di autori diversi
 * che hanno contribuito a un metodo nel tempo.
 */
public class NAuthors implements CodeMetric {

    private final Path projectRoot;
    private Map<String, Set<String>> authorsByMethodCache;

    /**
     * Costruttore che inizializza il calcolatore di NAuthors
     *
     * @param projectRoot la radice del progetto Git
     */
    public NAuthors(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.authorsByMethodCache = new HashMap<>();
    }

    @Override
    public String getName() {
        return "NAuthors";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se la cache è vuota, popolala
        if (authorsByMethodCache.isEmpty()) {
            populateAuthorsCache();
        }

        // Costruisce la chiave di identificazione del metodo
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Restituisce il numero di autori o 0 se il metodo non è presente nella cache
        Set<String> authors = authorsByMethodCache.getOrDefault(methodKey, new HashSet<>());
        return authors.size();
    }

    /**
     * Popola la cache degli autori per tutti i metodi nel progetto
     */
    private void populateAuthorsCache() {
        try {
            // Verifica che siamo in un repository Git
            if (!isGitRepository()) {
                System.err.println("Il progetto non sembra essere un repository Git");
                return;
            }

            // Trova tutti i file Java nel progetto
            List<Path> javaFiles = Files.walk(projectRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                // Ottieni il percorso relativo del file rispetto alla radice del progetto
                Path relativePath = projectRoot.relativize(javaFile);

                // Estrai i metodi dal file
                Map<String, MethodDeclaration> methods = extractMethodsFromFile(javaFile);

                if (!methods.isEmpty()) {
                    // Per ogni metodo, estrai gli autori usando Git
                    for (Map.Entry<String, MethodDeclaration> entry : methods.entrySet()) {
                        String methodKey = entry.getKey();
                        MethodDeclaration methodDecl = entry.getValue();

                        // Ottieni la riga di inizio e fine del metodo
                        int startLine = methodDecl.getBegin().get().line;
                        int endLine = methodDecl.getEnd().get().line;

                        // Usa Git blame per ottenere gli autori per ogni riga
                        Set<String> methodAuthors = getAuthorsForMethod(relativePath.toString(), startLine, endLine);

                        // Salva nella cache
                        authorsByMethodCache.put(methodKey, methodAuthors);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verifica se il progetto corrente è un repository Git
     */
    private boolean isGitRepository() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree");
            pb.directory(projectRoot.toFile());
            Process process = pb.start();

            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();

            return "true".equalsIgnoreCase(output);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Estrae tutti i metodi da un file Java
     */
    private Map<String, MethodDeclaration> extractMethodsFromFile(Path file) {
        Map<String, MethodDeclaration> methods = new HashMap<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString() + ".")
                    .orElse("");

            // Trova tutti i metodi nel file
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                // Ottieni il nome della classe
                String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> c.getNameAsString())
                        .orElse("Unknown");

                // Crea la chiave del metodo
                String methodKey = packageName + className + "." + md.getNameAsString();
                methods.put(methodKey, md);
            });
        } catch (Exception e) {
            // Ignora i file che non possono essere analizzati
        }

        return methods;
    }

    /**
     * Usa Git blame per ottenere l'elenco degli autori che hanno modificato un metodo
     */
    private Set<String> getAuthorsForMethod(String filePath, int startLine, int endLine) {
        Set<String> authors = new HashSet<>();

        try {
            // Esegui git blame per le righe specificate
            ProcessBuilder pb = new ProcessBuilder(
                "git",
                "blame",
                "-p", // include dettagli completi
                "-L", startLine + "," + endLine, // intervallo di righe
                filePath
            );
            pb.directory(projectRoot.toFile());
            Process process = pb.start();

            boolean completed = process.waitFor(30, TimeUnit.SECONDS); // Timeout dopo 30 secondi
            if (!completed) {
                process.destroyForcibly();
                return authors;
            }

            // Leggi l'output e estrai gli autori
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("author ")) {
                    String author = line.substring("author ".length()).trim();
                    if (!author.isEmpty()) {
                        authors.add(author);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            // In caso di errore, lascia il set vuoto
        }

        return authors;
    }
}