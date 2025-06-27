package utils;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKMethodResult;

import java.nio.file.Files;
import java.nio.file.Path;

public class SourceCodeExtractor {
    public static String getMethodSource(CKMethodResult method, Path root) {
        try {
            // Estrai le informazioni necessarie dal nome completo del metodo
            String fullMethodName = method.getQualifiedMethodName(); // Ottiene qualcosa come "package.Class.method"

            // Estrai nome della classe e del metodo
            int lastDot = fullMethodName.lastIndexOf(".");
            String methodName = fullMethodName.substring(lastDot + 1);
            String className = fullMethodName.substring(0, lastDot);

            // Costruisci il percorso del file sorgente
            String classPath = className.replace('.', '/') + ".java";
            Path sourceFilePath = root.resolve("src/main/java").resolve(classPath);

            // Se non esiste, prova con altre directory standard
            if (!Files.exists(sourceFilePath)) {
                sourceFilePath = root.resolve("src/test/java").resolve(classPath);
            }

            if (!Files.exists(sourceFilePath)) {
                return null;
            }

            // Parsifica il file
            CompilationUnit cu = StaticJavaParser.parse(sourceFilePath);

            // Cerca il metodo specifico
            for (MethodDeclaration methodDecl : cu.findAll(MethodDeclaration.class)) {
                if (methodDecl.getNameAsString().equals(method.getMethodName())) {
                    if (methodDecl.getParameters().size() == method.getParametersQty()) {
                        return methodDecl.toString();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}