#!/usr/bin/env python3
"""
This script runs performance benchmark under the adblock-android-benchmark module.
It
1) Installs the release version of apk ( assumes it is already built )
2) locks cpu on device to achieve reliable metrics
3) runs the benchmark
4) gets benchmarkData.json from device into $BENCHMARK_PULL_DIR
5) creates enrichedBenchmarkData.json from benchmarkData.json
6) Adds id (i. e. pipeline id) and time (i. e. number of seconds passed since epoch) to enrichedBenchmarkData.json
"""

import argparse
import subprocess
import sys
import os
import json
import shutil
import time

TEST_PACKAGE = "org.adblockplus.libadblockplus.benchmark.test"
TEST_APK = "adblock-android-benchmark/build/outputs/apk/androidTest/release/adblock-android-benchmark-release-androidTest.apk"

# performance benchmark location
BENCHMARK_PULL_DIR = os.getenv("BENCHMARK_PULL_DIR",
                               "adblock-android-benchmark/build/outputs/connected_android_test_additional_output")

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

def main(args):
    adb_device = "adb"
    if args.device is not None:
        adb_device = "adb -s {}".format(args.device)

    run_command("{} uninstall {}".format(adb_device, TEST_PACKAGE), False)
    run_command("{} install -r {}"
                .format(adb_device, TEST_APK))

    run_command("{} push adblock-android-benchmark/scripts/lockClocks.sh \
        $LOCK_CLOCK_FILE".format(adb_device))
    run_command("{} shell su - -c $LOCK_CLOCK_FILE".format(adb_device))
    run_command("{} shell rm $LOCK_CLOCK_FILE".format(adb_device))
    # treat error as warning 'UNLOCKED' since in some devices even after locking
    # clocks would still prompt this error
    run_command("{} shell am instrument -w -r \
        -e androidx.benchmark.suppressErrors UNLOCKED\
        -e no-isolated-storage 1 \
        -e androidx.benchmark.output.enable true \
        org.adblockplus.libadblockplus.benchmark.test/androidx.benchmark.junit4.AndroidBenchmarkRunner".format(adb_device))
    # collect benchmarking results raw file
    run_command("mkdir -p $BENCHMARK_PULL_DIR")
    run_command("{} pull \
        /storage/emulated/0/Download/org.adblockplus.libadblockplus.benchmark.test-benchmarkData.json $BENCHMARK_PULL_DIR/benchmarkData.json".format(adb_device))

    shutil.copyfile("{}/{}".format(BENCHMARK_PULL_DIR, "benchmarkData.json"),
                    "{}/{}".format(BENCHMARK_PULL_DIR, "enrichedBenchmarkData.json"))

    with open("{}/{}".format(BENCHMARK_PULL_DIR, "enrichedBenchmarkData.json"), 'r+') as json_file:
        data = json.load(json_file)
        info = {'id': os.getenv('CI_PIPELINE_ID', ''), 'time': time.time()}
        data['info'] = info

        json_file.seek(0)
        json.dump(data, json_file, indent=4)
        json_file.truncate()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)

    parser.add_argument('--device', type=str,
                        help='Run this script on a specific device (defaults to use ANDROID_SERIAL)',
                        default=os.environ.get('ANDROID_SERIAL'))
    args = parser.parse_args()
    main(args)
