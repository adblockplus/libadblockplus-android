object Versions {
    const val android_gradle_plugin = "4.2.0"
    const val core_ktx = "1.3.2"
    const val facebook_soloader = "0.10.1"
    const val findbugs = "3.0.1"
    const val kotlin = "1.4.32"
    const val jetbrains_annotations = "15.0"
    const val junit = "4.13.1"
    const val mockito = "2.28.2"
    const val mockito_kotlin = "2.2.0"
    const val robolectric = "4.4"
    const val timber = "4.7.1"
    const val wiremock = "2.26.2"
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
    const val androidx_test_ext = "androidx.test.ext:junit:${Versions.AndroidX.test_ext}"
    const val androidx_test_espresso_web = "androidx.test.espresso:espresso-web:${Versions.AndroidX.espresso}"
    const val androidx_test_espresso = "androidx.test.espresso:espresso-core:${Versions.AndroidX.espresso}"
    const val androidx_test_rules = "androidx.test:rules:${Versions.AndroidX.test}"
    const val androidx_test_runner = "androidx.test:runner:${Versions.AndroidX.test}"
    const val core_ktx = "androidx.core:core-ktx:${Versions.core_ktx}"
    const val facebook_soloader = "com.facebook.soloader:nativeloader:${Versions.facebook_soloader}"
    const val findbugs = "com.google.code.findbugs:jsr305:${Versions.findbugs}"

    const val kotlin = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.kotlin}"
    const val jetbrains_annotations = "org.jetbrains:annotations:${Versions.jetbrains_annotations}"
    const val junit = "junit:junit:${Versions.junit}"
    const val mockito_core = "org.mockito:mockito-core:${Versions.mockito}"
    const val mockito_android = "org.mockito:mockito-android:${Versions.mockito}"
    const val mockito_kotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:${Versions.mockito_kotlin}"
    const val roboletric = "org.robolectric:robolectric:${Versions.robolectric}"
    const val timber = "com.jakewharton.timber:timber:${Versions.timber}"

    const val tools_android_gradle_plugin = "com.android.tools.build:gradle:${Versions.android_gradle_plugin}"
    const val tools_kotlin_gradle_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"

    const val wiremock_standalone = "com.github.tomakehurst:wiremock-standalone:${Versions.wiremock}"
    const val androidx_appcompat = "androidx.appcompat:appcompat:${Versions.AndroidX.appcompat}"
    const val androidx_preference = "androidx.preference:preference:${Versions.AndroidX.preference}"
    const val android_material = "com.google.android.material:material:${Versions.android_material}"
}
