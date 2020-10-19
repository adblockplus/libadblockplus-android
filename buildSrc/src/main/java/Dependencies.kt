private object Versions {
    const val android_gradle_plugin = "3.5.3"
    const val kotlin = "1.3.72"
    const val junit4 = "4.13.1"
    const val mockito = "2.28.2"
    const val mockito_kotlin = "2.2.0"
    const val timber = "4.7.1"
    const val wiremock = "2.27.2"

    @Deprecated("The support library is deprecated in favor of AndroidX")
    const val android_support = "28.0.0"

    @Deprecated("This doesn't work with the latest Android Gradle Plugin")
    const val bintray_release = "0.9.1"

    object AndroidX {
        const val benchmark = "1.0.0"
        const val espresso = "3.3.0"
        const val multidex = "2.0.1"
        const val test = "1.3.0"
        const val test_ext = "1.1.2"
    }
}

@Suppress("unused")
object Deps {
    const val androidx_benchmark_gradle_plugin = "androidx.benchmark:benchmark-gradle-plugin:${Versions.AndroidX.benchmark}"
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
    const val tools_kotlin_gradle_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"

    const val wiremock_standalone = "com.github.tomakehurst:wiremock-standalone:${Versions.wiremock}"

    @Deprecated("The support library is deprecated in favor of AndroidX")
    const val android_support_appcompat = "com.android.support:appcompat-v7:${Versions.android_support}"

    @Deprecated("The support library is deprecated in favor of AndroidX")
    const val android_support_design = "com.android.support:design:${Versions.android_support}"

    @Deprecated("The support library is deprecated in favor of AndroidX")
    const val android_support_preference = "com.android.support:preference-v14:${Versions.android_support}"

    @Deprecated("This doesn't work with the latest Android Gradle Plugin")
    const val novoda_bintray_release = "com.novoda:bintray-release:${Versions.bintray_release}"
}
