#!/usr/bin/env python3

from pathlib import Path
from zipfile import ZipFile

def main():
    file_path = "adblock-android-webviewapp/build/outputs/apk/release/adblock-android-webviewapp-release-unsigned.apk"

    with ZipFile(file_path, 'r') as zip_obj:
        list_of_file_names = zip_obj.namelist()
        for file_name in list_of_file_names:
            if file_name.endswith('.so'):
                zip_obj.extract(file_name, 'temp')
            elif file_name.endswith('.dex'):
                zip_obj.extract(file_name, 'temp')

    so_arm64_v8a_path = "temp/lib/arm64-v8a/libadblockplus-jni.so"
    arm64_v8a_storage = Path(so_arm64_v8a_path).stat().st_size / 1024
    storage_metrics = open("metrics.txt", "a")
    storage_metrics.write("ARM64_V8A_KB {}\n".format(arm64_v8a_storage))

    so_armeabi_v7a_path = "temp/lib/armeabi-v7a/libadblockplus-jni.so"
    armeabi_v7a_storage = Path(so_armeabi_v7a_path).stat().st_size / 1024
    storage_metrics.write("ARMEABI_V7A_KB {}\n".format(armeabi_v7a_storage))

    so_x86_path = "temp/lib/x86/libadblockplus-jni.so"
    x86_storage = Path(so_x86_path).stat().st_size / 1024
    storage_metrics.write("X86_KB {}\n".format(x86_storage))


    so_x86_64_path = "temp/lib/x86_64/libadblockplus-jni.so"
    x86_64_storage = Path(so_x86_64_path).stat().st_size / 1024
    storage_metrics.write("X86_64_KB {}\n".format(x86_64_storage))

    dex_path = "temp/classes.dex"
    dex_storage = Path(dex_path).stat().st_size / 1024
    storage_metrics.write("CLASSES.DEX_KB {}\n".format(dex_storage))

    storage_metrics.close()


if __name__ == '__main__':
    main()
