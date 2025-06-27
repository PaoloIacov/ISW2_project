package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HalsteadVolume implements CodeMetric {
    @Override
    public String getName() {
        return "HalsteadVolume";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        String sourceCode = SourceCodeExtractor.getMethodSource(method, root);

        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }

        try {
            // Parsiamo il codice usando JavaParser
            CompilationUnit cu = StaticJavaParser.parse("class Temp { " + sourceCode + " }");

            // Contatori per operandi e operatori
            Set<String> uniqueOperators = new HashSet<>();
            Set<String> uniqueOperands = new HashSet<>();

            // Utilizziamo AtomicInteger per permettere la modifica all'interno delle lambda
            AtomicInteger totalOperators = new AtomicInteger(0);
            AtomicInteger totalOperands = new AtomicInteger(0);

            // Visitiamo l'AST per contare operatori e operandi
            cu.walk(Node.TreeTraversal.PREORDER, node -> {
                if (node instanceof BinaryExpr expr) {
                    String operator = expr.getOperator().asString();
                    uniqueOperators.add(operator);
                    totalOperators.incrementAndGet();
                }
                // Operatori unari (es. ++, --, !, ecc.)
                else if (node instanceof UnaryExpr expr) {
                    String operator = expr.getOperator().asString();
                    uniqueOperators.add(operator);
                    totalOperators.incrementAndGet();
                }
                // Assegnazioni (=, +=, -=, ecc.)
                else if (node instanceof AssignExpr expr) {
                    String operator = expr.getOperator().asString();
                    uniqueOperators.add(operator);
                    totalOperators.incrementAndGet();
                }
                // Costrutti di controllo (if, for, while, ecc.)
                else if (node instanceof IfStmt || node instanceof ForStmt ||
                        node instanceof WhileStmt || node instanceof DoStmt ||
                        node instanceof SwitchStmt) {
                    String nodeType = node.getClass().getSimpleName().replace("Stmt", "");
                    uniqueOperators.add(nodeType);
                    totalOperators.incrementAndGet();
                }
                // Identificatori (variabili, nomi di metodi)
                else if (node instanceof NameExpr expr) {
                    String name = expr.getNameAsString();
                    uniqueOperands.add(name);
                    totalOperands.incrementAndGet();
                }
                // Chiamate di metodo
                else if (node instanceof MethodCallExpr expr) {
                    String methodName = expr.getNameAsString();
                    uniqueOperands.add(methodName);
                    totalOperands.incrementAndGet();

                    // Il nome del metodo è un operatore
                    uniqueOperators.add("call");
                    totalOperators.incrementAndGet();
                }
                // Letterali (numeri, stringhe, booleani)
                else if (node instanceof LiteralExpr expr) {
                    String literal = expr.toString();
                    uniqueOperands.add(literal);
                    totalOperands.incrementAndGet();
                }
            });

            // Calcolo del volume di Halstead
            // Formula: N * log2(n)
            // dove: N = operandi totali + operatori totali
            //       n = operandi unici + operatori unici
            int N = totalOperands.get() + totalOperators.get();
            int n = uniqueOperands.size() + uniqueOperators.size();

            if (n <= 1) return 0;

            // Usa logaritmo in base 2
            double volume = N * (Math.log(n) / Math.log(2));

            return (int) Math.round(volume);

        } catch (Exception e) {
            System.err.println("Errore durante il calcolo del volume di Halstead: " + e.getMessage());
            return 0;
        }
    }
}