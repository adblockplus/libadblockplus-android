#!/bin/bash
#memory_benchmark_test=("MemoryBenchmark_20" "MemoryBenchmark_50" "MemoryBenchmark_80" "MemoryBenchmark_full_easy" "MemoryBenchmark_minDist")
memory_benchmark_test=("MemoryBenchmark_full_easy" "MemoryBenchmark_minDist")

adb_path="adb"

# if custom adb location
if [ $# -ge 1 ]; then
    echo "using custom adb location at $1"
    adb_path="$1"
fi

#if custom device
if [ $# -ge 2 ]; then
    echo "selecting device $2"
    adb_path="$adb_path -s $2"
fi

#installing the apk, if -r if exists reinstall
$adb_path uninstall org.adblockplus.libadblockplus.android.webview.test

#stop on errors
set -e
$adb_path install -r adblock-android-webview/build/outputs/apk/androidTest/debug/adblock-android-webview-debug-androidTest.apk

#Need to grant write and read premissions after installation with android test not only in android manifest file
$adb_path shell pm grant org.adblockplus.libadblockplus.android.webview.test android.permission.READ_EXTERNAL_STORAGE
$adb_path shell pm grant org.adblockplus.libadblockplus.android.webview.test android.permission.WRITE_EXTERNAL_STORAGE

num_tests=10

for (( TEST_NUM=1; TEST_NUM<=$num_tests; TEST_NUM++ )); do
    mkdir -p adblock-android-webview/build/outputs/memory_benchmark
    set +e
    rm adblock-android-webview/build/outputs/memory_benchmark/*
    set -e
    for test in "${memory_benchmark_test[@]}"; do

        echo "Running $test ..."
        # run the test
        $adb_path shell am instrument -w -r -e debug false -e class "'org.adblockplus.libadblockplus.android.webview.test."$test"_full_AA'" org.adblockplus.libadblockplus.android.webview.test/androidx.test.runner.AndroidJUnitRunner
        echo "Testing with AA enabled"
        echo "Test done pulling pulling results"
        $adb_path pull /storage/emulated/0/Download/memory_benchmark.csv adblock-android-webview/build/outputs/memory_benchmark/"$test"_full_AA.csv

        echo "Testing without AA"
        $adb_path shell am instrument -w -r -e debug false -e class "'org.adblockplus.libadblockplus.android.webview.test."$test"_min_AA'" org.adblockplus.libadblockplus.android.webview.test/androidx.test.runner.AndroidJUnitRunner
        # collect information
        echo "Test done pulling pulling results"
        $adb_path pull /storage/emulated/0/Download/memory_benchmark.csv adblock-android-webview/build/outputs/memory_benchmark/"$test"_min_AA.csv

    done

    # removes stop at error just for the next command
    set +e
    rm metrics.txt 2> /dev/null
    # create metrix file with all test cases 5 seconds cool of stage total pss
    set -e
    find adblock-android-webview/build/outputs/memory_benchmark/ -type f |sort | xargs -n1 bash -c 'tail -n1 $0 && echo $0 ' | xargs -L 1 echo | sed s/'adblock-android-webview\/build\/outputs\/memory_benchmark\/MemoryBenchmark_'//g | sed s/.csv//g | sed s/,//g | xargs -n 12 bash -c 'echo ${11} $5 >> metrics.txt'
    mv metrics.txt metrics_"$TEST_NUM".txt
done

# get average of min_AA_mindDist only (most stable metric https://jira.eyeo.com/browse/DP-899)
find . -maxdepth 1 -type f | xargs -n1 bash -c 'cat $0 | grep minDist_min_AA' | xargs -n 2 bash -c 'echo $1' | awk '{ sum+=$1 } END { print "AVERAGE_MIN_EASY_MIN_AA_KB "sum/NR }' > metrics.txt
find . -maxdepth 1 -type f | xargs -n1 bash -c 'cat $0 | grep full_easy_min_AA' | xargs -n 2 bash -c 'echo $1' | awk '{ sum+=$1 } END { print "AVERAGE_FULL_EASY_MIN_AA_KB "sum/NR }' >> metrics.txt
rm metrics_* 2> /dev/null
