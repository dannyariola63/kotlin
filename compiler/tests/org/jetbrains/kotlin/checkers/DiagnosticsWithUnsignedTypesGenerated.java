/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.checkers;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler/testData/diagnostics/testsWithUnsignedTypes")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class DiagnosticsWithUnsignedTypesGenerated extends AbstractDiagnosticsWithUnsignedTypes {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testAllFilesPresentInTestsWithUnsignedTypes() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("compiler/testData/diagnostics/testsWithUnsignedTypes"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("forbiddenEqualsOnUnsignedTypes.kt")
    public void testForbiddenEqualsOnUnsignedTypes() throws Exception {
        runTest("compiler/testData/diagnostics/testsWithUnsignedTypes/forbiddenEqualsOnUnsignedTypes.kt");
    }

    @TestMetadata("overloadResolutionOfBasicOperations.kt")
    public void testOverloadResolutionOfBasicOperations() throws Exception {
        runTest("compiler/testData/diagnostics/testsWithUnsignedTypes/overloadResolutionOfBasicOperations.kt");
    }

    @TestMetadata("unsignedLiteralsInsideConstVals.kt")
    public void testUnsignedLiteralsInsideConstVals() throws Exception {
        runTest("compiler/testData/diagnostics/testsWithUnsignedTypes/unsignedLiteralsInsideConstVals.kt");
    }

    @TestMetadata("unsignedLiteralsOverflowSignedBorder.kt")
    public void testUnsignedLiteralsOverflowSignedBorder() throws Exception {
        runTest("compiler/testData/diagnostics/testsWithUnsignedTypes/unsignedLiteralsOverflowSignedBorder.kt");
    }

    @TestMetadata("unsignedLiteralsTypeCheck.kt")
    public void testUnsignedLiteralsTypeCheck() throws Exception {
        runTest("compiler/testData/diagnostics/testsWithUnsignedTypes/unsignedLiteralsTypeCheck.kt");
    }
}
