package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;

import java.nio.file.Path;

public interface CodeMetric {
    String getName();
    Object calculate(CKMethodResult method, CKClassResult cls, Path root);
}
