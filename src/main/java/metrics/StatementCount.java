package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.nio.file.Path;

public class StatementCount implements CodeMetric {
    @Override
    public String getName() {
        return "StatementCount";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        String sourceCode = SourceCodeExtractor.getMethodSource(method, root);
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }

        int statements = 0;
        boolean inString = false;
        boolean inComment = false;

        for (int i = 0; i < sourceCode.length(); i++) {
            char c = sourceCode.charAt(i);

            // Gestisci stringhe
            if (c == '"' && (i == 0 || sourceCode.charAt(i-1) != '\\')) {
                inString = !inString;
                continue;
            }

            // Se siamo in una stringa, salta
            if (inString) continue;

            // Gestisci commenti
            if (!inComment && i < sourceCode.length() - 1) {
                if (c == '/' && sourceCode.charAt(i+1) == '*') {
                    inComment = true;
                    i++;
                    continue;
                } else if (c == '/' && sourceCode.charAt(i+1) == '/') {
                    // Salta fino a fine riga
                    while (i < sourceCode.length() && sourceCode.charAt(i) != '\n') i++;
                    continue;
                }
            }

            if (inComment && i < sourceCode.length() - 1) {
                if (c == '*' && sourceCode.charAt(i+1) == '/') {
                    inComment = false;
                    i++;
                    continue;
                }
            }

            if (inComment) continue;

            // Conta gli statement (terminati da punto e virgola)
            if (c == ';') {
                statements++;
            }
        }

        return statements > 0 ? statements : 1; // Almeno uno statement
    }
}