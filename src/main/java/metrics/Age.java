package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Calcola la metrica Age per ogni metodo: settimane dalla prima apparizione a oggi.
 */
public class Age implements CodeMetric {

    private final Path repoPath;
    private final LocalDate currentDate;
    private Map<String, LocalDate> firstAppearanceCache;
    private boolean cacheInitialized = false;

    public Age(Path repoPath) {
        this(repoPath, LocalDate.now());
    }

    public Age(Path repoPath, LocalDate currentDate) {
        this.repoPath = repoPath;
        this.currentDate = currentDate;
        this.firstAppearanceCache = new HashMap<>();
    }

    @Override
    public String getName() {
        return "Age";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        if (!cacheInitialized) {
            identifyFirstAppearanceDates();
            cacheInitialized = true;
        }

        String methodKey = buildMethodKey(cls, method);

        // Se il metodo non è in cache, viene considerato nato oggi
        LocalDate firstAppearance = firstAppearanceCache.getOrDefault(methodKey, currentDate);
        long weeks = ChronoUnit.WEEKS.between(firstAppearance, currentDate);
        return (int) Math.max(0, weeks);
    }

    /**
     * Costruisce una chiave univoca per il metodo
     */
    private String buildMethodKey(CKClassResult cls, CKMethodResult method) {
        return cls.getClassName() + "." + method.getMethodName() + "/" + method.getParametersQty();
    }

    /**
     * Scorre tutti i commit (dal più vecchio), estrae i metodi e aggiorna la cache della prima apparizione.
     */
    private void identifyFirstAppearanceDates() {
        Repository repository = null;
        Git git = null;
        String originalBranch = null;

        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder.setGitDir(new File(repoPath.toFile(), ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            git = new Git(repository);

            // Salva il branch corrente per poi tornarci
            originalBranch = repository.getBranch();

            // Ordina i commit dal più vecchio al più recente
            List<RevCommit> commits = new ArrayList<>();
            for (RevCommit c : git.log().all().call()) {
                commits.add(c);
            }
            commits.sort(Comparator.comparingInt(RevCommit::getCommitTime)); // più vecchio prima

            for (RevCommit commit : commits) {
                // Checkout del commit
                git.checkout().setName(commit.getName()).call();

                LocalDate commitDate = commit.getAuthorIdent().getWhen().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                // Scansiona tutti i file .java
                List<Path> javaFiles = getAllJavaFiles(repoPath);

                for (Path javaFile : javaFiles) {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(javaFile);
                        cu.findAll(MethodDeclaration.class).forEach(md -> {
                            String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                                    .map(c -> c.getNameAsString())
                                    .orElse("UnknownClass");
                            int paramQty = md.getParameters().size();
                            String methodKey = className + "." + md.getNameAsString() + "/" + paramQty;

                            // Se mai visto prima, salva la data
                            firstAppearanceCache.putIfAbsent(methodKey, commitDate);
                        });
                    } catch (Exception e) {
                        // File non parsabile o altro errore: ignora
                    }
                }
            }

        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        } finally {
            // Torna al branch di partenza (main/master)
            if (git != null && originalBranch != null) {
                try {
                    git.checkout().setName(originalBranch).call();
                } catch (Exception e) {
                    // fallback silenzioso
                }
            }
            if (repository != null) repository.close();
            if (git != null) git.close();
        }
    }

    /**
     * Ritorna la lista di tutti i file .java nel repo checkoutato
     */
    private List<Path> getAllJavaFiles(Path repoRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(repoRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(files::add);
        return files;
    }
}
