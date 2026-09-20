package com.edgerelative.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.edgerelative.application.history.HistoryQueryController;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Enforces the canonical-history read boundary: the read/query side and future research consumers
 * must never depend on a broker port or the Groww adapter (DD-05 §94/§97).
 */
class HistoryArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    @Test
    void queryPackageDoesNotDependOnBrokers() {
        var query = IMPORTER.importPackages("com.edgerelative.application.history.query");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker.api.port..", "com.edgerelative.broker.groww..")
                .allowEmptyShould(true)
                .check(query);
    }

    @Test
    void queryControllerDoesNotDependOnBrokers() {
        var controller = IMPORTER.importClasses(HistoryQueryController.class);

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker..")
                .check(controller);
    }

    @Test
    void onlyIngestionMayUseTheHistoricalBrokerPort() {
        var application = IMPORTER.importPackages("com.edgerelative.application");

        // Ingestion (history) and the explicit live broker surface (application.broker) may use it;
        // everything else — features/backtests/research — must not.
        noClasses().that()
                .resideInAPackage("com.edgerelative.application..")
                .and()
                .resideOutsideOfPackages(
                        "com.edgerelative.application.history..", "com.edgerelative.application.broker..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.edgerelative.broker.api.port.HistoricalDataBroker")
                .check(application);
    }

    @Test
    void futureReadConsumersCannotDependOnBrokers() {
        // Feature/backtest/replay/research packages do not exist yet; the rule guards their arrival.
        var consumers = IMPORTER.importPackages(
                "com.edgerelative.application.feature",
                "com.edgerelative.application.features",
                "com.edgerelative.application.backtest",
                "com.edgerelative.application.replay",
                "com.edgerelative.application.research");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.broker..")
                .allowEmptyShould(true)
                .check(consumers);
    }

    @Test
    void canonicalReadsLiveBehindTheReaderInterface() {
        var query = IMPORTER.importPackages("com.edgerelative.application.history.query");

        classes().that()
                .haveSimpleNameEndingWith("QueryService")
                .should()
                .beAssignableTo(HistoricalDataReader.class)
                .allowEmptyShould(true)
                .check(query);
    }
}
