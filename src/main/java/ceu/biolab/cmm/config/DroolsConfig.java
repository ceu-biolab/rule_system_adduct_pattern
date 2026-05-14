package ceu.biolab.cmm.config;

import java.util.List;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Results;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolsConfig {

    private static final List<String> RULE_FILES = List.of(
            "rules/positive_presence.xlsx",
            "rules/positive_intensityGT.xlsx",
            "rules/positive_intensityLT.xlsx",
            "rules/negative_presence.xlsx",
            "rules/negative_intensityGT.xlsx",
            "rules/negative_intensityLT.xlsx"
    );

    @Bean
    public KieContainer kieContainer() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        for (String path : RULE_FILES) {
            Resource xlsx = ks.getResources().newClassPathResource(path);
            xlsx.setResourceType(ResourceType.DTABLE);
            kfs.write(xlsx);
        }

        KieBuilder builder = ks.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();
        if (results.hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            throw new IllegalStateException(results.toString());
        }

        return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }
}
