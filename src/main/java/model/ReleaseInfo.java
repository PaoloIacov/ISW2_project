package model;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.ToString;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
@ToString
public class ReleaseInfo {
    private final String id;
    private final String name;
    private final LocalDate date;
}