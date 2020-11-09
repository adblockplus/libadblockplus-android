#!/usr/bin/env python3
"""
This script runs memory benchmark on android device.
1) installs apk for testing assuming it already is built
2) iterates following instructions NUM_TESTS times:
2.1) runs each test scenario under `memory_benchmark_test` array
2.2) collects the .csv output.
3) calculates average for the test scenarios and inserts it into a metrics.txt
   file
"""

import argparse
import csv
import subprocess

memory_benchmark_test = ["MemoryBenchmark_full_easy", "MemoryBenchmark_minDist"]

TEST_PACKAGE = "org.adblockplus.libadblockplus.android.webview.test"
NUM_TESTS = 10
OUTPUT_LOCATION = "adblock-android-webview/build/outputs/memory_benchmark"
COOL_OFF_ROW = 5
TOTAL_ANDROID_STUDIO_COLUMN = 2


def exit_with(message):
    print(message)
    sys.exit(1)


def run_command(command, exitOnError=True):
    child = subprocess.Popen(command, shell=True, close_fds=True)
    child.communicate()
    if child.returncode != 0:
        if exitOnError:
            exit_with(command + " failed!")
        else:
            print(command + " failed!")


def get_total_memory(memory_benchmark_csv):
    with open("{}/{}".format(OUTPUT_LOCATION, memory_benchmark_csv), mode='r') as csv_file:
        csv_reader = csv.reader(csv_file, delimiter=',')

        line_count = 0
        minDist_min_AA = 0

        for row in csv_reader:
            if line_count == COOL_OFF_ROW:
                minDist_min_AA = row[TOTAL_ANDROID_STUDIO_COLUMN]
            line_count += 1
        return minDist_min_AA


def main(args):
    adb_device = "adb"
    if args.device is not None:
        adb_device = "adb -s {}".format(args.device)

    run_command("{} logcat -c".format(adb_device))

    print("Preparing to run benchmark tests for: {}".format(TEST_PACKAGE))

    run_command("{} uninstall {}".format(adb_device, TEST_PACKAGE), False)
    run_command("{} install -r \
      adblock-android-webview/build/outputs/apk/androidTest/release/adblock-android-webview-release-androidTest.apk"
                .format(adb_device))

    run_command("{} shell pm grant {} android.permission.READ_EXTERNAL_STORAGE".format(adb_device,
                                                                                       TEST_PACKAGE))
    run_command("{} shell pm grant {} android.permission.WRITE_EXTERNAL_STORAGE".format(adb_device,
                                                                                        TEST_PACKAGE))

    print("Running memory benchmark tests...")

    minDist_min_AA_sum = 0
    full_easy_min_AA_sum = 0

    for i in range(0, NUM_TESTS):
        run_command("mkdir -p {}".format(OUTPUT_LOCATION))
        run_command("rm -f {}/*".format(OUTPUT_LOCATION))
        for test in memory_benchmark_test:
            print("Running {}...".format(test))
            print("Testing with AA enabled")
            run_command("{} shell am instrument -w -r -e debug false -e class {}.{}_full_AA -e \
            annotation androidx.test.filters.LargeTest {}/androidx.test.runner.AndroidJUnitRunner"
                    .format(adb_device, TEST_PACKAGE, test, TEST_PACKAGE))

            print("Test done pulling results with AA enabled")
            run_command("{} pull /storage/emulated/0/Download/memory_benchmark.csv {}/{}_full_AA.csv"
                    .format(adb_device, OUTPUT_LOCATION, test))

            print("Testing without AA...")
            run_command("{} shell am instrument -w -r -e debug false -e class {}.{}_min_AA -e \
            annotation androidx.test.filters.LargeTest {}/androidx.test.runner.AndroidJUnitRunner"
                    .format(adb_device, TEST_PACKAGE, test, TEST_PACKAGE))

            print("Test done pulling results without AA enabled")
            run_command("{} pull /storage/emulated/0/Download/memory_benchmark.csv {}/{}_min_AA.csv"
                    .format(adb_device, OUTPUT_LOCATION, test))

        minDist_min_AA_sum += int(float(get_total_memory("MemoryBenchmark_minDist_min_AA.csv")))
        full_easy_min_AA_sum += int(float(get_total_memory("MemoryBenchmark_full_easy_min_AA.csv")))

    run_command("{} logcat -d > {}/logcat.log".format(adb_device, OUTPUT_LOCATION))
    run_command("{} logcat -d -b main > {}/logcat_main.log".format(adb_device, OUTPUT_LOCATION))
    run_command("{} logcat -d -b crash > {}/logcat_crash.log".format(adb_device, OUTPUT_LOCATION))

    minDist_min_AA_avarage = minDist_min_AA_sum / NUM_TESTS
    full_easy_min_AA_sum_avarage = full_easy_min_AA_sum / NUM_TESTS

    metrics = open("metrics.txt", "w")
    metrics.write("AVERAGE_MIN_EASY_MIN_AA_KB {}\n".format(minDist_min_AA_avarage))
    metrics.write("AVERAGE_FULL_EASY_MIN_AA_KB {}".format(full_easy_min_AA_sum_avarage))
    metrics.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)

    parser.add_argument('--device', type=str,
                        help='Run this script on a specific device',
                        default=None)
    args = parser.parse_args()
    main(args)
