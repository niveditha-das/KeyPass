package com.keypass.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

    private final JavaClasses serverClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.keypass.server");

    private final JavaClasses commonClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.keypass.common");

    @Test
    void commonModuleHasNoSpringDependencies() {
        // keypass-common is shared by the server and the plain-Java car simulator, so it must
        // stay framework-free — a Spring dependency here would force the simulator onto Spring too.
        noClasses().that().resideInAPackage("com.keypass.common..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(commonClasses);
    }

    @Test
    void commonModuleHasNoJakartaPersistenceDependencies() {
        noClasses().that().resideInAPackage("com.keypass.common..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .check(commonClasses);
    }

    @Test
    void repositoriesAreInterfaces() {
        classes().that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("com.keypass.server..")
                .should().beInterfaces()
                .check(serverClasses);
    }

    @Test
    void restControllersAreNamedController() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .check(serverClasses);
    }

    @Test
    void entitiesDoNotDependOnRepositories() {
        noClasses().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(serverClasses);
    }
}
