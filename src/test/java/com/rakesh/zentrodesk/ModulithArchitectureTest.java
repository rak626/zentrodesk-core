package com.rakesh.zentrodesk;


import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void verifyModules() {
        ApplicationModules.of(ZentrodeskApplication.class)
                .verify();
    }
}
