plugins {
    application
}

dependencies {
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dps.keygen.client.Main"
}

// Запускать из корня лабы, чтобы .key/.crt появлялись там, а не в client/
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
