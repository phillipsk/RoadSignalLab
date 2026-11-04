plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.roadsignal.consumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.phillipsk.roadsignal.consumer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    if (providers.gradleProperty("consumePublishedSdk").orNull == "true") {
        implementation("io.github.phillipsk.roadsignal:telematics-sdk:${project.version}")
        implementation("io.github.phillipsk.roadsignal:cmt-bridge:${project.version}")
    } else {
        implementation(project(":telematics-sdk"))
        implementation(project(":cmt-bridge"))
    }
}

tasks.register("verifyPublishedDependencies") {
    doLast {
        check(providers.gradleProperty("consumePublishedSdk").orNull == "true")
        val sdkComponents =
            configurations
                .getByName("releaseRuntimeClasspath")
                .incoming
                .resolutionResult
                .allComponents
                .filter {
                    it.moduleVersion?.group == "io.github.phillipsk.roadsignal" &&
                        it.moduleVersion?.name in
                            setOf("telematics-core", "telematics-sdk", "cmt-bridge")
                }
        check(sdkComponents.size == 3) {
            "Expected all three published modules, got $sdkComponents"
        }
        check(
            sdkComponents.all {
                it.id is org.gradle.api.artifacts.component.ModuleComponentIdentifier
            },
        ) {
            "A project dependency leaked into the published consumer"
        }
        check(sdkComponents.all { it.moduleVersion?.version == project.version.toString() })
        logger.lifecycle(
            "Verified external Maven components: ${sdkComponents.map { it.id.displayName }}",
        )
    }
}
