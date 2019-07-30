
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val kashVersion = File("version.txt").readText().trim()
val kashJarBase = "kash"
val kashJar = "$kashJarBase-$kashVersion.jar"

allprojects {
    version = kashVersion
}

buildscript {
    val kotlinVer by extra { "1.3.41" }

    repositories {
        jcenter()
        mavenCentral()
        maven { setUrl("https://plugins.gradle.org/m2") }
    }

    dependencies {
        classpath(kotlin("gradle-plugin", kotlinVer))
        classpath("com.github.jengelman.gradle.plugins:shadow:4.0.4")
    }
}

repositories {
    jcenter()
    mavenCentral()
    maven { setUrl("https://plugins.gradle.org/m2") }
    maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
}

plugins {
    java
    application
    idea
    id("org.jetbrains.kotlin.jvm") version "1.3.40-eap-105"
    id("com.github.johnrengelman.shadow") version "4.0.2"
    id("ca.coglinc.javacc") version "2.4.0"
    id("com.github.breadmoirai.github-release") version "2.2.9"
}

val kotlinVer by extra { "1.3.41" }

sourceSets {
    main {
        java.srcDir("build/generated/javacc")
    }
}

dependencies {
    listOf("org.jline:jline:3.11.0",
            "org.fusesource:fuse-project:7.2.0.redhat-060",
            "org.slf4j:slf4j-api:1.8.0-beta4",
            "ch.qos.logback:logback-classic:1.3.0-alpha4",
            "com.google.inject:guice:4.2.2",
            "me.sargunvohra.lib:CakeParse:1.0.7",
            "org.apache.ivy:ivy:2.4.0")
        .forEach { compile(it) }

    compile("com.beust:klaxon:5.0.5") {
        exclude("org.jetbrains.kotlin")
    }

    listOf("compiler-embeddable", "scripting-compiler-embeddable", "scripting-common", "scripting-jvm",
                "scripting-jvm-host-embeddable", "main-kts")
        .forEach { compile(kotlin(it, kotlinVer)) }

    listOf("org.testng:testng:6.13.1",
            "org.assertj:assertj-core:3.5.2")
        .forEach { testCompile(it) }
}

application {
    mainClassName = "com.beust.kash.MainKt"
}

val shadowJar = tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set(kashJarBase)
        mergeServiceFiles()
    }
}

// Disable standard jar task to avoid building non-shadow jars
val jar by tasks.getting {
    enabled = false
}

// Update the scripts "run" and "kash" to use the correct jar file (which changes depending on the version number)
// This should only be run when the version number changes.
tasks.register("updateScripts") {
    listOf("run" to "./gradlew shadowJar && java",
            "kash" to "java -Dorg.slf4j.simpleLogger.defaultLogLevel=info",
            "kash-debug" to "java -Droot-level=DEBUG")
        .forEach { pair ->
            File(pair.first).apply {
                writeText(pair.second + " -jar build/libs/$kashJar" + "\n")
            }
        }
}

tasks {
    withType<Assemble> {
        finalizedBy("updateScripts")
    }
}

//
// Release stuff. To create and upload the distribution to Github releases:
// ./gradlew kashDist  // create the release zip file (build/distributions/kash-{version}.zip)
// ./gradlew upload // upload the release to github
//

githubRelease {
    // Defined in ~/.gradle/gradle.properties
    token(project.findProperty("githubToken")?.toString())
    owner("cbeust")
    repo("kash")
    overwrite(true)
    releaseAssets("$buildDir/distributions/kash-$kashVersion.zip")

// tagName("some tag")
// e.g. release notes
//    body("This is the body")
}

// These tasks are automatically generated by the application plug-in but they generate the wrong
// content, so disable them
listOf("distZip", "shadowDistZip").forEach { tasks[it].enabled = false }

distributions {
    // Create a task smallDistZip, which in turn will generate kash-small-{version}.zip. Then rename
    // that zip file to kash-{version}.zip.
    create("small") {
        contents {
            into("/")
            from("$buildDir/kashScripts") {
                include("*")
            }
            from("$buildDir/libs") {
                include("kash-$kashVersion.jar")
            }
        }
    }
}

tasks["smallDistZip"].dependsOn("createScript")

tasks.register("createScript") {
    doLast{
        println(">>> CREATESCRIPT")
        File("$buildDir/kashScripts").apply {
            mkdirs()
            File(absolutePath, "kash").apply {
                writeText("java -jar kash-$kashVersion.jar $*\n")
                setExecutable(true)
            }
        }
    }
}

tasks.register("kashDist") {
    dependsOn("assemble")
    dependsOn("smallDistZip")
    doLast {
        val file = "$buildDir/distributions/kash-$kashVersion.zip"
        File("$buildDir/distributions/kash-small-$kashVersion.zip")
                .renameTo(File(file))
        println("Created $file")
    }
}

// Upload to github releases
tasks.register("upload") {
    dependsOn("kashDist")
    dependsOn("githubRelease")
}

