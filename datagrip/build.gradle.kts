plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.lucasbp"
version = "0.3.2"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath")
        if (localIde.isPresent) local(localIde.get()) else datagrip("2026.2.6.1")
        bundledPlugin("com.intellij.database")
        pluginVerifier()
    }
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

intellijPlatform {
    pluginConfiguration {
        name = "SQL Toolkit"
        ideaVersion {
            sinceBuild = "262.11879"
            untilBuild = "262.*"
        }
    }
    pluginVerification { ides { current() } }
    buildSearchableOptions = false
}

tasks.test { useJUnitPlatform() }

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
