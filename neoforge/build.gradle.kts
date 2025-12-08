import dev.muon.dynamic_difficulty.gradle.Properties
import dev.muon.dynamic_difficulty.gradle.Versions
import org.apache.tools.ant.filters.LineContains
import org.gradle.jvm.tasks.Jar

plugins {
    id("conventions.loader")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin")
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
}

dependencies {
    runtimeOnly("curse.maven:configured-457570:5873783") { isTransitive = false }


    // Dev Env
    runtimeOnly("mezz.jei:jei-${Versions.MINECRAFT}-neoforge:${Versions.JEI}") { isTransitive = false }
    implementation("snownee.jade:Jade-NeoForge:${Versions.MINECRAFT}-${Versions.JADE}") { isTransitive = false }

    // Optional Compats:
    // Apotheosis
    implementation("dev.shadowsoffire:Placebo:${Versions.MINECRAFT}-${Versions.PLACEBO}") { isTransitive = false }
    implementation("dev.shadowsoffire:Apotheosis:${Versions.MINECRAFT}-${Versions.APOTHEOSIS}") { isTransitive = false }
    implementation("dev.shadowsoffire:ApothicAttributes:${Versions.MINECRAFT}-${Versions.APOTHIC_ATTRIBUTES}") { isTransitive = false }
    implementation("dev.shadowsoffire:ApothicSpawners:${Versions.MINECRAFT}-${Versions.APOTHIC_SPAWNERS}") { isTransitive = false }
    implementation("dev.shadowsoffire:ApothicEnchanting:${Versions.MINECRAFT}-${Versions.APOTHIC_ENCHANTING}") { isTransitive = false }

    // Puffish
    implementation("net.puffish:skillsmod:${Versions.PUFFISH_SKILLS}:neoforge") { isTransitive = false }
    runtimeOnly("net.puffish:attributesmod:${Versions.PUFFISH_ATTRIBUTES}:neoforge") { isTransitive = false }
    runtimeOnly("curse.maven:default-skill-trees-1074229:5852412") { isTransitive = false }

    // Reskillable Reimagined
    // https://www.curseforge.com/minecraft/mc-mods/reskillable-reimagined/files/
    implementation("curse.maven:reskillable-reimagined-1170464:7266884") { isTransitive = false }
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

        clientRequired = true
        serverRequired = true

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
