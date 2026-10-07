package com.edgerelative.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The advisory LLM and fundamental application contexts depend on their framework-free ports only.
 * They must never reach a concrete provider adapter (DD-06, ADR-007), so the provider stays
 * swappable and the key/transport never leaks into application code.
 */
class LlmFundamentalArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    @Test
    void llmContextDoesNotDependOnDeepSeekAdapter() {
        var llm = IMPORTER.importPackages("com.edgerelative.application.llm");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.llm.deepseek..")
                .allowEmptyShould(true)
                .check(llm);
    }

    @Test
    void fundamentalContextDoesNotDependOnVendorAdapter() {
        var fundamental = IMPORTER.importPackages("com.edgerelative.application.fundamental");

        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.edgerelative.fundamentals.nse..")
                .allowEmptyShould(true)
                .check(fundamental);
    }
}
