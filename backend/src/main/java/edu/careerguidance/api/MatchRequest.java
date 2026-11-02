package edu.careerguidance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class MatchRequest {
    @NotEmpty @Size(max = 100)
    private Map<@NotBlank @Size(max = 120) String, @NotNull @Min(1) @Max(5) Integer> studentSkills;
    @Size(min = 1, max = 3)
    private List<@NotBlank @Size(min = 1, max = 1) String> studentInterests = List.of();
    @Min(1) @Max(25)
    private Integer topN = 10;
    private boolean assessedOnly;
    @Size(max = 12)
    private Map<@NotBlank @Size(max = 80) String, Object> assessment = Map.of();

    public Map<String, Integer> getStudentSkills() { return studentSkills; }
    public void setStudentSkills(Map<String, Integer> studentSkills) { this.studentSkills = studentSkills; }
    public List<String> getStudentInterests() { return studentInterests; }
    public void setStudentInterests(List<String> studentInterests) { this.studentInterests = studentInterests; }
    public Integer getTopN() { return topN; }
    public void setTopN(Integer topN) { this.topN = topN; }
    public boolean isAssessedOnly() { return assessedOnly; }
    public void setAssessedOnly(boolean assessedOnly) { this.assessedOnly = assessedOnly; }
    public Map<String, Object> getAssessment() { return assessment; }
    public void setAssessment(Map<String, Object> assessment) { this.assessment = assessment == null ? Map.of() : assessment; }
}
