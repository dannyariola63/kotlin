/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING
import org.jetbrains.kotlin.gradle.plugin.MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.util.checkedReplace
import org.junit.jupiter.api.DisplayName
import kotlin.io.path.appendText
import kotlin.test.assertEquals

@DisplayName("Different Gradle classloaders warning")
@MppGradlePluginTests
class DifferentClassloadersIT : KGPBaseTest() {

    @DisplayName("Different classloaders message is not displayed")
    @GradleTest
    fun testDifferentClassloadersNotDisplayed(gradleVersion: GradleVersion) {
        project("differentClassloaders", gradleVersion) {
            build("publish", "-PmppProjectDependency=true") {
                assertOutputDoesNotContain(MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING)
                assertOutputDoesNotContain(MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING)
            }
        }
    }

    @DisplayName("Different classloader message is displayed on different plugin versions")
    @GradleTest
    fun testDetectingDifferentClassLoaders(gradleVersion: GradleVersion) {
        project("differentClassloaders", gradleVersion) {
            setupDifferentClassloadersProject()

            buildAndFail("publish", "-PmppProjectDependency=true") {
                assertOutputContains(MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING)
            }
        }
    }

    @DisplayName("KT-50598: Different classloaders message is only displayed on the first build")
    @GradleTest
    fun differentClassloadersOnlyFirstBuild(gradleVersion: GradleVersion) {
        project("differentClassloaders", gradleVersion) {
            setupDifferentClassloadersProject()

            build("-PmppProjectDependency=true") {
                assertOutputContains(MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING)

                val specificProjectsReported = Regex("$MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING((?:'.*'(?:, )?)+)")
                    .find(output)!!.groupValues[1].split(", ").map { it.removeSurrounding("'") }.toSet()

                assertEquals(setOf(":mpp-lib", ":jvm-app", ":js-app"), specificProjectsReported)
            }

            // Test the flag that turns off the warnings
            build("-PmppProjectDependency=true", "-Pkotlin.pluginLoadedInMultipleProjects.ignore=true") {
                assertOutputDoesNotContain(MULTIPLE_KOTLIN_PLUGINS_LOADED_WARNING)
                assertOutputDoesNotContain(MULTIPLE_KOTLIN_PLUGINS_SPECIFIC_PROJECTS_WARNING)
            }
        }
    }

    private fun TestProject.setupDifferentClassloadersProject() {
        // Specify the plugin versions in the subprojects with different plugin sets –
        // this will make Gradle use separate class loaders
        buildGradle.modify {
            it.checkedReplace("id \"org.jetbrains.kotlin.multiplatform\"", "//")
        }
        subProject("mpp-lib").buildGradle.modify {
            it.checkedReplace(
                "id \"org.jetbrains.kotlin.multiplatform\"",
                "id \"org.jetbrains.kotlin.multiplatform\" version \"${TestVersions.Kotlin.CURRENT}\""
            )
        }
        subProject("jvm-app").buildGradle.modify {
            it.checkedReplace(
                "id \"org.jetbrains.kotlin.jvm\"",
                "id \"org.jetbrains.kotlin.jvm\" version \"${TestVersions.Kotlin.CURRENT}\""
            )
        }
        subProject("js-app").buildGradle.modify {
            it.checkedReplace(
                "id \"kotlin2js\"",
                "id \"kotlin2js\" version \"${TestVersions.Kotlin.CURRENT}\""
            )
        }

        // Also include another project via a composite build:
        includeOtherProjectAsIncludedBuild("allopenPluginsDsl", "pluginsDsl")
        buildGradle.appendText(
            "\ntasks.create(\"publish\").dependsOn(gradle.includedBuild(\"allopenPluginsDsl\").task(\":assemble\"))"
        )
    }
}
