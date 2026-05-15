package ceu.biolab.cmm.rulesReload.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ceu.biolab.cmm.config.KieContainerProvider;

@RestController
@RequestMapping("/api")
public class RulesReloadController {

    private final KieContainerProvider kieContainerProvider;

    public RulesReloadController(KieContainerProvider kieContainerProvider) {
        this.kieContainerProvider = kieContainerProvider;
    }

    /**
     * Rebuild the Drools container from the classpath rule files. If the build
     * succeeds the new container is swapped in atomically; if it fails the active
     * container is left untouched and the error is returned.
     *
     * @return 200 on success, 500 with the build error message on failure
     */
    @PostMapping("/reload-rules")
    public ResponseEntity<String> reloadRules() {
        
        Optional<String> error = kieContainerProvider.reload();
        return error
                .map(msg -> ResponseEntity.internalServerError().body(msg))
                .orElse(ResponseEntity.ok("Rules reloaded successfully"));
    }
}
