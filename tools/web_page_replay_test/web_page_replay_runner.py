#!/usr/bin/env python3
"""
Web page runner is a helper script built around web_page_replay_go [1]
script to create static resources for our unit tests that later will be
benchmarked, and also replay them.
whats needed to run ( tested with ubuntu )
- python 3
- have docker in the system
- adb in the system

This script has a couple of assumptions:
1) Either debug or release version of android test is in the device
2) you have a rooted device
3) have device with certificate from container
4) You have pulled the docker imaged you planed on using

[1] https://github.com/catapult-project/catapult/tree/master/web_page_replay_go/src/webpagereplay

"""
import argparse
import sys
import os
import subprocess
import time

import signal, psutil

DOCKER_WORK_DIR = "/go/src/github.com/catapult/web_page_replay_go"
CONTAINER_NAME = "runner_docker_test"
DEFAULT_GITLAB_REGISTRY_NAME = "registry.gitlab.com/eyeo/adblockplus/libadblockplus-android/web_page_replay"

# Returns list of pairs of test -> static resource
def read_index_file(path):
    file = open(path, "r")
    index = []
    #for each line
    for line in file.read().splitlines():
        # simple ignore comment lines
        if line[0] == "#":
            continue
        index.append(line.split(' '))
    return index

def get_if_fnull():
    fnull = None
    if not args.v:
        fnull = open(os.devnull, "w")
    return fnull

def exit_with(message):
    print (message)
    sys.exit(1)

def run_command_async(command, path=None, isShell=False):
    fnull = get_if_fnull()
    child = subprocess.Popen(command, shell=isShell, cwd=path, stdout=fnull,
        close_fds=True)
    return child

def run_command(command, exitOnError=True, path=None):
    child = run_command_async(command, path, True)
    child.communicate()
    if child.returncode != 0:
        if exitOnError:
            exit_with(command + " failed!")
        else:
            print_if_debug(command + " failed!", args)
    return child

def print_if_debug(message, args):
    if args.v:
        print (message)

# kill all child process with sigint
# this is necessary for stopping go process
# go run launches a child process
def kill_child_processes(parent_pid, sig=signal.SIGINT):
    parent = psutil.Process(parent_pid)
    children = parent.children(recursive=True)
    for process in children:
        process.send_signal(sig)

class WprRunner:
    def __init__(self, wpr_path):
        self.is_docker_runner = True
        self.docker_image = ""
        self.container = None
        self.client = None
        self.wpr_path = wpr_path
        self.process = None

    #no need to init
    def init(self):
        pass

    def kill_any_instance_of_wpr(self):
         # -s 2 is SIGINT so that wpr can record int to the file
        run_command("kill -s 2 $(ps aux | grep /tmp | grep go | grep wpr \
        | awk '{ print $2 }')", False)

    def run(self, command):
        # launch a process
        try:
            # assert it has go
            self.kill_any_instance_of_wpr()
            run_command("go version")
            a = ["go", "run", "src/wpr.go", str(args.mode),
             "--http_port=8080", "--https_port=8081", "./filename"]
            child = run_command_async(a, self.wpr_path)
            self.process = child
        except Exception as err:
            exit_with("could not run wpr command " + str(err))

    def stop_wpr(self):
        assert(self.process != None)
        kill_child_processes(self.process.pid)
        # wait for it to be over
        self.process.communicate()

    def copy_file_from(self, path):
        run_command("cp {}/filename {}".format(self.wpr_path, path))

    def copy_file_to(self, path):
        run_command("cp {} {}/filename ".format(path, self.wpr_path))

class WprRunnerDocker:
    def __init__(self, docker_image):
        self.is_docker_runner = True
        self.docker_image = ""
        self.container = None
        self.docker_image = docker_image

    def build_command(self, mode):
        # building command
        wpr_command = "go run src/wpr.go "
        wpr_command += mode
        wpr_command += " --http_port=8080 --https_port=8081 "
        wpr_command += " ./filename"
        return wpr_command

    # run with normal command
    def set_as_os_runner(self, wpr_path):
        self.is_docker_runner = False
        self.wpr_path = wpr_path

    def remove_container_if_exists(self):
        try:
            run_command("docker rm --force " + CONTAINER_NAME, False)
        except Exception as err:
            pass

    def init(self):
        try:
            print_if_debug("grabbed docker client ... ", args)

            self.remove_container_if_exists()

            print("creating container ... ")

            # creates container running detached with the
            # same network as host and name CONTAINER_NAME
            run_command("docker run --net=\"host\" -d -it --name {} {}"
            .format(CONTAINER_NAME, self.docker_image))
            print("container created as {} ...".format(CONTAINER_NAME))
        except Exception as err:
            exit_with("found error while grabbing docker client " + str(err))

    def run(self, mode):
        try:
            command = self.build_command(mode)
            print_if_debug("running command on container -> {}".format(command),
             args)
            run_command("docker exec -d {} bash -c '{}'".format(CONTAINER_NAME,
             command))
            #create container
        except Exception as err:
            exit_with("found error while grabbing docker client " + str(err))

    def copy_file_from(self, path):
        if self.is_docker_runner:
            try:
                print_if_debug("calling stop.sh", args)
                # docker file copy to the respective path
                run_command("docker cp {}:{}/filename {}".format(CONTAINER_NAME,
                 DOCKER_WORK_DIR, path))
            except Exception as err:
                exit_with("Found error while copying file from the container \
                {}".format(str(err)))

    def copy_file_to(self, path):
        if self.is_docker_runner:
            try:
                # copy file to the container
                run_command("docker cp {} {}:{}/filename ".format(path,
                    CONTAINER_NAME, DOCKER_WORK_DIR))
            except Exception as err:
                exit_with("Found error while stopping container for test \
                {}".format(str(err)))

    def stop_wpr(self):
        if self.is_docker_runner:
            try:
                run_command("docker exec {} sh stop.sh".format(CONTAINER_NAME))
            except Exception as err:
                exit_with("Found error while stopping container for test \
                {}".format(str(err)))

    def remove(self):
        if self.is_docker_runner:
            try:
                # container cp
                print_if_debug("stoping and removing container ...", args)
                run_command("docker rm --force {}".format(CONTAINER_NAME), False)
            except Exception as err:
                exit_with("Found error while stopping container for test {}".format(str(err)) )

def set_port_forwarding(adb, device):
    run_command("{} {} reverse tcp:8080 tcp:8080".format(adb, device))
    run_command("{} {} reverse tcp:8081 tcp:8081".format(adb, device))
    run_command("{} {} shell su root iptables -t nat -A OUTPUT -p tcp --dport 80 -j \
    DNAT --to-destination 127.0.0.1:8080".format(adb, device))
    run_command("{} {} shell su root iptables -t nat -A OUTPUT -p tcp --dport 443 -j \
    DNAT --to-destination 127.0.0.1:8081".format(adb, device))

def main():
    if args.mode not in ["record", "replay"]:
        exit_with("mode should either be record or replay")
    device = ""
    if args.device != "":
        device = "-s " + args.device
    set_port_forwarding(args.adb, device)

    is_record = args.mode == "record"
    is_replay = args.mode == "replay"
    path = args.wpr_path
    is_docker_run = args.wpr_path == None

    print_if_debug("building wpr command ...", args)
    print_if_debug("reading index file ... ", args)

    index = read_index_file(args.index)
    # executing task
    wpr_runner = None

    if is_docker_run:
        wpr_runner = WprRunnerDocker(args.with_docker_image)
    else:
        wpr_runner = WprRunner(path)

    # iterate over index file
    for test, runner, filename in index:

        if is_docker_run:
            print("init docker")
            wpr_runner.init()

        # if record and file exists and not force continue
        if is_record and os.path.isfile(filename) and args.force == False:
            print("skipping test {}".format(test))
            continue

        # if replay we need to copy resource file from outside to the
        # running environment to the container
        if is_replay:
            print("copying file to ...")
            wpr_runner.copy_file_to(filename)

        # launch wpr
        wpr_runner.run(args.mode)

        #wait for wpr in docker is ready
        time.sleep(2)
        # launch gradlew test with emulator attached
        run_command("{} {} shell am instrument -w -r -e debug false -e class \
        {} {}".format(args.adb,
        device, test, runner))

        wpr_runner.stop_wpr()
        print ("finished executing test ...")
        if is_record:
            print("copying file ...")
            wpr_runner.copy_file_from(filename)

        # if record copy
        print("finished test {}".format(test))
    print("finished all tests")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", type=str, required=True,
                        help="Set the  mode in which you want to run, options record or replay")
    parser.add_argument("--with-docker-image", type=str, required=False,
                        default=DEFAULT_GITLAB_REGISTRY_NAME,
                        help="Will run under docker mode, which means that will launch the docker\
                        container under hood, docker image should be the one with web_page_replay built")
    parser.add_argument("--wpr-path", type=str, required=False,
                        help="if not using docker we can specify a local path to run web_page_replay")
    parser.add_argument("--force", type=bool, required=False, default=False,
                        help="If under record mode and with force enable it will overwrite previous\
                         index files")
    parser.add_argument("--index", type=str, required=True,
                        help="Path for the index file where you have pairs of test and static resources")
    parser.add_argument("--device", type=str, required=False, default="",
                        help="Android device id to run the tests under")
    parser.add_argument("--adb", type=str, required=False, default="adb",
                        help="Path to to adb")
    parser.add_argument("--v", type=bool, required=False, default=False, help="verbose")
    args = parser.parse_args()
    main()
