plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.fabric.loom) apply false
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }
}
