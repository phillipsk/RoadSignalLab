plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

dependencies { testImplementation(libs.junit) }

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "telematics-core"
            pom {
                name.set("RoadSignal core")
                description.set("Original educational telematics engine; not CMT software")
                url.set("https://github.com/phillipsk/RoadSignalLab")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "Project"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repository"))
        }
    }
}
