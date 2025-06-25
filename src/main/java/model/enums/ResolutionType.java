package model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ResolutionType {

    SOLVED("solved"),
    FIXED("fixed"),
    WON_T_FIX("won't fix");

    @Getter
    private final String resolution;

    public static ResolutionType fromResolution(String resolution) {
        if (resolution == null) {
            return null;
        }
        for (ResolutionType type : ResolutionType.values()) {
            if (type.getResolution().equalsIgnoreCase(resolution)) {
                return type;
            }
        }
        return null;
    }
}