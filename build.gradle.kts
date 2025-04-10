plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.github.nizienko"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        instrumentationTools()
        bundledPlugin("org.intellij.plugins.markdown")
        pluginVerifier()
    }
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242.1")
            untilBuild.set("252.*")
        }
    }
}