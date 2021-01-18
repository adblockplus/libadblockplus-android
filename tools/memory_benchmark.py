#!/usr/bin/env python3
"""
This script runs memory benchmark on android device.
1) installs apk for testing assuming it already is built
2) iterates following instructions NUM_TESTS times:
2.1) runs each test scenario under `memory_benchmark_adblockwebview_test` array
2.2) collects the .csv output.
3) calculates average for the test scenarios and inserts it into a metrics.txt
   file
"""

import argparse
import csv
import sys
import subprocess
import os
import time
import shutil

memory_benchmark_adblockwebview_test = ["MemoryBenchmark_full_easy",
    "MemoryBenchmark_minDist"]
memory_benchmark_system_webview_test = "SystemWebViewBenchmark"

TEST_PACKAGE = "org.adblockplus.libadblockplus.android.webview.test"
CSV_FOLDER = os.path.join("adblock-android-webview", "build", "outputs", "memory_benchmark")
DEVICE_BENCHMARK_CSV = "/storage/emulated/0/Download/memory_benchmark.csv"

NUM_TESTS = 10
COOL_OFF_LABEL = "5 seconds cool off"
TOTAL_ANDROID_STUDIO_COLUMN = 2
FILE_METRICS = "metrics.txt"

# this maps internal classpath of test to viewer name
# ultimately its what is shown to observer
testname_to_metrics = {
    "MemoryBenchmark_full_easy_min_AA"      : "MIN_EASY_MIN_AA_KB",
    "MemoryBenchmark_full_easy_full_AA"     : "MIN_EASY_MIN_AA_KB",
    "MemoryBenchmark_minDist_min_AA"        : "FULL_EASY_MIN_AA_KB",
    "MemoryBenchmark_minDist_full_AA"       : "FULL_EASY_MIN_AA_KB",
    "MemoryBenchmark_full_easy_min_AA_off"  : "MIN_EASY_MIN_AA_OFF_KB",
    "MemoryBenchmark_full_easy_full_AA_off" : "MIN_EASY_MIN_AA_OFF_KB",
    "MemoryBenchmark_minDist_min_AA_off"    : "FULL_EASY_MIN_AA_OFF_KB",
    "MemoryBenchmark_minDist_full_AA_off"   : "FULL_EASY_MIN_AA_OFF_KB",
    "SystemWebViewBenchmark"                : "SYSTEM_WEBVIEW_KB"
}


def map_to_metrics_name(testname, metric):
    assert (testname in testname_to_metrics.keys()), "invalid test name, \
    please corresponding 'show' value on testname_to_metrics variable"
    return metric.upper() + "_" + testname_to_metrics[testname]


def exit_with(message):
    print(message)
    print("exiting ...")
    sys.exit(1)


def get_if_fnull():
    fnull = None
    if not args.v:
        fnull = open(os.devnull, "w")
    return fnull


def run_command(command, exit_on_error=True):
    fnull = get_if_fnull()
    child = subprocess.run(command, shell=True, stdout=fnull)
    if child.returncode != 0:
        if exit_on_error:
            exit_with(command + " failed!")
        else:
            print(command + " failed!")


def get_total_memory(filepath):
    with open(filepath, mode='r') as csv_file:
        csv_reader = csv.reader(csv_file, delimiter=',')
        for row in csv_reader:
            if row[0] == COOL_OFF_LABEL:
                return int(row[TOTAL_ANDROID_STUDIO_COLUMN])

    assert False, "could not find cool off row"


def launch_test_and_collect(adb_device, testname, output):
    print("cleaning previous file ...")
    run_command("{} shell rm {}"
                .format(adb_device, DEVICE_BENCHMARK_CSV), False)

    print("Launching {} ... ".format(testname))
    classpath = TEST_PACKAGE + "." + testname
    run_command("{} shell am instrument -w -r -e debug false -e class {} -e \
            annotation androidx.test.filters.LargeTest {}/androidx.test.runner.AndroidJUnitRunner"
                .format(adb_device, classpath, TEST_PACKAGE))
    time.sleep(1)
    print("Test done pulling results ...")
    run_command("{} pull {} {}"
                .format(adb_device, DEVICE_BENCHMARK_CSV, output))


def launch_tests_and_collect(NUM_TESTS, adb_device, test_name):
    print("launch_tests_and_collect")
    for i in range(0, NUM_TESTS):
        output = CSV_FOLDER + "/" + test_name + "_" + str(i) + ".csv"
        launch_test_and_collect(adb_device, test_name, output)


def calc_stats(test, num_tests):
    print("calculating stats ...")
    memories = []
    for i in range(0, num_tests):
        filename = CSV_FOLDER + "/" + test + "_" + str(i) + ".csv"
        memories.append(get_total_memory(filename))

    memories.sort()

    _max    = int(max(memories))
    average = int(sum(memories) / len(memories))
    median  = int(memories[int(len(memories) / 2)])
    _min    = int(min(memories))
    return _max, average, median, _min


def save_logcat(adb_device):
    run_command("{} logcat -d > {}/logcat.log".format(adb_device, CSV_FOLDER))
    run_command("{} logcat -d -b main > {}/logcat_main.log".format(adb_device, CSV_FOLDER))
    run_command("{} logcat -d -b crash > {}/logcat_crash.log".format(adb_device, CSV_FOLDER))


def store_metrics_file(test, max, average, median, min):
    metrics = open(FILE_METRICS, "a")

    min_name     = map_to_metrics_name(test, "min")
    max_name     = map_to_metrics_name(test, "max")
    median_name  = map_to_metrics_name(test, "median")
    average_name = map_to_metrics_name(test, "average")

    metrics.write("{} {} \n".format(min_name, min))
    metrics.write("{} {} \n".format(max_name, max))
    metrics.write("{} {} \n".format(median_name, median))
    metrics.write("{} {} \n".format(average_name, average))

    metrics.close()


def main(args):
    adb_device = "adb"
    if args.device is not None:
        adb_device += " -s {}".format(args.device)

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

    shutil.rmtree(CSV_FOLDER, ignore_errors=True)
    os.makedirs(CSV_FOLDER)
    if os.path.exists(FILE_METRICS):
        os.remove(FILE_METRICS)

    # benchmark with adblockwebview
    for test in memory_benchmark_adblockwebview_test:
        for aa_subscription_list in ["_full_AA", "_min_AA"]:
            for aa_off_on in ["", "_off"]:
                test_name = test + aa_subscription_list + aa_off_on
                launch_tests_and_collect(NUM_TESTS, adb_device, test_name)
                max, average, median, min = calc_stats(test_name, NUM_TESTS)
                store_metrics_file(test_name, max, average, median, min)

    # test with system webview
    test_name = memory_benchmark_system_webview_test
    launch_tests_and_collect(NUM_TESTS, adb_device, test_name)
    max, average, median, min = calc_stats(test_name, NUM_TESTS)
    store_metrics_file(test_name, max, average, median, min)

    save_logcat(adb_device)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)

    parser.add_argument('--device', type=str,
                        help='Run this script on a specific device (defaults to use ANDROID_SERIAL)',
                        default=os.environ.get('ANDROID_SERIAL'))
    parser.add_argument("--v", type=bool, required=False, default=False, help="verbose")

    args = parser.parse_args()
    main(args)
