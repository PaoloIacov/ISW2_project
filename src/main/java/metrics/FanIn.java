package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FanIn implements CodeMetric {
    @Override
    public String getName() {
        return "FanIn";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Nome del metodo che stiamo analizzando
        String methodName = method.getMethodName();

        // Estrai il nome completo della classe
        String fullMethodName = method.getQualifiedMethodName();
        int lastDot = fullMethodName.lastIndexOf(".");
        String className = fullMethodName.substring(0, lastDot);

        // Numero di parametri del metodo corrente
        int parametersQty = method.getParametersQty();

        // Conta il numero di chiamate ricevute da questo metodo
        int fanIn = 0;

        try {
            // Cerca in tutti i file Java del progetto
            List<Path> javaFiles = findAllJavaFiles(root);

            for (Path filePath : javaFiles) {
                // Analizza ogni file Java per cercare chiamate al nostro metodo
                try {
                    String fileContent = Files.readString(filePath);
                    CompilationUnit cu = StaticJavaParser.parse(fileContent);

                    // Cerca le chiamate al metodo
                    List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);

                    // Verifica ogni chiamata
                    for (MethodCallExpr call : calls) {
                        // Controlla se il nome del metodo corrisponde
                        if (call.getNameAsString().equals(methodName)) {
                            // In un'implementazione completa dovresti verificare anche:
                            // - La classe di appartenenza (attraverso import e pacchetti)
                            // - I tipi dei parametri (più complesso)
                            // Per semplicità, consideriamo solo il nome del metodo e il numero di parametri
                            if (call.getArguments().size() == parametersQty) {
                                fanIn++;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignora file non parsificabili
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

        return fanIn;
    }

    private List<Path> findAllJavaFiles(Path root) {
        List<Path> javaFiles = new ArrayList<>();
        try {
            // Cerca file .java nella directory src
            Path srcDir = root.resolve("src");
            if (Files.exists(srcDir)) {
                javaFiles = Files.walk(srcDir)
                        .filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return javaFiles;
    }
}