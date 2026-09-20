package com.edgerelative.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The feature engine depends on canonical internals only. It must never reach a broker adapter, the
 * broker ports, execution/orders, or the frontend (DD-05 §2, DD-04 boundary rules).
 */
class FeatureArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    @Test
    void featurePackageDoesNotDependOnBrokers() {
        var feature = IMPORTER.importPackages("com.edgerelative.application.feature");

        // The shared API error envelope lives in application.broker.api; broker adapters and the
        // Groww surface must never be reachable from feature code.
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.edgerelative.broker..",
                        "com.edgerelative.application.broker.Groww..")
                .allowEmptyShould(true)
                .check(feature);
    }

    @Test
    void featurePackageDoesNotDependOnExecutionOrFrontend() {
        var feature = IMPORTER.importPackages("com.edgerelative.application.feature");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.edgerelative.application.execution..",
                        "com.edgerelative.application.order..",
                        "com.edgerelative.application.orders..",
                        "com.edgerelative.frontend..")
                .allowEmptyShould(true)
                .check(feature);
    }

    @Test
    void featureApiExposesNoBrokerTypes() {
        var api = IMPORTER.importPackages("com.edgerelative.application.feature.api");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker..")
                .allowEmptyShould(true)
                .check(api);
    }
}
