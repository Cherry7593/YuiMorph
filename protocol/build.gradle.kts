plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // 标准库由使用方提供：插件 shadow 打包，Mod 由 fabric-language-kotlin 提供
    compileOnly(kotlin("stdlib"))

    testImplementation(kotlin("stdlib"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
