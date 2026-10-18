plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

base.archivesName = "YuiMorph"

dependencies {
    compileOnly(libs.folia.api)
    compileOnly(libs.packetevents.spigot)

    implementation(project(":protocol"))
    implementation(kotlin("stdlib"))

    testImplementation(libs.folia.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier = ""
    // 重定位 Kotlin，避免和服务器上其他 Kotlin 插件冲突
    relocate("kotlin", "moe.yui.morph.libs.kotlin")
    relocate("org.intellij.lang.annotations", "moe.yui.morph.libs.annotations.intellij")
    relocate("org.jetbrains.annotations", "moe.yui.morph.libs.annotations.jetbrains")
    exclude("META-INF/*.kotlin_module", "META-INF/versions/**/module-info.class", "DebugProbesKt.bin")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

/** 构建并拷贝到本地测试服 plugins/ 目录（需要重启测试服生效）。 */
val deploy by tasks.registering(Copy::class) {
    group = "yuimorph"
    description = "构建插件并拷贝到本地测试服"
    val serverDir = providers.gradleProperty("yuimorph.testServerDir")
    from(tasks.shadowJar)
    into(serverDir.map { "$it/plugins" })
    doFirst {
        // 清理旧版本 jar
        val plugins = file("${serverDir.get()}/plugins")
        plugins.listFiles { f -> f.name.startsWith("YuiMorph-") && f.name.endsWith(".jar") }?.forEach { it.delete() }
    }
}
