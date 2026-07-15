plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application {
    mainClass = "com.rafaam11.businfo.probe.MainKt"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
