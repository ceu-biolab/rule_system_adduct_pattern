package ceu.biolab.cmm.shared.domain.msFeature;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AnnotationsByAdduct {
    private String adduct;
    private List<Annotation> annotations;

    public AnnotationsByAdduct() {
        this.annotations = new ArrayList<>();
    }

    public AnnotationsByAdduct(String adduct) {
        this.adduct = adduct;
        this.annotations = new ArrayList<>();
    }

    public AnnotationsByAdduct(String adduct, List<Annotation> annotations) {
        this.adduct = adduct;
        this.annotations = annotations == null ? new ArrayList<>() : new ArrayList<>(annotations);
    }

    public void addAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
    }
}
