plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.pleosfriends"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.pleosfriends"
  minSdk = 28
  targetSdk = 35
  versionCode = 5
  versionName = "0.5.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 flavorDimensions += "environment"
 productFlavors {
  create("demo") {
   dimension = "environment"
   applicationIdSuffix = ".demo"
   buildConfigField("boolean", "DEMO_MODE", "true")
   resValue("string", "app_name", "Pleos Friends · Demo")
  }
  create("connect") {
   dimension = "environment"
   buildConfigField("boolean", "DEMO_MODE", "false")
   resValue("string", "app_name", "Pleos Friends")
  }
 }
 sourceSets.getByName("demo").assets.srcDir("../scenarios")
 buildFeatures { compose = true; buildConfig = true }
 useLibrary("android.car")
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
 testOptions { unitTests.isReturnDefaultValues = true }
 signingConfigs {
  create("contest") {
   val file = rootProject.file("keystore/keystore.properties")
   require(file.exists()) { "Missing android/keystore/keystore.properties for contest release signing." }
   val props = file.readLines().mapNotNull { line ->
    val text = line.trim()
    if (text.isEmpty() || text.startsWith("#")) null
    else text.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
   }.toMap()
   storeFile = rootProject.file("keystore/${props.getValue("storeFile")}")
   storePassword = props.getValue("storePassword")
   keyAlias = props.getValue("keyAlias")
   keyPassword = props.getValue("keyPassword")
  }
 }
 buildTypes {
  getByName("release") {
   signingConfig = signingConfigs.getByName("contest")
   isMinifyEnabled = false
  }
 }
}
dependencies {
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui:1.7.6")
 implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
 implementation("androidx.compose.foundation:foundation:1.7.6")
 implementation("androidx.compose.material3:material3:1.3.1")
 implementation("androidx.compose.material:material-icons-extended:1.7.6")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.google.code.gson:gson:2.10.1")
 implementation("ai.pleos.playground:NaviHelper:3.0.5")
 implementation("ai.pleos.playground:FusedLocation:1.1.3")
 implementation("ai.pleos.playground:SpeechToText:3.3.1")
 implementation("ai.pleos.playground:TextToSpeech:3.3.1")
 implementation("ai.pleos.playground:Vehicle:2.3.2")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
 debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
 debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
