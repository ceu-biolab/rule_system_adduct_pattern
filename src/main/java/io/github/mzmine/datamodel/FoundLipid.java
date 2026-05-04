package io.github.mzmine.datamodel;

import java.util.ArrayList;
import java.util.List;

public class FoundLipid {
    private double score;
    private String descrCorrect;
    private String descrIncorrect;
    private int appliedPresence;
    private int appliedIntensity;
    private List<FoundAdduct> listAdducts;

    public FoundLipid() {
        this.listAdducts = new ArrayList<>();
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getDescrCorrect() {
        return descrCorrect;
    }

    public void setDescrCorrect(String descrCorrect) {
        this.descrCorrect = descrCorrect;
    }

    public String getDescrIncorrect() {
        return descrIncorrect;
    }

    public void setDescrIncorrect(String descrIncorrect) {
        this.descrIncorrect = descrIncorrect;
    }

    public int getAppliedPresence() {
        return appliedPresence;
    }

    public void setAppliedPresence(int appliedPresence) {
        this.appliedPresence = appliedPresence;
    }

    public int getAppliedIntensity() {
        return appliedIntensity;
    }

    public void setAppliedIntensity(int appliedIntensity) {
        this.appliedIntensity = appliedIntensity;
    }

    public List<FoundAdduct> getListAdducts() {
        return listAdducts;
    }

    public void setListAdducts(List<FoundAdduct> listAdducts) {
        this.listAdducts = listAdducts;
    }
}