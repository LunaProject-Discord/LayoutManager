import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Layout Manager Advanced: the parts of Layout Manager that need internal IDE API (the precise layout engine
// and the standard layout menu). Distributed on GitHub, not on JetBrains Marketplace; see README.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("advancedVersion").get()

base {
    archivesName = "LayoutManagerAdvanced"
}

kotlin {
    jvmToolchain(25)
}

// Java 21 bytecode, as for Layout Manager (see the root build script).
if (providers.gradleProperty("riderLocalPath").orNull == null) {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget = JvmTarget.JVM_21
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("riderLocalPath").orNull
        if (localPath != null) {
            local(localPath)
        } else {
            rider(providers.gradleProperty("platformVersion")) {
                useInstaller = false
            }
        }
        // Layout Manager, which this plugin extends.
        localPlugin(project(":"))
    }
}

intellijPlatform {
    // Names the plugin directory and the archive (LayoutManagerAdvanced-<version>.zip) instead of "advanced".
    projectName = "LayoutManagerAdvanced"
    buildSearchableOptions = false

    pluginConfiguration {
        name = providers.gradleProperty("advancedName")
        version = providers.gradleProperty("advancedVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    // Same signing key as Layout Manager, from environment variables only (see README).
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            val localPath = providers.gradleProperty("riderLocalPath").orNull
            if (localPath != null) {
                local(localPath)
            } else {
                create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersion")) {
                    useInstaller = false
                }
            }
            create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersionLatest")) {
                useInstaller = false
            }
        }
        // Layout Manager is not on JetBrains Marketplace yet, so the verifier cannot resolve it: its classes are
        // treated as external (the self-test runs both plugins together instead).
        externalPrefixes = listOf("jp.lunaproject.layoutmanager.actions", "jp.lunaproject.layoutmanager.engine")
        // This plugin exists to use internal API: report the usages, but do not fail on them.
        failureLevel = VerifyPluginTask.FailureLevel.ALL -
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES -
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES
    }
}
