pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
        maven("https://repo.codemc.io/repository/maven-releases/") { name = "CodeMC" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }
}

rootProject.name = "YuiMorph"

include("protocol", "plugin", "mod")
