plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "org.openclover"
version = "5.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        pluginVerifier()
    }

    // OpenClover core (built separately via Maven, installed to mavenLocal)
    implementation("org.openclover:clover:5.0.0-SNAPSHOT")

    // TreeMap visualization
    implementation("net.sf.jtreemap:jtreemap:1.1.3") {
        exclude(group = "org.projectlombok", module = "lombok")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "org.openclover.idea"
        name = "OpenClover"
        version = project.version.toString()
        description = """
            OpenClover is a free and open-source code coverage tool for Java and Groovy.
            It instruments source code and records precisely what is executed when tests are run,
            helping developers identify areas where testing is weak.
        """.trimIndent()
        vendor {
            name = "OpenClover.org"
            email = "contact@openclover.org"
            url = "https://openclover.org"
        }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
        changeNotes = """
            OpenClover for IDEA rebuilt for IntelliJ 2025.1+.
            Full Kotlin rewrite with modern threading model and New UI support.
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Ensure clover-core is built and installed to mavenLocal before building this plugin.
    // Run from the root project: mvn install -pl clover-core -DskipTests
}
