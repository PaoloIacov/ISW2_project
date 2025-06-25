package model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import manager.CommitManager;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@AllArgsConstructor
public class ModifiedMethod {
    private final String filePath;
    private final String methodCode;
    private final CommitManager.ModificationType modificationType;

    public String getMethodName() {
        // Extract method name from the method code
        Pattern pattern = Pattern.compile("\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(methodCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }

    @Override
    public String toString() {
        return String.format("%s: %s in %s", modificationType, getMethodName(), filePath);
    }
}