import dev.muon.dynamic_difficulty.gradle.Properties
import dev.muon.dynamic_difficulty.gradle.Versions
import me.modmuss50.mpp.PublishModTask
import org.apache.tools.ant.filters.LineContains
import org.gradle.jvm.tasks.Jar
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

plugins {
    id("conventions.loader")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin")
    id("dev.mixinmcp.decompile")
}

tasks {
    withType<Javadoc> {
        enabled = false
    }
}

neoForge {
    version = Versions.NEOFORGE
    parchment {
        minecraftVersion = Versions.PARCHMENT_MINECRAFT
        mappingsVersion = Versions.PARCHMENT
    }
    addModdingDependenciesTo(sourceSets["test"])

    val at = project(":common").file("src/main/resources/${Properties.MOD_ID}.cfg")
    if (at.exists())
        setAccessTransformers(at)
    validateAccessTransformers = true

    runs {
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            systemProperty("neoforge.enabledGameTestNamespaces", Properties.MOD_ID)
        }
        create("client") {
            client()
            gameDirectory.set(file("runs/client"))
            sourceSet = sourceSets["test"]
            jvmArguments.set(setOf("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true"))
        }
        create("server") {
            server()
            gameDirectory.set(file("runs/server"))
            programArgument("--nogui")
            sourceSet = sourceSets["test"]
            jvmArguments.set(setOf("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true"))
        }
    }

    mods {
        register(Properties.MOD_ID) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["test"])
        }
    }
}

tasks {
    named<ProcessResources>("processResources").configure {
        filesMatching("*.mixins.json") {
            filter<LineContains>("negate" to true, "contains" to setOf("refmap"))
        }
    }
}

repositories {
    maven {
        url = uri("https://maven.shadowsoffire.dev/releases")
        content {
            includeGroup("dev.shadowsoffire")
            includeGroup("snownee.jade")
        }
    }
    maven {
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        url = uri("https://code.redspace.io/releases")
        content {
            includeGroup("io.redspace")
        }
    }
    maven {
        url = uri("https://code.redspace.io/snapshots")
        content {
            includeGroup("io.redspace")
        }
    }
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.blamejared.com/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.fabricmc.net")
    maven("https://maven.shedaniel.me/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.kosmx.dev/")
    maven("https://maven.theillusivec4.top/")
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
    maven("https://maven.puffish.net")
    maven("https://maven.fzzyhmstrs.me/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

val localRuntime: Configuration by configurations.creating

configurations {
    named("runtimeClasspath") {
        extendsFrom(localRuntime)
    }
    named("testRuntimeClasspath") {
        extendsFrom(localRuntime)
    }
}

dependencies {
    implementation("me.fzzyhmstrs:fzzy_config:${Versions.FZZY_CONFIG}+neoforge")

    // Dev Env
    localRuntime("mezz.jei:jei-${Versions.MINECRAFT}-neoforge:${Versions.JEI}")
    localRuntime("curse.maven:configured-457570:5873783")

    // Optional Compats:
    // Jade
    compileOnly("snownee.jade:Jade-NeoForge:${Versions.MINECRAFT}-${Versions.JADE}")
    localRuntime("snownee.jade:Jade-NeoForge:${Versions.MINECRAFT}-${Versions.JADE}")

    // Apotheosis
    compileOnly("dev.shadowsoffire:Placebo:${Versions.MINECRAFT}-${Versions.PLACEBO}")
    localRuntime("dev.shadowsoffire:Placebo:${Versions.MINECRAFT}-${Versions.PLACEBO}")
    compileOnly("dev.shadowsoffire:Apotheosis:${Versions.MINECRAFT}-${Versions.APOTHEOSIS}")
    localRuntime("dev.shadowsoffire:Apotheosis:${Versions.MINECRAFT}-${Versions.APOTHEOSIS}")
    compileOnly("dev.shadowsoffire:ApothicAttributes:${Versions.MINECRAFT}-${Versions.APOTHIC_ATTRIBUTES}")
    localRuntime("dev.shadowsoffire:ApothicAttributes:${Versions.MINECRAFT}-${Versions.APOTHIC_ATTRIBUTES}")
    compileOnly("dev.shadowsoffire:ApothicSpawners:${Versions.MINECRAFT}-${Versions.APOTHIC_SPAWNERS}")
    localRuntime("dev.shadowsoffire:ApothicSpawners:${Versions.MINECRAFT}-${Versions.APOTHIC_SPAWNERS}")
    compileOnly("dev.shadowsoffire:ApothicEnchanting:${Versions.MINECRAFT}-${Versions.APOTHIC_ENCHANTING}")
    localRuntime("dev.shadowsoffire:ApothicEnchanting:${Versions.MINECRAFT}-${Versions.APOTHIC_ENCHANTING}")

    // Puffish
    compileOnly("net.puffish:skillsmod:${Versions.PUFFISH_SKILLS}:neoforge")
    localRuntime("net.puffish:skillsmod:${Versions.PUFFISH_SKILLS}:neoforge")
    localRuntime("net.puffish:attributesmod:${Versions.PUFFISH_ATTRIBUTES}:neoforge")
    localRuntime("curse.maven:default-skill-trees-1074229:5852412")

    // Reskillable Reimagined
    // https://www.curseforge.com/minecraft/mc-mods/reskillable-reimagined/files/
    compileOnly("curse.maven:reskillable-reimagined-1170464:7564069")
    localRuntime("curse.maven:reskillable-reimagined-1170464:7564069")

    // Dungeon Difficulty
    compileOnly("curse.maven:dungeon-difficulty-645559:7279795")
    // localRuntime("curse.maven:dungeon-difficulty-645559:7279795") Requires FFAPI. Rather not rn

    // Health Bars
    compileOnly("maven.modrinth:new-health-bars:${Versions.HEALTH_BARS}-${Versions.MINECRAFT}-NeoForge")
}

publishMods {
    file.set(tasks.named<Jar>("jar").get().archiveFile)
    modLoaders.add("neoforge")
    changelog = rootProject.file("CHANGELOG.md").readText()
    version = "${Versions.MOD}+${Versions.MINECRAFT}"
    type = STABLE

    curseforge {
        projectId = Properties.CURSEFORGE_PROJECT_ID
        accessToken = providers.environmentVariable("CF_TOKEN")

        minecraftVersions.add(Versions.MINECRAFT)
        javaVersions.add(JavaVersion.VERSION_21)

        client = true
        server = true

        requires("fzzy-config")
    }

//    modrinth {
//        projectId = Properties.MODRINTH_PROJECT_ID
//        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
//
//        minecraftVersions.add(Versions.MINECRAFT)
//
//    }

    /*
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        parent(project(":common").tasks.named("publishGithub"))
    }
     */
}

tasks.withType<PublishModTask>().configureEach {
    mustRunAfter(rootProject.subprojects.map { "${it.path}:publishAllPublicationsToMuonRepository" })
}

tasks.register("sendToModpack") {
    group = "publishing"
    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)
    val builtJar = jarTask.flatMap { it.archiveFile }
    val modsDir = Path.of(System.getProperty("user.home"), "curseforge", "minecraft", "Instances", "Raven's High Fantasy", "mods")
    val deployedJar = modsDir.resolve(jarTask.get().archiveFileName.get().replace(project.version.toString(), "dev"))
    val releasedJarPrefix = "${base.archivesName.get()}-"
    doLast {
        // The pack ships a released copy of this mod; loaders don't pick a winner between duplicates.
        Files.list(modsDir).use { files ->
            files.filter { it != deployedJar && it.fileName.toString().let { name -> name.startsWith(releasedJarPrefix) && name.endsWith(".jar") } }
                .forEach {
                    Files.move(it, it.resolveSibling("${it.fileName}.disabled"), StandardCopyOption.REPLACE_EXISTING)
                    println("Disabled ${it.fileName} in the modpack")
                }
        }
        // Writing into the deployed jar corrupts a running pack's open handle; replacing the file leaves the pack on the old one.
        val staged = deployedJar.resolveSibling("${deployedJar.fileName}.staged")
        Files.copy(builtJar.get().asFile.toPath(), staged, StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.deleteIfExists(deployedJar)
        } catch (e: FileSystemException) {
            Files.delete(staged)
            throw GradleException("${deployedJar.fileName} is held open by a running pack; close it and rerun", e)
        }
        Files.move(staged, deployedJar)
        println("Mod JAR sent to modpack folder: ${deployedJar.fileName}")
    }
}
