import dev.muon.dynamic_difficulty.gradle.Properties
import dev.muon.dynamic_difficulty.gradle.Versions
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.jvm.tasks.Jar

plugins {
    id("conventions.loader")
    id("fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
}

repositories {
    maven("https://maven.blamejared.com/")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.fabricmc.net")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/")
    maven("https://jitpack.io/")
    maven("https://maven.ladysnake.org/releases")
    maven("https://maven.ladysnake.org/snapshots")
    maven("https://maven.jamieswhiteshirt.com/libs-release")
    maven("https://maven.parchmentmc.org")
    maven("https://cursemaven.com")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.bawnorton.com/releases")
    maven("https://maven.kosmx.dev/")
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.ftb.dev/releases")
    maven("https://maven.bai.lol" )
    maven("https://maven.kosmx.dev/")
    maven("https://nexus.resourcefulbees.com/repository/maven-public/")
    maven("https://jm.gserv.me/repository/maven-public/" )
    maven("https://masa.dy.fi/maven" )
    maven("https://maven.quiltmc.org/repository/release" )
    maven("https://maven.uuid.gg/releases" )
    maven("https://maven.puffish.net")
}

dependencies {
    minecraft("com.mojang:minecraft:${Versions.MINECRAFT}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${Versions.FABRIC_LOADER}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${Versions.FABRIC_API}")

    modImplementation("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.4")
    include("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.4")
    annotationProcessor(("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.4"))?.let {
        include(it)?.let {
            modImplementation(
                it
            )
        }
    }

    // Dev Env
    modLocalRuntime("curse.maven:emi-580555:6420930")

    // Config
    modImplementation("fuzs.forgeconfigapiport:forgeconfigapiport-fabric:${Versions.FCAP}")

    // Compats
    // Modmenu
    modCompileOnly("com.terraformersmc:modmenu:${Versions.MOD_MENU}")
    modLocalRuntime("com.terraformersmc:modmenu:${Versions.MOD_MENU}")

    // Jade
    modCompileOnly("curse.maven:jade-324717:6738760")
    modLocalRuntime("curse.maven:jade-324717:6738760")

    // Skill Tree
    modCompileOnly("net.puffish:skillsmod:${Versions.PUFFISH_SKILLS}:fabric")
    modLocalRuntime("net.puffish:skillsmod:${Versions.PUFFISH_SKILLS}:fabric")
    modLocalRuntime("net.puffish:attributesmod:${Versions.PUFFISH_ATTRIBUTES}:fabric")
    modLocalRuntime("curse.maven:default-skill-trees-1074229:5852412")

    // Dungeon Difficulty
    modCompileOnly("curse.maven:dungeon-difficulty-645559:7279793")
    modLocalRuntime("curse.maven:dungeon-difficulty-645559:7279793")
    modLocalRuntime("maven.modrinth:tiny-config:3.0.0-fabric")

}

loom {
    val aw = file("src/main/resources/${Properties.MOD_ID}.accesswidener");
    if (aw.exists())
        accessWidenerPath.set(aw)
    mixin {
        defaultRefmapName.set("${Properties.MOD_ID}.refmap.json")
    }
    mods {
        register(Properties.MOD_ID) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["test"])
        }
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            setSource(sourceSets["test"])
            ideConfigGenerated(true)
            vmArgs("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            setSource(sourceSets["test"])
            ideConfigGenerated(true)
            vmArgs("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true")
        }
        register("datagen") {
            server()
            configName = "Fabric Datagen"
            setSource(sourceSets["test"])
            ideConfigGenerated(true)
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${file("../common/src/generated/resources")}")
            vmArg("-Dfabric-api.datagen.modid=${Properties.MOD_ID}")
            runDir("build/datagen")
        }
    }
}

tasks {
    named<ProcessResources>("processResources").configure {
        exclude("${Properties.MOD_ID}.cfg")
    }
}

publishMods {
    file.set(tasks.named<Jar>("remapJar").get().archiveFile)
    modLoaders.add("fabric")
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
//    }

    /*
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        parent(project(":common").tasks.named("publishGithub"))
    }

     */
}