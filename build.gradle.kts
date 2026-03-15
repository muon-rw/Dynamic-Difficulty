plugins {
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.133" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.6.2" apply false
    id("dev.mixinmcp.decompile") version "0.6.4" apply false
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes all artifacts to Maven and mod platforms (CurseForge/Modrinth)"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("publish") })
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("publishMods") })
}