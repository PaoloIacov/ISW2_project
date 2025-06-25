package model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomMetric {
    private String methodName;
    private String releaseId;
    private int loc;
    private int fanIn;
    private int fanOut;
    private int nSmells;
    private double halsteadVolume;
    private int statementCount;
    private int methodHistories;
    private int churn;
    private int locAdded;
    private int age;
    private int nAuthors;
    private double duplicationPercent;

    public String[] toStringArray() {
        return new String[] {
                methodName,
                releaseId,
                String.valueOf(loc),
                String.valueOf(fanIn),
                String.valueOf(fanOut),
                String.valueOf(nSmells),
                String.valueOf(halsteadVolume),
                String.valueOf(statementCount),
                String.valueOf(methodHistories),
                String.valueOf(churn),
                String.valueOf(locAdded),
                String.valueOf(age),
                String.valueOf(nAuthors),
                String.valueOf(duplicationPercent)
        };
    }
}