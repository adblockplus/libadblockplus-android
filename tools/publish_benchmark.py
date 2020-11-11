#!/usr/bin/env python3
"""
This script is putting the benchmarking results to Google Sheets spreadsheet.
It takes for an input environment variables:
- Standard GitLab CI env vars: CI_COMMIT_BRANCH or CI_COMMIT_TAG, CI_COMMIT_SHA,
  CI_PIPELINE_ID, CI_PROJECT_URL
- Custom GSHEETS_SPREADSHEET_ID should contain the id of the spreadsheet.
- Custom BENCHMARK_GSHEETS_CREDENTIALS_FILE should contain the name of the file with credentials
  JSON from https://developers.google.com/identity/protocols/oauth2/web-server#creatingcred
- [Optional] Custom BENCHMARK_GSHEETS_TOKEN_FILE_DIR where the token will be saved, see below for
  the default.

Custom environment variables should be entered as vars and files through GitLab CI /settings/ci_cd
-> Variables. Note: Protect variable setting: Export variable to pipelines running on protected
branches and tags only.

The data contains the fixed columns WORKSHEET_FIXED_HEADERS_LIST and data for benchmarking values
that is provided via `BenchmarkDataset.values` dictionary. Data insert is smart, so columns will be
added as necessary (but not removed), and empty cells will be added for columns where data does
not currently exist in `BenchmarkDataset.values`.
"""

import os
import pygsheets
from dataclasses import dataclass
from datetime import datetime
import time
import json

BENCHMARK_GSHEETS_SPREADSHEET_ID = os.getenv('BENCHMARK_GSHEETS_SPREADSHEET_ID')
BENCHMARK_GSHEETS_CREDENTIALS_FILE = os.getenv('BENCHMARK_GSHEETS_CREDENTIALS_FILE')
BENCHMARK_GSHEETS_TOKEN_FILE_DIR = os.getenv('BENCHMARK_GSHEETS_TOKEN_FILE_DIR')
CI_COMMIT_TAG = os.getenv('CI_COMMIT_TAG', "")

# when it is release we launch a TAG job
# so it should be empty when it is not a release
is_release = (CI_COMMIT_TAG != "")

# where performance benchmark located
BENCHMARK_PULL_DIR = os.getenv("BENCHMARK_PULL_DIR", \
    "adblock-android-benchmark/build/outputs/connected_android_test_additional_output")

CI_PROJECT_URL = os.getenv('CI_PROJECT_URL')

OUTPUT_WORKSHEET_NAME = 'Data'
WORKSHEET_FIXED_HEADERS_LIST = ["Branch/Tag", "Commit", "Pipeline", "Date/Time", "Device", "Release"]

GITLAB_BRANCH_FOLDER = '/-/tree/'
GITLAB_TAG_FOLDER = '/-/tags/'
GITLAB_COMMIT_FOLDER = '/-/commit/'
GITLAB_PIPELINE_FOLDER = '/-/pipelines/'


class GSheetsClientProvider:

    def __init__(self, credentials_file, token_dir):
        self.credentials_file = credentials_file
        self.credentials_dir = token_dir
        self.auth_completed = False
        self.client = None

    def authenticate(self):
        if not self.__credentials_exists():
            raise PygsheetsClientError('Your specified credentials file does not exist!')
        try:
            self.client = pygsheets.authorize(client_secret=self.credentials_file,
                                              credentials_directory=self.credentials_dir)
        except:
            raise PygsheetsClientError('Google Account authentication failed!')
        self.auth_completed = True
        return self

    def get_client(self):
        if self.auth_completed:
            return self.client
        else:
            raise PygsheetsClientError('Pygsheets client is not authenticated. '
                                       'Please run authenticate() method to '
                                       'authenticate and continue')

    def __credentials_exists(self):
        return (os.path.isfile(os.path.join(self.credentials_dir,
                                            'sheets.googleapis.com-python.json'))
                or os.path.isfile(self.credentials_file))


class PygsheetsClientError(Exception):
    pass


@dataclass
class BenchmarkDataset:
    device: str
    values: dict
    branch: str = os.getenv('CI_COMMIT_BRANCH', '')
    tag: str = os.getenv('CI_COMMIT_TAG', '')
    commit: str = os.getenv('CI_COMMIT_SHA', '')
    pipeline: str = os.getenv('CI_PIPELINE_ID', '')
    date_time: datetime = datetime.utcnow()

    def __init__(self, device, values):
        self.device = device
        self.values = values


class Worksheet:

    def __init__(self, client, spreadsheet_key, worksheet_name):
        try:
            self.spreadsheet = client.open_by_key(spreadsheet_key)
        except pygsheets.SpreadsheetNotFound:
            raise GoogleWorksheetError('Could not find a spreadsheet with key {}.'
                                       .format(spreadsheet_key))

        try:
            self.worksheet = self.spreadsheet.worksheet_by_title(worksheet_name)
        except pygsheets.WorksheetNotFound:
            raise GoogleWorksheetError('Could not find a worksheet with title {}.'
                                       .format(worksheet_name))

    def put_dataset(self, dataset: BenchmarkDataset):
        """Appends the dataset row in the bottom of the worksheet"""
        self.__update_value_keys(dataset.values)

        if not dataset.commit:
            raise GoogleWorksheetError("No commit in dataset " + dataset)

        if not dataset.device:
            raise GoogleWorksheetError("No device in dataset " + dataset)

        if not dataset.pipeline:
            raise GoogleWorksheetError("No pipeline in dataset " + dataset)

        if not dataset.branch and not dataset.tag:
            raise GoogleWorksheetError("No branch or tag in dataset " + dataset)

        if not dataset.date_time:
            raise GoogleWorksheetError("No date in dataset " + dataset)

        cell_values = []

        if dataset.branch:
            cell_values.append(format_title_with_hyperlink(
                dataset.branch,
                CI_PROJECT_URL + GITLAB_BRANCH_FOLDER + dataset.branch))
        else:
            cell_values.append(format_title_with_hyperlink(
                dataset.tag,
                CI_PROJECT_URL + GITLAB_TAG_FOLDER + dataset.tag))

        cell_values.append(format_title_with_hyperlink(
            dataset.commit[0:8],
            CI_PROJECT_URL + GITLAB_COMMIT_FOLDER + dataset.commit))

        cell_values.append(format_title_with_hyperlink(
            dataset.pipeline,
            CI_PROJECT_URL + GITLAB_PIPELINE_FOLDER + dataset.pipeline))

        cell_values.append(dataset.date_time.isoformat()[:-3] + 'Z')

        cell_values.append(dataset.device)
        if is_release:
            cell_values.append("Yes")

        for key in self.__get_value_keys()[len(WORKSHEET_FIXED_HEADERS_LIST):]:
            cell_values.append(dataset.values[key] if key in dataset.values else '')

        self.worksheet.append_table(cell_values, overwrite=False)

    def __get_value_keys(self):
        cells = self.worksheet.range('A1:Z1', 'cells')
        headers = []
        for cell in cells[0]:
            if not cell.value == '':
                headers.append(cell.value)
        fixed_headers = headers[0:len(WORKSHEET_FIXED_HEADERS_LIST)]
        if not WORKSHEET_FIXED_HEADERS_LIST == fixed_headers:
            raise GoogleWorksheetError("Unexpected fixed headers in worksheet", fixed_headers)
        return headers

    def __update_value_keys(self, value_dict: dict):
        raw_keys = Worksheet.__get_value_keys(self)
        if not dict:
            return
        new_var_keys = value_dict.keys()
        add_keys = []
        i = 1
        for key in new_var_keys:
            if not key in raw_keys:
                add_keys.append(
                    pygsheets.Cell(
                        pos=(1, len(raw_keys) + i),
                        val=key)
                )
                i += 1

        if add_keys:
            self.worksheet.update_values(cell_list=add_keys)


def format_title_with_hyperlink(title, url):
    return '=HYPERLINK("{}","{}")'.format(url, title)

class GoogleWorksheetError(Exception):
    pass

def check_inputs():
    if (not os.getenv('CI_COMMIT_SHA') or not os.getenv('CI_PIPELINE_ID')
            or not CI_PROJECT_URL
            or (not os.getenv('CI_COMMIT_BRANCH') and not os.getenv('CI_COMMIT_TAG'))):
        raise Exception("GitLab CI_ env vars are not set. The required ones:\n"
                        "CI_COMMIT_BRANCH or CI_COMMIT_TAG, CI_COMMIT_SHA, CI_PIPELINE_ID, "
                        "CI_PROJECT_URL")
    if not BENCHMARK_GSHEETS_SPREADSHEET_ID:
        raise Exception("BENCHMARK_GSHEETS_SPREADSHEET_ID env var is not set")

    if not BENCHMARK_GSHEETS_CREDENTIALS_FILE:
        raise Exception("BENCHMARK_GSHEETS_CREDENTIALS_FILE env var is not set")

def read_performance_benchmark():
    _file = open(BENCHMARK_PULL_DIR + "/benchmarkData.json", "r")

    parsed_data = json.load(_file)
    device_fingerprint = parsed_data["context"]["build"]["fingerprint"]
    benchmarks = parsed_data["benchmarks"]
    values = {}
    # dynamic load of key values
    # column name on spreadsheet will be a derived from test name
    for benchmark in benchmarks:
        min_key    = benchmark["name"] + "_min_ns"
        max_key    = benchmark["name"] + "_max_ns"
        median_key = benchmark["name"] + "_median_ns"

        values[min_key]    = benchmark["metrics"]["timeNs"]["minimum"]
        values[max_key]    = benchmark["metrics"]["timeNs"]["maximum"]
        values[median_key] = benchmark["metrics"]["timeNs"]["median"]

    return values, device_fingerprint

def read_memory_benchmark():
    # right now to read memory benchmark we can read metrics.txt
    # it contains average for minified lists AA in 10 runs with full easy
    # and minified easy list, this will change soon to include max and min
    # with that change we can skip producing metrics file and just read the csv
    # produced by the benchmarks

    _file = open("metrics.txt")
    lines = _file.readlines()
    values = {}
    for line in lines:
        splittedLine = line.split(" ")
        if (len(splittedLine) != 2):
            print("memory metrics file corrupt")
            os._exit(1)
        values[splittedLine[0]] = int(float(splittedLine[1]))

    return values

def main():

    start = time.time()

    print("loading benchmark results ...")
    performance_benchmark, device_fingerprint = read_performance_benchmark()
    memory_benchmark                          = read_memory_benchmark()

    # merge all
    benchmarks = memory_benchmark.copy()
    benchmarks.update(performance_benchmark)

    check_inputs()
    client = GSheetsClientProvider(BENCHMARK_GSHEETS_CREDENTIALS_FILE,
                                   BENCHMARK_GSHEETS_TOKEN_FILE_DIR).authenticate().get_client()
    print("Client authenticated.")

    sheet = Worksheet(client, BENCHMARK_GSHEETS_SPREADSHEET_ID, OUTPUT_WORKSHEET_NAME)
    print("Sheet is found and opened.")

    ds = BenchmarkDataset(device=device_fingerprint, values=benchmarks)

    sheet.put_dataset(ds)

    end = time.time()

    print("The dataset was successfully inserted into spreadsheet in {0:0.2f} seconds."
          .format(end - start))


if __name__ == '__main__':
    main()
