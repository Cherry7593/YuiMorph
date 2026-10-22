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

// 客户端自动化测试：./gradlew :mod:runClientGameTest（会打开一个游戏窗口，测试完自动关闭）
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "yuimorph-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
        // 保留运行目录，便于预置 options.txt（例如在 macOS 上切换到 Vulkan）
        clearRunDirectory = false
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

// 第一人称手臂画廊只截部分生物：./gradlew :mod:runClientGameTest -ParmGallery=pig,zombie
tasks.matching { it.name == "runClientGameTest" }.configureEach {
    val gallery = providers.gradleProperty("armGallery")
    if (gallery.isPresent) (this as JavaExec).systemProperty("yuimorph.armGallery", gallery.get())
}

// 测试源码需要访问客户端代码
sourceSets.named("gametest") {
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}
