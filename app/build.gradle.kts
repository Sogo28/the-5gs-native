plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.aurelius.the_5gs"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurelius.the_5gs"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // CameraX dependencies
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    // ARCore dependencies
    implementation(libs.core)
    implementation(libs.obj)

    // cronet dependencies
    implementation(libs.cronet.embedded)

    // protobuf dependencies
    implementation(libs.protobuf.javalite) // Or protobuf-java for full features; javalite for Android
    implementation(libs.protobuf.kotlin.lite) // For Kotlin generated code

    // gRPC Dependencies for Android (using javalite for smaller code size)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub) // For Kotlin coroutine-based stubs, check latest

    // For using Cronet as gRPC transport (to get QUIC/HTTP3 benefits)
    implementation(libs.grpc.cronet)

    // If using Java-generated gRPC stubs and need JSR-305 annotations (often needed)
    implementation(libs.javax.annotation.api)
}

// Configure protobuf code generation
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    } // Use desired protoc version

    plugins {
        // Use create("pluginName") to define the plugin
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
        create("grpckt") { // For Kotlin gRPC stubs
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar" // Check latest
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
            task.plugins{
                create("grpc") {
                    option("lite")
                }
                create("grpckt") {
                    option("lite")
                }
            }

        }
    }
}

