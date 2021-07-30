plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    id("io.github.dryrum.update-changelog")
    id("io.github.dryrum.replace-in-file")
    id("io.github.dryrum.git-utils")
    id("io.github.dryrum.bump-version-code")
    id("com.squareup.sqldelight")
    id("kotlin-android")
}


apply(from = "${project.rootDir.path}/gradleutils/ktlint_utils.gradle")
apply(from = "${project.rootDir.path}/gradleutils/test_config.gradle")

val versionCodeMeta = (project.property("VERSION_CODE") as String).toInt()

android {
    compileSdkVersion(29)
    defaultConfig {
        applicationId = "com.sourcepointmeta.metaapp"
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = versionCodeMeta
        versionName = "${rootProject.project("cmplibrary").version}"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs{
        getByName("debug"){
            storeFile = file("../../keystore4tests.jks")
            storePassword = "123456"
            keyAlias=  "testalias"
            keyPassword = "123456"
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("localProd") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".localprod"
        }
        create("preprod") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preprod"
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        val sharedRes = "${project.rootDir.path}/ui-test-util/jsonFiles"
        getByName("test").resources.srcDir(sharedRes)
        getByName("androidTest").resources.srcDir(sharedRes)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    testOptions {
        // JSONObject return null during unit tests
        // https://stackoverflow.com/questions/49667567/android-org-json-jsonobject-returns-null-in-unit-tests/57592457#57592457
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lintOptions {
        // https://stackoverflow.com/questions/44751469/kotlin-extension-functions-suddenly-require-api-level-24/44752239
        isAbortOnError = false
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

}

sqldelight {
    database("MetaAppDB") {
        packageName = "com.sourcepointmeta.metaapp.db"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))


    // kotlin
    implementation(Libs.kotlinxCoroutinesCore)
    implementation(Libs.kotlinReflect)

    implementation(project(":cmplibrary"))

    api(Libs.okHttpLatest)

    // UI
    implementation(Libs.androidxAppcompat)
    implementation(Libs.androidxCore)
    implementation(Libs.material)
    implementation(Libs.constraintLayout)
    implementation(Libs.vectorDrawable)
    implementation(Libs.androidxLifLivedata)
    implementation(Libs.androidxLifViewModel)
    implementation("androidx.fragment:fragment-ktx:1.3.5")
    implementation("androidx.core:core-ktx:1.5.0") // ext drawable


    // Koin
//    implementation(Libs.koinCore)
//    implementation(Libs.koinCoreExt)
    implementation(Libs.koinAndroid)
    implementation(Libs.koinViewModel)

    // SQLDelight
    implementation(Libs.sqlDelight)
    implementation(Libs.sqlDelightCoroutines)

    // unit-test
    testImplementation(Libs.mockk)
    testImplementation(Libs.mockwebserver)

    // integration-test
    androidTestImplementation(Libs.koinTest)

}

versionCodePropPath {
    path = "gradle.properties"
}

addCommitPushConfig {
    fileList = listOf(
        "${rootDir.path}/samples/metaapp/gradle.properties"
    )
}
