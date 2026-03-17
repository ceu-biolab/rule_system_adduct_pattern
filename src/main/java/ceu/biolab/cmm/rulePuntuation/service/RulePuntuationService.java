package ceu.biolab.cmm.rulePuntuation.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ceu.biolab.cmm.shared.domain.msFeature.AnnotatedFeature;

@Service
public class RulePuntuationService {

    public List<AnnotatedFeature> score(List<AnnotatedFeature> features) {
        
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Rule scoring is not implemented.");
    }
}
