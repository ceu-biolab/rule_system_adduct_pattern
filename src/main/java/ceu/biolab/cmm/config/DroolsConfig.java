package ceu.biolab.cmm.config;

import java.io.IOException;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
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

        DecisionTableConfiguration decisionTableConfig = KnowledgeBuilderFactory.newDecisionTableConfiguration();
        decisionTableConfig.setInputType(DecisionTableInputType.XLSX);

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] decisionTables = resolver.getResources("classpath*:rules/**/*.xlsx");

        for (Resource decisionTable : decisionTables) {
            org.kie.api.io.Resource kieResource = kieServices.getResources()
                    .newInputStreamResource(decisionTable.getInputStream());
            kieResource.setResourceType(ResourceType.DTABLE);
            kieResource.setConfiguration(decisionTableConfig);
            kieResource.setSourcePath("rules/" + decisionTable.getFilename());
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
