#!/usr/bin/env python3
"""
This script runs memory benchmark on android device.
1) installs apk for testing assuming it already is built
2) iterates following instructions NUM_TESTS times:
2.1) runs each test scenario under `memory_benchmark_adblockwebview_test` array
2.2) collects the .json output.
3) calculates average for the test scenarios and inserts it into a metrics.txt
   file
"""

import argparse
import sys
import subprocess
import os
import time
import shutil
import json

memory_benchmark_adblockwebview_test = ["MemoryBenchmark_full_easy",
    "MemoryBenchmark_minDist"]
memory_benchmark_system_webview_test = "SystemWebViewBenchmark"

TEST_APK = os.path.join("adblock-android-webview", "build", "outputs", "apk", "androidTest", "release", "adblock-android-webview-release-androidTest.apk")
TEST_PACKAGE = "org.adblockplus.libadblockplus.android.webview.test"
OUTPUT_FOLDER = os.path.join("adblock-android-webview", "build", "outputs", "memory_benchmark")
JSON_FILE = os.path.join(OUTPUT_FOLDER, "memory_benchmark.json")
DEVICE_BENCHMARK_JSON = "/storage/emulated/0/Download/memory_benchmark.json"

DEFAULT_NUM_TRIES = 10
FILE_METRICS = "metrics.txt"

# this maps internal classpath of test to viewer name
# ultimately its what is shown to observer
# the rest of this variable is filled by fill_map_classpath_metric
classpath_to_metric = { "SystemWebViewBenchmark" : "SYSTEM_WEBVIEW_KB" }
# fills the classpath to metric dictionary
# since it is possible to do so
def fill_map_classpath_metric():
    init = "MemoryBenchmark"
    end = "_KB"

    easylist_subscription_types = {
        "full_easy" : "FULL_EASY",
        "minDist" : "MIN_EASY"
    }
    aa_subscription_types = {
        "min_AA" : "MIN_AA",
        "full_AA" : "FULL_AA"
    }
    options = {
        "" : "",
        "AA_disabled" : "AA_DISABLED",
        "adblock_disabled" : "ADBLOCK_DISABLED"
    }

    for easylist_subscripteasyion_type in easylist_subscription_types.keys():
        for aa_subscription_type in aa_subscription_types.keys():
            for option in options.keys():

                classpath = "_".join([
                    init,
                    easylist_subscripteasyion_type,
                    aa_subscription_type,
                    option])
                metric = "_".join([
                    easylist_subscription_types[easylist_subscripteasyion_type],
                    aa_subscription_types[aa_subscription_type],
                    options[option]])

                if option != "":
                    classpath_to_metric[classpath] = metric + end
                else:
                    classpath_to_metric[classpath[:-1]] = metric[:-1] + end


def map_to_metrics_name(testname, metric):
    assert (testname in classpath_to_metric.keys()), "invalid test name, \
    please corresponding 'show' value on classpath_to_metric variable"
    return metric.upper() + "_" + classpath_to_metric[testname]


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
            print("If it fails retrieving the file it might be a permissions "+\
                "issue in newer versions of android you might need to go: "   +\
                "developer options -> (Security settings) allow granting "    +\
                "permissions")
            exit_with(command + " failed!")
        else:
            print(command + " failed!")


def launch_test_and_collect(adb_device, testname):
    print("Launching {} ... ".format(testname))
    classpath = TEST_PACKAGE + "." + testname
    run_command("{} shell am instrument -w -r -e debug false -e num_tries {} -e class {} -e \
            annotation androidx.test.filters.LargeTest {}/androidx.test.runner.AndroidJUnitRunner"
                .format(adb_device, getNumberOfTries(), classpath, TEST_PACKAGE))
    time.sleep(1)


def pull_result_json(adb_device):
    print("Test done pulling results ...")
    output = os.path.join(OUTPUT_FOLDER, "memory_benchmark.json")
    run_command("{} pull {} {}"
                .format(adb_device, DEVICE_BENCHMARK_JSON, output))


def calc_stats_and_save(json_file_path, test_names: list):
    print("calculating stats ...")

    with open(json_file_path) as json_file:
        data = json.load(json_file)
        for i, run in enumerate(data):
            memories = []
            for _try in run["tries"]: 
                memories.append(int(_try["total_android_studio"]))

            memories.sort()
            _max    = int(max(memories))
            average = int(sum(memories) / len(memories))
            median  = int(memories[int(len(memories) / 2)])
            _min    = int(min(memories))

            store_metrics_file(test_names[i], _max, average, median, _min)


def save_logcat(adb_device):
    run_command("{} logcat -d > {}/logcat.log".format(adb_device, OUTPUT_FOLDER))
    run_command("{} logcat -d -b main > {}/logcat_main.log".format(adb_device, OUTPUT_FOLDER))
    run_command("{} logcat -d -b crash > {}/logcat_crash.log".format(adb_device, OUTPUT_FOLDER))


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

def prepare_device(adb_device):
    run_command("{} logcat -c".format(adb_device), False)

    print("cleaning previous file ...")
    run_command("{} shell rm {}"
                .format(adb_device, DEVICE_BENCHMARK_JSON), False)

    print("Preparing to run benchmark tests for: {}".format(TEST_PACKAGE))

    run_command("{} uninstall {}".format(adb_device, TEST_PACKAGE), False)
    run_command("{} install -r {} ".format(adb_device, TEST_APK))
    # in newer versions of android you might need to go:
    # developer options -> (Security settings) allow granting permissions
    run_command("{} shell pm grant {} android.permission.READ_EXTERNAL_STORAGE".format(adb_device,
                                                                                    TEST_PACKAGE))
    run_command("{} shell pm grant {} android.permission.WRITE_EXTERNAL_STORAGE".format(adb_device,
                                                                                        TEST_PACKAGE))


def getNumberOfTries():
    return os.environ.get("NUM_TRIES", str(DEFAULT_NUM_TRIES))


def main(args):
    adb_device = "adb"
    if args.device is not None:
        adb_device += " -s {}".format(args.device)

    if not args.skip_test: 
        prepare_device(adb_device)
        shutil.rmtree(OUTPUT_FOLDER, ignore_errors=True)
        os.makedirs(OUTPUT_FOLDER)

    if os.path.exists(FILE_METRICS):
        os.remove(FILE_METRICS)

    print("Running memory benchmark tests...")

    # We want to know the test name to save it properly to metrics.txt afterwards but we do not store it anywhere in json.
    # Since metrics.txt will be deprecated soon, we can just stack it in the array and pass to a saving function
    # because we know the exact order of test runs and corresponding names
    test_names = list()

    # benchmark with adblockwebview
    for test in memory_benchmark_adblockwebview_test:
        for aa_subscription_list in ["_full_AA", "_min_AA"]:
            # AA on or off and adblock disabled tests
            for option in ["_adblock_disabled", "_AA_disabled", ""]:
                test_name = test + aa_subscription_list + option
                test_names.append(test_name)
                if not args.skip_test: 
                    launch_test_and_collect(adb_device, test_name)

    # test with system webview
    test_name = memory_benchmark_system_webview_test
    test_names.append(test_name)

    if not args.skip_test: 
        launch_test_and_collect(adb_device, test_name)
        pull_result_json(adb_device)
        save_logcat(adb_device)

    calc_stats_and_save(args.json, test_names)    

if __name__ == "__main__":
    fill_map_classpath_metric()
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)

    parser.add_argument('--device', type=str,
                        help='Run this script on a specific device (defaults to use ANDROID_SERIAL)',
                        default=os.environ.get('ANDROID_SERIAL'))
    parser.add_argument("--v", type=bool, required=False, default=False, help="verbose")
    parser.add_argument("--json", type=str, required=False, default=JSON_FILE, 
                        help="Path to a json file with memory benchmark")
    parser.add_argument("--skip-test", type=bool, required=False, default=False, 
                        help="Skip running test and generate metrics.txt from specified or default json")

    args = parser.parse_args()
    main(args)
