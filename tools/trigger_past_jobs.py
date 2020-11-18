#!/usr/bin/env python3
"""
This script will launch up to 5 benchmark jobs of the last versions to gather
benchmark data.

It checks if the release is an old version if so, then there are 3 releases
backported to have benchmark as well.
"""
import re
import subprocess
import os

INIT_VERSION_VERSION = "4.0"

TOKEN = os.getenv("CI_JOB_TOKEN", "")
PIPELINE_SOURCE = os.getenv("CI_PIPELINE_SOURCE", "")
PIPELINE = "https://gitlab.com/api/v4/projects/8817162/trigger/pipeline"

CI_COMMIT_TAG = os.getenv('CI_COMMIT_TAG', "")

# when it is release we launch a TAG job
# so it should be empty when it is not a release
is_tag_job = (CI_COMMIT_TAG != "")

map_to_backport_branch = {
    "4.0"   : "benchmark-backport-4.0",
    "4.1.0" : "benchmark-backport-4.1.0",
    "4.1.1" : "benchmark-backport-4.1.1",
}

def exit_with(message):
    print(message)
    sys.exit(1)

def run_command(command, exitOnError=True):
    child = subprocess.Popen(command, shell=True, close_fds=False, stdout=subprocess.PIPE)
    result_str = child.stdout.read()
    child.communicate()
    if child.returncode != 0:
        if exitOnError:
            exit_with(command + " failed!")
        else:
            print(command + " failed!")
    return str(result_str)

def filter(tags):
    clean_version = re.compile("^[0-9]+\\.[0-9]+(\\.[0-9]+){0,1}$")
    filter_tags = []
    for tag in tags.split("\\n"):
        tag = str(tag)
        if clean_version.match(tag):
            filter_tags.append(tag)
    return filter_tags

def get_trigger_tags(tags):
    last_5 = tags[len(tags) - 5 : len(tags) - 1]
    trigger_tags = []

    for tag in last_5:
        if tag >= INIT_VERSION_VERSION:
            if tag in map_to_backport_branch:
                trigger_tags.append(map_to_backport_branch[tag])
            else:
                trigger_tags.append(tag)
    return trigger_tags

def start_jobs(token, tags):
    for tag in tags:
        print(run_command("curl -X POST -F token={} -F ref={} {}".format(TOKEN, tag, PIPELINE)))

def main():
    all_tags = run_command("git tag | sort -r")
    filter_tags = filter(all_tags)
    filter_tags.sort()
    tags = get_trigger_tags(filter_tags)

    start_jobs(TOKEN, tags)

# avoid never ending loops
# this will trigger other rolling window jobs if it is either a scheduled job
# or a release job. But it must not be a pipeline (triggered)
# this way it will not start never ending loops
if (PIPELINE_SOURCE == "schedule" or is_tag_job) and PIPELINE_SOURCE != "pipeline":
    main()
