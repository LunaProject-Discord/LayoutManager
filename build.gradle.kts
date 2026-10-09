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
        // JetBrains Marketplace rejects internal API usage, so any finding fails the build. What needs internal
        // API lives in Layout Manager Advanced (subproject "advanced").
        failureLevel = VerifyPluginTask.FailureLevel.ALL
    }
}

// region In-IDE self-test
//
// `./gradlew selfTest` runs src/selfTest inside a real Rider: phase 1 exercises the layout engines
// and the layout operations, then the IDE exits; phase 2 starts it again on the same sandbox and
// checks what must survive a restart. The test code is packaged as a separate test-only plugin that
// is installed into the self-test sandbox only, so it never reaches the published plugin.
// It runs twice: "basic" with Layout Manager alone (as installed from JetBrains Marketplace) and
// "advanced" with Layout Manager Advanced added (precise engine and standard menu).
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

val createSelfTestSolution = tasks.register<CreateSampleSolution>("createSelfTestSolution") {
    solutionDirectory = selfTestSolution
}

// Layout Manager Advanced is installed from its final plugin jar.
evaluationDependsOn(":advanced")
val advancedPluginJar = project(":advanced").tasks.named("composedJar")

val selfTestVariants = listOf("basic", "advanced")
val selfTestPhases = listOf("phase1", "phase2")
val selfTestTaskNames = mutableListOf<String>()
for (variant in selfTestVariants) {
    val results = layout.buildDirectory.dir("selftest/$variant/results")
    for (phase in selfTestPhases) {
        val name = "selfTest" + listOf(variant, phase).joinToString("") { it.replaceFirstChar(Char::uppercase) }
        val previous = selfTestTaskNames.lastOrNull()
        selfTestTaskNames += name
        intellijPlatformTesting.runIde.register(name) {
            // Both phases of a variant share one sandbox so that phase 2 sees what phase 1 saved.
            sandboxDirectory = layout.buildDirectory.dir("selftest/$variant/sandbox")
            prepareSandboxTask {
                sandboxSuffix = "-selftest"
                // Installed by copying rather than plugins { localPlugin() }, which needs the jar to exist
                // before any task has run.
                from(selfTestPluginJar) { into("LayoutManagerSelfTest/lib") }
                if (variant == "advanced") {
                    from(advancedPluginJar) { into("LayoutManagerAdvanced/lib") }
                }
            }
            task {
                group = "verification"
                dependsOn(createSelfTestSolution)
                args(selfTestSolution.get().file("SampleSolution.slnx").asFile.absolutePath)
                systemProperty("layoutmanager.selftest.phase", phase)
                systemProperty("layoutmanager.selftest.variant", variant)
                // A fresh sandbox would otherwise stop at the "Untrusted Solution" dialog.
                systemProperty("idea.trust.all.projects", "true")
                systemProperty("layoutmanager.selftest.out", results.get().asFile.absolutePath)
                if (phase == "phase1") {
                    doFirst { results.get().asFile.deleteRecursively() }
                }
                // One IDE at a time, each variant's phase 1 before its phase 2.
                previous?.let { mustRunAfter(it) }
            }
        }
    }
}

tasks.register("selfTest") {
    group = "verification"
    description = "Runs the in-IDE self-test in a sandbox Rider (four IDE launches) and checks the results."
    dependsOn(selfTestTaskNames)
    val resultsRoot = layout.buildDirectory.dir("selftest")
    doLast {
        val failures = mutableListOf<String>()
        for (variant in selfTestVariants) {
            for (phase in selfTestPhases) {
                val run = "$variant $phase"
                val log = resultsRoot.get().file("$variant/results/$phase.log").asFile
                if (!log.exists()) {
                    failures += "$run: no results (the IDE did not run the self-test)"
                    continue
                }
                val lines = log.readLines()
                logger.lifecycle("== $run")
                lines.forEach { logger.lifecycle(it) }
                failures += lines.filter { it.startsWith("FAIL") }.map { "$run: $it" }
                if (lines.none { it.startsWith("DONE") }) failures += "$run: did not finish"
            }
        }
        if (failures.isNotEmpty()) throw GradleException("Self-test failed:\n" + failures.joinToString("\n"))
    }
}

// endregion
