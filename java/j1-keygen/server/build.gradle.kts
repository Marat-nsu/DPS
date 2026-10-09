plugins {
    application
}

dependencies {
    implementation(libs.bcpkix)
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dps.keygen.server.Main"
}

// Запускать из корня лабы, чтобы ca.key и выходные файлы лежали там, а не в server/
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
