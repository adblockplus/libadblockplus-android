plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java.srcDir("../hermes/buildSrc/src/main/java")
                .exclude("Config.kt")
                .exclude("Dependencies.kt")
    }
}

gradlePlugin {
    plugins {
        create("eyeoCMakePlugin") {
            id = "eyeo-cmake-plugin"
            implementationClass = "com.eyeo.cmake.CMakePlugin"
        }

        create("eyeoSharedTestPlugin") {
            id = "eyeo-shared-test-plugin"
            implementationClass = "com.eyeo.config.ConfigPlugin"
        }

        create("eyeoNpmPlugin") {
            id = "eyeo-npm-plugin"
            implementationClass = "com.eyeo.npm.NpmPlugin"
        }
    }
}