plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "dps.list.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Программа читает строки с клавиатуры: без этого `gradle run` не передаст ей ввод
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// ./gradlew benchmark --args="-t 1,2,4,8 --seconds 10"
tasks.register<JavaExec>("benchmark") {
    group = "application"
    description = "Замер числа шагов сортировки в обеих реализациях"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dps.list.Benchmark"
}
