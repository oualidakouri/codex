plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("com.batoulapps.adhan:adhan2:0.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
tasks.test { testLogging { events("passed", "skipped", "failed") } }
