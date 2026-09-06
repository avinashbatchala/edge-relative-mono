package com.edgerelative.application;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class DomainArchitectureTest {
    @Test
    void domainDependsOnlyOnTheJdkAndItself() {
        var domain = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.edgerelative.domain");

        classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.edgerelative.domain..")
                // The scaffold deliberately has no domain classes yet.
                .allowEmptyShould(true)
                .check(domain);
    }
}
