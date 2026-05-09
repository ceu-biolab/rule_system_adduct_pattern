package ceu.biolab.cmm.config;

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

    @Bean
    public KieContainer kieContainer() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        Resource xlsx = ks.getResources()
                .newClassPathResource("rules/AdductRules.drl.xlsx");
        xlsx.setResourceType(ResourceType.DTABLE);
        kfs.write(xlsx);

        KieBuilder builder = ks.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();
        if (results.hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            throw new IllegalStateException(results.toString());
        }

        return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }
}
