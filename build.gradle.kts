import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.github.nizienko"
version = "1.0.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven("https://jitpack.io")
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2")
        bundledPlugin("org.intellij.plugins.markdown")
        pluginVerifier()
    }
    implementation("com.github.haroldadmin.lucilla:core:0.2.0") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            create("IC", "2025.2")
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("252")
        }
    }
}
