plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "io.roadsignal.cmt"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":telematics-sdk"))
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.androidx.work.testing)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "cmt-bridge"
                pom {
                    name.set("RoadSignal cmt-bridge")
                    description.set(
                        "Educational telematics lab; CMT bridge is a mock, not vendor software",
                    )
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
}
