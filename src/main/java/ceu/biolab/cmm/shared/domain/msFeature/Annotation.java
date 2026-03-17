package ceu.biolab.cmm.shared.domain.msFeature;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Annotation {
    private Double neutralMass;
    private List<Score> scores;

    public Annotation() {
        this.scores = new ArrayList<>();
    }

    public Annotation(Double neutralMass) {
        this.neutralMass = neutralMass;
        this.scores = new ArrayList<>();
    }

    public void addScore(Score score) {
        this.scores.add(score);
    }
}
