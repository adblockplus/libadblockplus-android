private object Versions {
    const val android_gradle_plugin = "3.5.3"
    const val bintray_gradle_plugin = "1.8.5"
    const val kotlin = "1.3.72"
    const val junit4 = "4.13.1"
    const val mockito = "2.28.2"
    const val mockito_kotlin = "2.2.0"
    const val timber = "4.7.1"
    const val wiremock = "2.27.2"
    const val android_material = "1.2.1"

    object AndroidX {
        const val benchmark = "1.0.0"
        const val espresso = "3.3.0"
        const val multidex = "2.0.0"
        const val test = "1.3.0"
        const val test_ext = "1.1.2"
        const val appcompat = "1.3.0-beta01"
        const val preference = "1.1.1"
    }
}

// externally exposed version of "Versions" class
object Vers {
    const val checkstyle = "8.39"
}

@Suppress("unused")
object Deps {
    const val androidx_benchmark_gradle_plugin = "androidx.benchmark:benchmark-gradle-plugin:${Versions.AndroidX.benchmark}"
    const val androidx_benchmark_common = "androidx.benchmark:benchmark-common:${Versions.AndroidX.benchmark}"
    const val androidx_benchmark_junit4 = "androidx.benchmark:benchmark-junit4:${Versions.AndroidX.benchmark}"
    const val androidx_multidex = "androidx.multidex:multidex:${Versions.AndroidX.multidex}"
    const val androidx_test_core = "androidx.test:core:${Versions.AndroidX.test}"
    const val androidx_test_ext_junit = "androidx.test.ext:junit:${Versions.AndroidX.test_ext}"
    const val androidx_test_espresso_web = "androidx.test.espresso:espresso-web:${Versions.AndroidX.espresso}"
    const val androidx_test_espresso_core = "androidx.test.espresso:espresso-core:${Versions.AndroidX.espresso}"
    const val androidx_test_rules = "androidx.test:rules:${Versions.AndroidX.test}"
    const val androidx_test_runner = "androidx.test:runner:${Versions.AndroidX.test}"

    const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlin}"
    const val junit4 = "junit:junit:${Versions.junit4}"
    const val mockito_core = "org.mockito:mockito-core:${Versions.mockito}"
    const val mockito_android = "org.mockito:mockito-android:${Versions.mockito}"
    const val mockito_kotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:${Versions.mockito_kotlin}"
    const val timber = "com.jakewharton.timber:timber:${Versions.timber}"

    const val tools_android_gradle_plugin = "com.android.tools.build:gradle:${Versions.android_gradle_plugin}"
    const val tools_bintray_gradle_plugin = "com.jfrog.bintray.gradle:gradle-bintray-plugin:${Versions.bintray_gradle_plugin}"
    const val tools_kotlin_gradle_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"

    const val wiremock_standalone = "com.github.tomakehurst:wiremock-standalone:${Versions.wiremock}"
    const val androidx_appcompat = "androidx.appcompat:appcompat:${Versions.AndroidX.appcompat}"
    const val androidx_preference = "androidx.preference:preference:${Versions.AndroidX.preference}"
    const val android_material = "com.google.android.material:material:${Versions.android_material}"
}
