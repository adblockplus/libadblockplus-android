#!/usr/bin/env python3

from pathlib import Path
from zipfile import ZipFile

def main():
    file_path = "adblock-android-webviewapp/build/outputs/apk/release/adblock-android-webviewapp-release-unsigned.apk"

    so_arm64_v8a_path = "lib/arm64-v8a/libadblockplus-jni.so"
    so_armeabi_v7a_path = "lib/armeabi-v7a/libadblockplus-jni.so"
    so_x86_path = "lib/x86/libadblockplus-jni.so"
    so_x86_64_path = "lib/x86_64/libadblockplus-jni.so"
    dex_path = "classes.dex"
    # measures size of the `kotlin` folder in the apk; it contains basic kotlin classes and extensions
    kotlin_path = "kotlin/"

    storage_metrics = open("metrics.txt", "a")

    kotlin_folder_size = 0
    kotlin_folder_compressed_size = 0

    with ZipFile(file_path, 'r') as zip_obj:
        list_of_file_names = zip_obj.infolist()
        for file_info in list_of_file_names:
            # extract to vars only once
            filesize_kb = round(file_info.file_size / 1024)
            filesize_compressed_kb = round(file_info.compress_size / 1024)
            # ---------------
            # -- arm64_v8a --
            # ---------------
            if file_info.filename == so_arm64_v8a_path:
                storage_metrics.write("ARM64_V8A_KB {}\n".format(filesize_kb))
                storage_metrics.write("ARM64_V8A_COMPRESSED_KB {}\n".format(filesize_compressed_kb))
            # ---------------
            # - armeabi_v7a -
            # ---------------
            elif file_info.filename == so_armeabi_v7a_path:
                storage_metrics.write("ARMEABI_V7A_KB {}\n".format(filesize_kb))
                storage_metrics.write("ARMEABI_V7A_COMPRESSED_KB {}\n".format(filesize_compressed_kb))
            # ---------------
            # ----- x86 -----
            # ---------------
            elif file_info.filename == so_x86_path:
                storage_metrics.write("X86_KB {}\n".format(filesize_kb))
                storage_metrics.write("X86_KB_COMPRESSED_KB {}\n".format(filesize_compressed_kb))
            # ---------------
            # --- x86_64 ----
            # ---------------
            elif file_info.filename == so_x86_64_path:
                storage_metrics.write("X86_64_KB {}\n".format(filesize_kb))
                storage_metrics.write("X86_64_COMPRESSED_KB {}\n".format(filesize_compressed_kb))
            # ---------------
            # - classes.dex -
            # ---------------
            elif file_info.filename == dex_path:
                storage_metrics.write("CLASSES.DEX_KB {}\n".format(filesize_kb))
                storage_metrics.write("CLASSES.DEX_COMPRESSED_KB {}\n".format(filesize_compressed_kb))
            # ---------------
            # --- Kotlin ----
            # ---------------
            elif file_info.filename.startswith(kotlin_path):
                kotlin_folder_size += filesize_kb
                kotlin_folder_compressed_size += filesize_compressed_kb
            # ---------------

    zip_obj.close()

    # write kotlin when calculated
    storage_metrics.write("KOTLIN_FOLDER_KB {}\n".format(kotlin_folder_size))
    storage_metrics.write("KOTLIN_FOLDER_COMPRESSED_KB {}\n".format(kotlin_folder_compressed_size))

    # nice to have total webviewapp APK size in KB
    storage_metrics.write("WEBAPP_APK_SIZE_KB {}\n".format(round(Path(file_path).stat().st_size / 1024)))

    storage_metrics.close()


if __name__ == '__main__':
    main()
