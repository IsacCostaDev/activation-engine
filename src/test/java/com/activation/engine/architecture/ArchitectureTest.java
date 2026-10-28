package com.activation.engine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.activation.engine", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    public static final ArchRule domainMustNotDependOnApplication =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    public static final ArchRule controllersMustResideInWebPackage =
            classes().that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("..infrastructure.web..");

    @ArchTest
    public static final ArchRule repositoriesMustResideInCorrectPackages =
            classes().that().haveSimpleNameEndingWith("Repository")
                    .should().resideInAnyPackage("..domain.repository..", "..infrastructure.persistence..");
}
