object Config {
    const val moduleVersion = "4.3.0"

    const val buildToolsVersion = "30.0.2"
    const val compileSdkVersion = 29
    const val minSdkVersion = 16
    const val webView_minSdkVersion = 21
    const val targetSdkVersion = compileSdkVersion

    const val jvmTarget = "1.8"
}

object Bintray {
    const val userOrg = "adblockplus"
    const val groupId = "org.adblockplus"
    val licences = arrayOf("GPL-3.0")
    const val website = "https://github.com/adblockplus/libadblockplus-android"
    const val issueTracker = "http://issues.adblockplus.org/"
    const val repository = "https://github.com/adblockplus/libadblockplus-android.git"
}
