package ceu.biolab.cmm.config;

import java.io.IOException;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer kieContainer() throws IOException {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] drlRules = resolver.getResources("classpath*:rules/**/*.drl");

        for (Resource rule : drlRules) {
            org.kie.api.io.Resource kieResource = kieServices.getResources()
                .newInputStreamResource(rule.getInputStream());
            kieResource.setResourceType(ResourceType.DRL);
            kieResource.setSourcePath("rules/" + rule.getFilename());
            kieFileSystem.write(kieResource);
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem).buildAll();
        Results results = kieBuilder.getResults();
        if (results.hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            throw new IllegalStateException(results.toString());
        }

        return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    }
}
