plugins {
    kotlin("jvm") version "2.1.21"
    application
}



sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include("com/example/feynman/physics/**", "com/example/feynman/latex/**", "com/example/feynman/draw/**", "*.kt")
        kotlin.srcDir("../preview-render/src")
    }
    test {
        kotlin.srcDir("../app/src/test/java")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

application {
    // Renders diagrams and answers to PNG for checking by eye (see preview-render/).
    mainClass.set("PreviewKt")
}

tasks.test {
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = true }
}
