package com.edgerelative.application;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces the broker boundary: broker-neutral and domain code must not depend on the Groww adapter. */
class BrokerArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    @Test
    void brokerApiIsBrokerNeutral() {
        var brokerApi = IMPORTER.importPackages("com.edgerelative.broker.api");

        classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.edgerelative.broker.api..")
                .check(brokerApi);
    }

    @Test
    void domainDoesNotDependOnBrokerAdapters() {
        var domain = IMPORTER.importPackages("com.edgerelative.domain");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker..")
                .allowEmptyShould(true)
                .check(domain);
    }

    @Test
    void growwAdapterDoesNotDependOnApplication() {
        var groww = IMPORTER.importPackages("com.edgerelative.broker.groww");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.application..")
                .check(groww);
    }

    @Test
    void applicationDoesNotUseGrowwWireDtosDirectly() {
        var application = IMPORTER.importPackages("com.edgerelative.application");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker.groww.dto..")
                .check(application);
    }
}
