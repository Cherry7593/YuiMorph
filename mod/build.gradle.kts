plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
}

base.archivesName = "yuimorph-fabric"

loom {
    splitEnvironmentSourceSets()
    mods {
        register("yuimorph") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.kotlin)

    // 协议模块打包进 Mod（jar-in-jar）
    implementation(project(":protocol"))
    include(project(":protocol"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
