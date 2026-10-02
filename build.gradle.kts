import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

// Java 21 bytecode, so that the plugin also loads in the oldest supported Rider (2026.1, built against by
// default). A newer local Rider (riderLocalPath) inlines Java 25 bytecode, so such builds are for development only.
if (providers.gradleProperty("riderLocalPath").orNull == null) {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget = JvmTarget.JVM_21
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
    }
}

// `-PrunIdeOpen=<path>` opens a solution in the sandbox IDE started by `runIde`.
tasks.runIde {
    providers.gradleProperty("runIdeOpen").orNull?.let { args(it) }
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
    }
}

intellijPlatform {
    // Indexing settings for search starts a headless Rider, which crashes; the page is still found by its name.
    buildSearchableOptions = false

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    // Secrets come from environment variables only (see README): `signPlugin` and `publishPlugin`.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
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
            // The latest Rider of the supported range, besides the one built against (the oldest).
            create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersionLatest")) {
                useInstaller = false
            }
        }
        providers.gradleProperty("verifierMute").orNull?.let { freeArgs = listOf("-mute", it) }
        // The precise engine deliberately uses DesktopLayout (internal API) and falls back to public API
        // at runtime, so internal API usages are reported but do not fail the build.
        failureLevel = VerifyPluginTask.FailureLevel.ALL - VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES
    }
}

// region In-IDE self-test
//
// `./gradlew selfTest` runs src/selfTest inside a real Rider: phase 1 exercises both layout engines
// and the layout operations, then the IDE exits; phase 2 starts it again on the same sandbox and
// checks what must survive a restart. The test code is packaged as a separate test-only plugin that
// is installed into the self-test sandbox only, so it never reaches the published plugin.
// Add `-PriderLocalPath=...` to use an installed Rider instead of the downloaded one.

val selfTestSourceSet = sourceSets.create("selfTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

val selfTestPluginJar = tasks.register<Jar>("selfTestPluginJar") {
    description = "Packages the in-IDE self-test as a test-only plugin."
    archiveFileName = "LayoutManagerSelfTest.jar"
    destinationDirectory = layout.buildDirectory.dir("selftest/plugin")
    // Not selfTestSourceSet.output: the IntelliJ Platform plugin swaps every plugin.xml in processed
    // resources for the patched one of the main plugin.
    from(selfTestSourceSet.output.classesDirs)
    from("src/selfTest/resources")
    dependsOn(selfTestSourceSet.classesTaskName)
}

/** Creates a minimal .NET solution for the self-test IDE to open, once. */
abstract class CreateSampleSolution @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:OutputDirectory
    abstract val solutionDirectory: DirectoryProperty

    @TaskAction
    fun create() {
        val dir = solutionDirectory.get().asFile
        if (dir.resolve("SampleSolution.slnx").exists()) return
        fun dotnet(vararg args: String) = exec.exec {
            workingDir = dir
            commandLine("dotnet", *args)
        }
        dotnet("new", "sln", "-n", "SampleSolution", "--format", "slnx")
        dotnet("new", "console", "-n", "SampleApp")
        dotnet("sln", "add", "SampleApp/SampleApp.csproj")
    }
}

val selfTestSolution = layout.buildDirectory.dir("selftest/SampleSolution")
val selfTestResults = layout.buildDirectory.dir("selftest/results")

val createSelfTestSolution = tasks.register<CreateSampleSolution>("createSelfTestSolution") {
    solutionDirectory = selfTestSolution
}

val selfTestPhases = listOf("phase1", "phase2")
for (phase in selfTestPhases) {
    intellijPlatformTesting.runIde.register("selfTest${phase.replaceFirstChar { it.uppercase() }}") {
        // Both phases share one sandbox so that phase 2 sees what phase 1 saved.
        sandboxDirectory = layout.buildDirectory.dir("selftest/sandbox")
        prepareSandboxTask {
            sandboxSuffix = "-selftest"
            // Installed by copying rather than plugins { localPlugin() }, which needs the jar to exist
            // before any task has run.
            from(selfTestPluginJar) { into("LayoutManagerSelfTest/lib") }
        }
        task {
            group = "verification"
            dependsOn(createSelfTestSolution)
            args(selfTestSolution.get().file("SampleSolution.slnx").asFile.absolutePath)
            systemProperty("layoutmanager.selftest.phase", phase)
            systemProperty("layoutmanager.selftest.out", selfTestResults.get().asFile.absolutePath)
            if (phase == "phase1") {
                val results = selfTestResults
                doFirst { results.get().asFile.deleteRecursively() }
            } else {
                mustRunAfter("selfTestPhase1")
            }
        }
    }
}

tasks.register("selfTest") {
    group = "verification"
    description = "Runs the in-IDE self-test in a sandbox Rider (two IDE launches) and checks the results."
    dependsOn("selfTestPhase1", "selfTestPhase2")
    val results = selfTestResults
    doLast {
        val failures = mutableListOf<String>()
        for (phase in listOf("phase1", "phase2")) {
            val log = results.get().file("$phase.log").asFile
            if (!log.exists()) {
                failures += "$phase: no results (the IDE did not run the self-test)"
                continue
            }
            val lines = log.readLines()
            logger.lifecycle("== $phase")
            lines.forEach { logger.lifecycle(it) }
            failures += lines.filter { it.startsWith("FAIL") }.map { "$phase: $it" }
            if (lines.none { it.startsWith("DONE") }) failures += "$phase: did not finish"
        }
        if (failures.isNotEmpty()) throw GradleException("Self-test failed:\n" + failures.joinToString("\n"))
    }
}

// endregion
