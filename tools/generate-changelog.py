#!/usr/bin/env python3

"""Automatic ABPWebView changelog generator.

Prerequisites:
Generate gitlab API personal token and save if in ~/gitlab-api-token.
See https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html#creating-a-personal-access-token
for instructions on how to generate. Save your JIRA credentials to ~/.netrc
These are not mandatory (the script will prompt for any missing tokens/credentials)
but makes life a lot easier.

"""

import argparse
import getpass
import os
import sys

# ------------------- DEPS -------------------#
try:
    import gitlab
except ImportError as e:
    print("Unable to import gitlab package. Use 'pip install python-gitlab'")
    sys.exit(1)

try:
    from jira import JIRA
except ImportError as e:
    print("Unable to import jira package. Use 'pip install jira'")
    sys.exit(1)


# ------------------- JIRA  -------------------#
def log_into_jira():
    options = {"server": "https://jira.eyeo.com"}

    netrc_file = os.path.join(os.path.expanduser("~"), ".netrc")
    if not os.path.exists(netrc_file):
        user = getpass.getpass("JIRA username: ")
        passwd = getpass.getpass("Password for " + user + " JIRA user: ")
        jira = JIRA(options=options, auth=(user, passwd))
    else:
        jira = JIRA(options=options)

    return jira


# ------------------- GITLAB -------------------#
def log_into_gitlab():
    token_file = os.path.join(os.path.expanduser("~"), "gitlab-api-token")
    if os.path.exists(token_file):
        with open(token_file, "r") as file:
            token = file.read().replace("\n", "")
    else:
        token = getpass.getpass("Get GITLAB API Token: ")
    gl = gitlab.Gitlab("https://gitlab.com", private_token=token)
    gl.auth()
    return gl


def get_project(gl):
    projects = gl.projects.list(search="libadblockplus-android")
    expected_ns = "eyeo/adblockplus/libadblockplus-android"
    proper_project = None
    for project in projects:
        if project.path_with_namespace == expected_ns:
            proper_project = project
            break
    return proper_project


# ------------------- MAIN -------------------#
def main(args):
    label = "ABP WebView {}".format(args.version)
    project = "DP"
    version = args.version

    jira = log_into_jira()
    gl = log_into_gitlab()
    proper_project = get_project(gl)

    issues = jira.search_issues(f'project={project} AND fixVersion="{label}"')
    mr_list = proper_project.mergerequests.list(
        state="merged", order_by="updated_at", sort="desc", per_page=100
    )

    changelog = {}
    for issue in issues:
        if issue.fields.issuetype.name not in ["Bug", "Task", "Story"]:
            print(
                f"Skipping {issue.key} from changelog, its a {issue.fields.issuetype.name}"
            )
            continue
        elif (
            "release" in issue.fields.summary.lower()
            and version.lower() in issue.fields.summary.lower()
        ):
            print(f"Skipping release task {issue.key}")
            continue

        # Get MR for issue
        mr_for_issue = [
            f"[{mr.reference}]({mr.web_url})" for mr in mr_list if issue.key in mr.title
        ]

        # Create an entry for the issue type in the changelog dict
        if issue.fields.issuetype.name not in changelog.keys():
            changelog[issue.fields.issuetype.name] = []

        # Example changelog line:
        # Enabled element hiding in iFrames as opt-in feature [!432](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/432)
        changelog_line = f'{issue.fields.summary} {", ".join(mr_for_issue)}'
        changelog[issue.fields.issuetype.name].append(changelog_line)

    # Get the release MR
    changelog["release"] = []
    release_mr_list = proper_project.mergerequests.list(
        state="opened", order_by="updated_at", sort="desc", per_page=100
    )

    for mr in release_mr_list:
        if (
            mr.target_branch == "master"
            and "release" in str(mr.title).lower()
            and version in mr.title
        ):
            mergedate = mr.opened_at.split("T")[0]
            # Example release line:
            ## [4.2.0] - 2021-01-15 - [!452](https://gitlab.com/eyeo/adblockplus/libadblockplus-android/-/merge_requests/452)
            changelog["release"].append(
                f"[{version}] - {mergedate} - [{mr.reference}]({mr.web_url})"
            )

    if len(changelog["release"]) != 1:
        print(f"WARNING: Unable to reliably find release MR, please check it!")
        print("\n".join(changelog["release"]))
    else:
        print(changelog["release"][0])

    if "Bug" in changelog.keys():
        print(f"\n### Fixed")
        for issue in changelog["Bug"]:
            print(f" - {issue}")

    print(f"\n### Changed/Added")
    if "Task" in changelog.keys():
        for issue in changelog["Task"]:
            print(f" - {issue}")
    if "Story" in changelog.keys():
        for issue in changelog["Story"]:
            print(f" - {issue}")


# -------------------  ENTRY POINT -------------------#
if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--version",
        type=str,
        required=True,
        help="Version of the APBWebView to generate changelog for, eg '4.2.0'. Needs to match the fixVersion in Jira",
    )
    args = parser.parse_args()
    main(args)
