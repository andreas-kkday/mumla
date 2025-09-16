/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// It's generally recommended to manage plugin versions in settings.gradle.kts
// and declare plugins in the plugins {} block at the top for .kts files.
// However, this buildscript block is converted as-is from the original.

plugins {
    id("com.android.library")
    // Consider adding kotlin-android plugin if you use Kotlin in this module's source code
    // id("org.jetbrains.kotlin.android")
}

// The allprojects block here will configure this project and its subprojects (if any).
// If this configuration is intended for all projects in the build,
// it should be moved to the root project's build.gradle.kts file.
allprojects {
    tasks.withType<JavaCompile> {
        // TODO include deprecations at some point, but currently they are *many*
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-deprecation", "-Xlint:-dep-ann"))
    }
}

android {
    namespace = "se.lublin.mumla"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 14
        targetSdk = 34
        multiDexEnabled = true

        resourceConfigurations.addAll(
            listOf(
                "en", "ar", "ca", "cs", "de", "el", "es", "fi", "fr", "hu", "in", "it",
                "ja", "nb-rNO", "nl", "pl", "pt", "pt-rBR", "ru", "sv", "ta", "th",
                "tr", "uk", "zh-rCN"
            )
        )

        // This sets an extra property on the defaultConfig object.
        // If the intent is to change the archive base name for the project's outputs,
        // consider using project.base.archivesName.set("mumla") at the project level.
        (this as ExtensionAware).extra.set("archivesBaseName", "mumla")
        buildConfigField("long", "TIMESTAMP", "${System.currentTimeMillis()}L")

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    flavorDimensions.add("release")
    productFlavors {
        create("betaDebug") {
            dimension = "release"
        }
        create("betaRelease") {
            dimension = "release"
        }
    }

    buildTypes {
        getByName("release") {
        }
        getByName("debug") {
        }
    }

    // betas may be released every minute
    // TODO? dynamic stuff, have to rebuild a lot
    // The libraryVariants.all block is deprecated in newer AGP versions.
    // Use androidComponents.onVariants or androidComponents.beforeVariants instead if this logic is needed.
    // libraryVariants.all { // Changed from applicationVariants as this is a library module
    //    val variant = this // In KTS, 'variant' is not introduced, 'this' is the variant
    //    if (variant.flavorName == "beta") { // Note: flavorName for betaDebug/betaRelease will include "Debug"/"Release"
    //        // A more robust check might be: if (variant.name.contains("beta", ignoreCase = true))
    //        variant.outputs.forEach { output ->
    //            // output.versionCodeOverride is deprecated. Access via mainOutput or similar in new Variant API
    //            // For example, if output is an ApkVariantOutput:
    //            // (output as? com.android.build.gradle.api.ApkVariantOutput)?.versionCodeOverride = (System.currentTimeMillis() / 1000 / 60).toInt()
    //        }
    //    }
    // }
}

dependencies {
    implementation(project(":mumble:humla")) // Ensure :mumble:humla is a valid Gradle project path
    implementation(libs.spongycastle.prov)
    implementation(libs.spongycastle.pkix)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerView)
    implementation(libs.jsoup)
    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.exifinterface)
    implementation(libs.netcipher)
}
