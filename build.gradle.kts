plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.yu212"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        clion("2025.3.1")
        bundledPlugin("com.intellij.clion")
        bundledModule("intellij.platform.dap")
    }
}

tasks.test {
    useJUnitPlatform()
}
