package ceu.biolab.cmm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolsConfig {

    @Bean
    public KieContainerProvider kieContainerProvider() {
        return new KieContainerProvider();
    }
}
