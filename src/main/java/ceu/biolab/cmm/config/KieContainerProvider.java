package ceu.biolab.cmm.config;

import java.util.List;
import java.util.Optional;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KieContainerProvider {

    private static final Logger logger = LoggerFactory.getLogger(KieContainerProvider.class);

    private static final List<String> RULE_FILES = List.of(
            "rules/positive_presence.xlsx",
            "rules/positive_intensityGT.xlsx",
            "rules/positive_intensityLT.xlsx",
            "rules/negative_presence.xlsx",
            "rules/negative_intensityGT.xlsx",
            "rules/negative_intensityLT.xlsx"
    );

    private volatile KieContainer container;

    public KieContainerProvider() {
        this.container = build();
    }

    public KieContainer getContainer() {
        return container;
    }

    /**
     * Rebuilds the KieContainer from the classpath rule files and, only if the
     * build succeeds, swaps the active container. Concurrent sessions that started
     * before the swap continue against the old container until they dispose.
     *
     * @return empty if the reload succeeded, or the error message if it failed
     */
    public Optional<String> reload() {
        logger.info("Reloading Drools rules from classpath: {}", RULE_FILES);
        try {
            KieContainer newContainer = build();
            this.container = newContainer;
            logger.info("Rules reloaded successfully — previous container replaced");
            return Optional.empty();
        } catch (IllegalStateException e) {
            logger.error("Rules reload failed — previous container kept active. Error: {}", e.getMessage());
            return Optional.of(e.getMessage());
        }
    }

    private static KieContainer build() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        for (String path : RULE_FILES) {
            Resource xlsx = ks.getResources().newClassPathResource(path);
            xlsx.setResourceType(ResourceType.DTABLE);
            kfs.write(xlsx);
        }

        KieBuilder builder = ks.newKieBuilder(kfs).buildAll();
        Results results = builder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(results.toString());
        }

        return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }
}
