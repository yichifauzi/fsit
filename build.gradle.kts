import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val gitVersion: groovy.lang.Closure<String> by extra

plugins {
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin

    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.publish)
    alias(libs.plugins.git)
    alias(libs.plugins.machete)
}

val modVersion = gitVersion().let { if (it.first() == 'v') it.drop(1) else it }
val modrinthId = "${property("mod.modrinth_id")}"

val mcVersion = stonecutter.current.version
val mcTargetStart = "${property("minecraft.target.start")}"
val mcTargetEnd = "${property("minecraft.target.end")}"
val mcTarget = ">=$mcTargetStart-" + (" <=$mcTargetEnd".takeUnless { mcTargetEnd == "latest" } ?: "")

val fabricYarnBuild = "${property("fabric.yarn_build")}"
val fabricVersion = "${property("fabric.api")}+${stonecutter.current.project}"

val modmenuVersion = "${property("api.modmenu")}"
val yaclVersion = "${property("api.yacl")}"

val javaVersion = "${property("java.version")}".toInt(10)

version = "$modVersion+$mcVersion"
group = "dev.rvbsm"
base.archivesName = rootProject.name

loom {
    accessWidenerPath = rootProject.file("src/main/resources/fsit.accesswidener")

    splitEnvironmentSourceSets()
    mods.register(name) {
        sourceSet("main")
        sourceSet("client")
    }

    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "../../run"
    }

    mixin {
        useLegacyMixinAp = false
    }
}

machete {
    json.enabled = false
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases")
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.quiltmc.org/repository/release")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:$mcVersion+build.$fabricYarnBuild:v2")

    modImplementation(libs.bundles.fabric)
    setOf(
        "fabric-api-base",
        "fabric-command-api-v2",
        "fabric-key-binding-api-v1",
        "fabric-lifecycle-events-v1",
        "fabric-networking-api-v1"
    ).map { fabricApi.module(it, fabricVersion) }.forEach(::modImplementation)

    // fabric-resource-loader-v0: @Mixin target net.minecraft.class_60 was not found
    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    modApi("com.terraformersmc:modmenu:$modmenuVersion")
    modApi("dev.isxander:yet-another-config-lib:$yaclVersion-fabric") {
        exclude("net.fabricmc.fabric-api", "fabric-api")
    }

    implementation(libs.bundles.kaml)
    include(libs.bundles.kaml)
}

tasks {
    processResources {
        inputs.property("version", "$version")
        inputs.property("mcTarget", mcTarget)
        inputs.property("javaTarget", javaVersion)

        filesMatching("fabric.mod.json") {
            expand("version" to version, "mcTarget" to mcTarget, "javaTarget" to javaVersion)
        }
    }

    jar {
        from("LICENSE")
    }

    withType<JavaCompile> {
        options.release = javaVersion
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "$javaVersion"
    }

    if (stonecutter.current.isActive) {
        register("buildActive") {
            group = "project"
            dependsOn(build)
        }
    }
}

java {
    withSourcesJar()

    sourceCompatibility = enumValues<JavaVersion>()[javaVersion - 1]
    targetCompatibility = enumValues<JavaVersion>()[javaVersion - 1]
}

kotlin {
    jvmToolchain(javaVersion)
}

publishMods {
    file = tasks.remapJar.get().archiveFile
    additionalFiles.from(tasks.remapSourcesJar.get().archiveFile)
    changelog = providers.environmentVariable("CHANGELOG").orElse("No changelog provided.")
    type = when {
        "alpha" in modVersion -> ALPHA
        "beta" in modVersion -> BETA
        else -> STABLE
    }
    displayName = "[$mcVersion] v$modVersion"
    modLoaders.addAll("fabric", "quilt")

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = modrinthId

        minecraftVersionRange {
            start = mcTargetStart
            end = mcTargetEnd
        }

        requires("fabric-api", "fabric-language-kotlin")
        optional("modmenu", "yacl")

        tasks.getByName("publishModrinth") {
            dependsOn("optimizeOutputsOfRemapJar")
        }
    }
}
