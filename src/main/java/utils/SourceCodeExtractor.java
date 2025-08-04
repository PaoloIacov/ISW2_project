package utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SourceCodeExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(SourceCodeExtractor.class);
    private static final int MAX_FILE_SIZE_MB = 5;

    /**
     * Estrae il codice sorgente di un metodo specifico
     */
    public static String getMethodSource(CKMethodResult method, Path root) {
        try {
            MethodInfo methodInfo = extractMethodInfo(method);
            Path sourceFile = findSourceFile(root, methodInfo.classPath);

            if (sourceFile == null) {
                LOG.warn("File sorgente non trovato per: {}", methodInfo.fullName);
                return null;
            }

            return extractMethodCode(sourceFile, methodInfo);
        } catch (Exception e) {
            LOG.error("Errore nell'estrazione del metodo: {}", method.getMethodName(), e);
            return null;
        }
    }

    /**
     * Estrae tutti i metodi da una directory, con filtraggio avanzato
     */
    public static List<MethodCodeInfo> extractAllMethods(Path root, boolean includeTests) {
        LOG.info("Estrazione metodi dalla directory: {}", root);
        List<MethodCodeInfo> methods = new ArrayList<>();

        try {
            List<Path> javaFiles = findJavaFiles(root, includeTests);
            LOG.info("Trovati {} file Java da analizzare", javaFiles.size());

            for (Path file : javaFiles) {
                try {
                    methods.addAll(extractMethodsFromFile(file, root));
                } catch (Exception e) {
                    LOG.warn("Errore nell'elaborazione del file {}: {}", file, e.getMessage());
                }
            }

            LOG.info("Estratti {} metodi totali", methods.size());
            return methods;
        } catch (IOException e) {
            LOG.error("Errore nella ricerca dei file Java", e);
            return List.of();
        }
    }

    /**
     * Estrae i metodi da un singolo file
     */
    private static List<MethodCodeInfo> extractMethodsFromFile(Path file, Path root) throws IOException {
        if (Files.size(file) > MAX_FILE_SIZE_MB * 1024 * 1024) {
            LOG.warn("File troppo grande, ignorato: {}", file);
            return List.of();
        }

        String relativePath = root.relativize(file).toString().replace('\\', '/');
        List<MethodCodeInfo> methods = new ArrayList<>();

        ParserConfiguration config = new ParserConfiguration();
        JavaParser parser = new JavaParser(config);

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ParseResult<CompilationUnit> result = parser.parse(reader);

            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                String packageName = cu.getPackageDeclaration().map(pd -> pd.getName().asString()).orElse("");

                cu.findAll(MethodDeclaration.class).forEach(m -> {
                    if (m.getRange().isPresent()) {
                        String className = m.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                                .map(c -> c.getNameAsString())
                                .orElse("UnknownClass");

                        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
                        String methodSignature = m.getDeclarationAsString(false, true, true);
                        int startLine = m.getRange().get().begin.line;
                        int endLine = m.getRange().get().end.line;

                        MethodCodeInfo methodInfo = new MethodCodeInfo(
                                fullClassName,
                                m.getNameAsString(),
                                methodSignature,
                                m.toString(),
                                relativePath,
                                startLine,
                                endLine
                        );

                        methods.add(methodInfo);
                    }
                });
            }
        } catch (Exception e) {
            LOG.warn("Errore di parsing per {}: {}", file, e.getMessage());
        }

        return methods;
    }

    private static List<Path> findJavaFiles(Path root, boolean includeTests) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> filterJavaFiles(p, includeTests))
                    .collect(Collectors.toList());
        }
    }

    private static boolean filterJavaFiles(Path path, boolean includeTests) {
        String pathStr = path.toString();

        // Escludi file generati e di build
        if (pathStr.contains("/target/") || pathStr.contains("/build/") ||
            pathStr.contains("/generated/") || pathStr.contains("/gen-src/")) {
            return false;
        }

        // Gestisci i file di test
        boolean isTestFile = pathStr.contains("/test/") ||
                            pathStr.endsWith("Test.java") ||
                            pathStr.endsWith("IT.java");

        return includeTests || !isTestFile;
    }

    private static Path findSourceFile(Path root, String classPath) {
        // Cerca nelle directory standard
        List<String> searchPaths = Arrays.asList(
                "src/main/java",
                "src/test/java",
                "main/java",
                "java"
        );

        for (String searchPath : searchPaths) {
            Path possiblePath = root.resolve(searchPath).resolve(classPath);
            if (Files.exists(possiblePath)) {
                return possiblePath;
            }
        }

        // Ricerca avanzata (trova tutti i file con lo stesso nome e scegli il più probabile)
        String fileName = classPath.substring(classPath.lastIndexOf('/') + 1);
        try (Stream<Path> walk = Files.walk(root)) {
            Optional<Path> found = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();

            return found.orElse(null);
        } catch (IOException e) {
            LOG.error("Errore nella ricerca avanzata del file", e);
            return null;
        }
    }

    private static String extractMethodCode(Path sourceFile, MethodInfo methodInfo) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);

        for (MethodDeclaration methodDecl : cu.findAll(MethodDeclaration.class)) {
            if (methodDecl.getNameAsString().equals(methodInfo.methodName)) {
                if (methodDecl.getParameters().size() == methodInfo.paramCount) {
                    return methodDecl.toString();
                }
            }
        }

        LOG.warn("Metodo non trovato nel file: {}", methodInfo.methodName);
        return null;
    }

    private static MethodInfo extractMethodInfo(CKMethodResult method) {
        String fullMethodName = method.getMethodName();
        int lastDot = fullMethodName.lastIndexOf(".");

        if (lastDot == -1) {
            throw new IllegalArgumentException("Nome metodo non valido: " + fullMethodName);
        }

        String methodName = fullMethodName.substring(lastDot + 1);
        String className = fullMethodName.substring(0, lastDot);
        System.out.println("Estrazione informazioni per classe: " + className);

        String classPath = className.replace('.', '/') + ".java";
        int paramCount = method.getParametersQty();

        return new MethodInfo(className + "." + methodName, className, methodName, classPath, paramCount);
    }

    /**
     * Informazioni su un metodo estratto con il suo codice
     */
    public static class MethodCodeInfo {
        private final String className;
        private final String methodName;
        private final String signature;
        private final String sourceCode;
        private final String filePath;
        private final int startLine;
        private final int endLine;

        public MethodCodeInfo(String className, String methodName, String signature,
                              String sourceCode, String filePath, int startLine, int endLine) {
            this.className = className;
            this.methodName = methodName;
            this.signature = signature;
            this.sourceCode = sourceCode;
            this.filePath = filePath;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public String getSignature() { return signature; }
        public String getSourceCode() { return sourceCode; }
        public String getFilePath() { return filePath; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public int getLinesOfCode() { return endLine - startLine + 1; }

        @Override
        public String toString() {
            return className + "." + methodName + " [linee: " + startLine + "-" + endLine + "]";
        }
    }

    private static class MethodInfo {
        final String fullName;
        final String className;
        final String methodName;
        final String classPath;
        final int paramCount;

        MethodInfo(String fullName, String className, String methodName, String classPath, int paramCount) {
            this.fullName = fullName;
            this.className = className;
            this.methodName = methodName;
            this.classPath = classPath;
            this.paramCount = paramCount;
        }
    }
}