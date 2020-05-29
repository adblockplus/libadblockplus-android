#!/usr/bin/env python

"""Automatic ABPWebView release script.

Prerequsites:
Generate gitlab API personal token and save if in ~/gitlab-api-token.
See https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html#creating-a-personal-access-token
for instructions on how to generate.
Save you JIRA credentials to ~/.netrc
This are not mandatory (if not present the script will ba asking for
token/credentials) but makes life easier a lot. Script may log in to these
many times.

The script automates the ABPWebView release process by:
0.  Checking it release is ready to be worked on (all issues in release scope
    are done).
1.  Creating release-$VERSION branch from the current develop branch.
2.  Creating issue-$RELEASEISSUE-release-$VERSION, bumping version there and
    filing MR with bumping.
3.  Waiting till MR from 2 is accepted and pipelines passed and merging
    bumping into release-$VERSION.
4.  Creating release scope MR (MR from release-$VERSION to master)
5.  Waiting till MR from 4. is accepted and pipelines passed
6.  Taging release-$VERSION with the RC tag
7.  Creating RC testing JIRA issue
8.  Waiting till the testing issue is done.
a.  If new issues appeared it goes back to 7 incrementing RC number
9.  Merging MR from 4 to master
10. Tagging master with $VERSION tag (publishing to BINTRAY)
11. Merging release-$VERSION to develop
"""

import argparse
import getpass
import os
import shutil
import subprocess
import sys
import time

# ------------------- DEPS -------------------#
try:
    import gitlab
except ImportError as e:
    print "Gitlab package have to be installed. " \
          "Use 'pip install python-gitlab'"
    sys.exit(1)

try:
    from jira import JIRA
except ImportError as e:
    print "Jira package have to be installed. Use 'pip install jira'"
    sys.exit(1)


# TODO: Assure git is set up properly (config)?


# ------------------- JIRA  -------------------#
def log_into_jira():
    options = {"server": "https://jira.eyeo.com"}

    netrc_file = os.path.join(os.path.expanduser('~'), '.netrc')
    if not os.path.exists(netrc_file):
        user = getpass.getpass("JIRA username: ")
        passwd = getpass.getpass("Password for " + user + " JIRA user: ")
        jira = JIRA(options=options, auth=(user, passwd))
    else:
        jira = JIRA(options=options)

    return jira


def get_release_ticket_if_ready_to_release(jira, project, label):
    issues = jira.search_issues(
        'project={} AND fixVersion="{}"'.format(project, label))
    ready_ones = 0
    release_issue = None
    for issue in issues:
        if (str(issue.fields.status) == "Resolved" or
                str(issue.fields.status) == "In Testing"):
            print str(issue) + " ready."
            ready_ones = ready_ones + 1
        elif (("Release" in issue.fields.summary or "release" in
              issue.fields.summary) and args.version in issue.fields.summary):
            print str(issue) + " is about releasing itself."
            release_issue = issue
            ready_ones = ready_ones + 1
        else:
            print str(issue) + " not ready: " + str(issue.fields.status)

    if ready_ones == len(issues):
        print "All issues ready for releasing"
    else:
        raise Exception("Not all issues ready")

    return release_issue, len(issues)


# ------------------- GIT  -------------------#
def fetch(remote, branch, repo):
    command = "git fetch {} {}".format(remote, branch)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


def create_branch(branch, branch_point, repo):
    command = "git checkout -b {} {}".format(branch, branch_point)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


def switch_branch(branch, update_from_remote, repo):
    command = "git checkout {}".format(branch)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')
    if update_from_remote:
        command = "git pull origin {}".format(branch)
        result = subprocess.call(command, shell=True, cwd=repo)
        if result != 0:
            raise Exception(command + ' failed!')


def push_branch(remote, branch, repo):
    command = "git push {} --set-upstream {}:{}".format(remote, branch, branch)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


def create_branches(devel, master, repo, version, release_issue):
    fetch('origin', devel, repo)
    fetch('origin', master, repo)
    release_branch = 'release-{}'.format(version)
    create_branch(release_branch, 'origin/{}'.format(devel),
                  repo)
    push_branch('origin', release_branch, repo)
    release_issue_branch = "issue-{}-release-{}".format(release_issue, version)
    create_branch(release_issue_branch, release_branch, repo)
    push_branch('origin', release_issue_branch, repo)

    return release_issue_branch


def tag(branch, tag, repo):
    switch_branch(branch, True, repo)
    command = "git tag {}".format(tag)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')
    command = "git push origin {}".format(tag)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


def merge(src, target, repo):
    switch_branch(target, True, repo)
    fetch('origin', src, repo)
    command = "git merge {}".format(src)
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')
    push_branch('origin', target, repo)


def add_file_to_git(file, repo):
    command = "git add {}".format(str(file))
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


def commit(repo, message):
    command = "git add {}".format(str(file))
    result = subprocess.call(command, shell=True, cwd=repo)
    if result != 0:
        raise Exception(command + ' failed!')


# ------------------- UTILS  -------------------#
def bump_versions_and_commit(code, release_issue, release_issue_branch,
                             version):
    switch_branch(release_issue_branch, False, code)

    version_file_in = os.path.join(code, "build.gradle")
    version_file_out = os.path.join(code, "build.gradle.out")
    in_file = open(version_file_in, "rt")
    out_file = open(version_file_out, "wt")
    for line in in_file:
        if "moduleVersion = '" in line:
            elements = line.split("'")
            old_version = elements[1].strip()
            line = line.replace(old_version, version)
        out_file.write(line)
    in_file.close()
    out_file.close()
    shutil.move(version_file_out, version_file_in)

    version_file_in = os.path.join(code, os.path.join(
        "adblock-android-webviewapp", "build.gradle"))
    version_file_out = os.path.join(code, os.path.join(
        "adblock-android-webviewapp", "build.gradle.out"))
    in_file = open(version_file_in, "rt")
    out_file = open(version_file_out, "wt")
    for line in in_file:
        if "versionCode " in line:
            elements = line.split('versionCode ')
            old_version = elements[1].strip()
            next_version = int(old_version)
            next_version = next_version + 1
            line = line.replace(old_version, str(next_version))
        out_file.write(line)
    in_file.close()
    out_file.close()
    shutil.move(version_file_out, version_file_in)

    add_file_to_git(
        os.path.join("adblock-android-webviewapp", "build.gradle"), code)
    add_file_to_git("build.gradle", code)

    subprocess.call("git commit -m \"Issue {} - Release {}. Bump versions\"".
                    format(release_issue, version), shell=True, cwd=code)

    push_branch('origin', release_issue_branch, code)


# ------------------- GITLAB -------------------#
def log_into_gitlab():
    token_file = os.path.join(os.path.expanduser('~'), 'gitlab-api-token')
    if os.path.exists(token_file):
        with open(token_file, 'r') as file:
            token = file.read().replace('\n', '')
    else:
        token = getpass.getpass("Get GITLAB API Token: ")
    gl = gitlab.Gitlab('https://gitlab.com', private_token=token)
    gl.auth()
    return gl


def get_project(gl):
    projects = gl.projects.list(search="libadblockplus-android")
    expected_ns = "eyeo/adblockplus/libadblockplus-android"
    for project in projects:
        if project.path_with_namespace == expected_ns:
            proper_project = project
            break

    return proper_project


def get_mr(gl, source_branch, target_branch, title='', create=True):
    proper_project = get_project(gl)

    if proper_project is None:
        raise Exception('No proper gitlab project found')

    mr_list = proper_project.mergerequests.list(state="opened",
                                                source_branch=source_branch,
                                                target_branch=target_branch)
    if not mr_list:
        print "\nCreating '{}' MR".format(title)
        mr = proper_project.mergerequests.create({
            'source_project_id': str(proper_project.id),
            'target_project_id': str(proper_project.id),
            'source_branch': source_branch,
            'target_branch': target_branch,
            'title': title})
    else:
        mr = mr_list[0]
        print "\nMR is already there"

    try:
        mr.subscribe()
    except Exception as e:
        pass

    return mr


def check_pipelines_status(mr):
    done = 0
    pipelines = mr.pipelines()
    for pipeline in pipelines:
        if pipeline['status'] == "success" or \
           pipeline['status'] == "canceled" or \
           pipeline['status'] == "skipped":
            done = done + 1
        elif pipeline['status'] == "failed":
            raise Exception('Pipeline failed! Aborting.')

    return done == len(pipelines)


def check_jobs_status(jobs):
    done = 0
    for job in jobs:
        if job.status == "success" or \
           job.status == "canceled" or \
           job.status == "skipped":
            done = done + 1
        elif job.status == "failed":
            raise Exception('Job failed! Aborting.')

    return done == len(jobs)


# ------------------- MAIN -------------------#
def main(args):
    if args.stage not in ['start', 'post-bump', 'post-bump-review',
                          'post-scope-review', 'post-testing',
                          'post-master-merge']:
        print "Wrong stage. Use one of: start, post-bump, post-bump-review, " \
           "post-scope-review, post-testing, post-master-merge"
        sys.exit(1)

    label = "ABP WebView {}".format(args.version)
    project = "DP"
    if args.debug:
        devel = "release-script-test-develop{}".format(args.uid)
    else:
        devel = "develop"

    if args.debug:
        master = 'release-script-test-master{}'.format(args.uid)
    else:
        master = "master"

    version = args.version
    if args.debug:
        version = "test-version{}".format(args.uid)

    jira = log_into_jira()
    gl = log_into_gitlab()
    release_branch = 'release-{}'.format(version)

    try:
        if args.debug and args.rc_num == 1:
            create_branch(devel, 'develop', args.code)
            create_branch(master, 'master', args.code)
            push_branch('origin', master, args.code)
            push_branch('origin', devel, args.code)

        release_info = get_release_ticket_if_ready_to_release(jira, project,
                                                              label)
        release_issue = release_info[0]
        release_issues_no = release_info[1]

        if args.stage == "start":
            release_issue_branch = \
                create_branches(devel, master, args.code,
                                version, str(release_issue))
            bump_versions_and_commit(args.code, str(
                release_issue), release_issue_branch, args.version)

            args.stage = "post-bump"

        if args.stage == "post-bump":
            source_branch = 'issue-{}-release-{}'.format(
                release_issue, version)
            mr = get_mr(gl, source_branch, release_branch,
                        'Version bump for {} release'.format(version), True)

            print "\nWaiting for bump MR to be accepted"
            while (mr.approvals.get().approvals_left > 0):
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)

            print "\nWaiting for pipelines to finish"
            while (True):
                done = check_pipelines_status(mr)
                if done:
                    break
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)

            while (True):
                try:
                    mr.merge()
                    break
                except Exception as e:
                    sys.stdout.write(".")
                    sys.stdout.flush()
                    time.sleep(30)
                    pass

            args.stage = "post-bump-review"

        if args.stage == "post-bump-review":
            mr = get_mr(gl, release_branch, master,
                        'Release {} scope'.format(version), True)

            print "\nWaiting for the scope MR to be accepted"
            while (mr.approvals.get().approvals_left > 0):
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)

            args.stage = "post-scope-review"

        if args.stage == "post-scope-review":
            tag(release_branch, version + "-rc{}".format(args.rc_num),
                args.code)
            summary = 'WebView {} RC{}'.format(version, args.rc_num)
            rc_testing = jira.search_issues(
                'project={} AND summary ~ "{}" AND issuetype=Sub-task AND '
                'fixVersion="{}"'.format(project, summary, label))
            if not rc_testing:
                rc_testing = jira.create_issue(
                  project=project,
                  parent={'id': release_issue.id},
                  issuetype="Sub-task",
                  summary=summary,
                  customfield_11524={'value': "Undecided"},
                  description='Check the \
                               tickets that are part of this release for \
                               hints on what is important to test.')
                fix_versions = [{'name': label}]
                components = [{'name': 'ABPWebView'}]
                rc_testing.update(fields={'fixVersions': fix_versions,
                                          'components': components})
            else:
                rc_testing = rc_testing[0]

            jira.transition_issue(rc_testing, transition="In Testing")

            print "\nWaiting for testing to complete"
            while str(rc_testing.fields.status) != "Resolved":
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)
                rc_testing = jira.issue(str(rc_testing))
            print "Testing done."

            args.stage = "post-testing"

        if args.stage == "post-testing":
            ready_straight_away = True
            while (True):
                try:
                    # If testing resulted in a new issues in progress this will
                    # throw.
                    info = get_release_ticket_if_ready_to_release(
                        jira, project, label)
                    if (info[1] != release_issues_no and
                            # Testing issue filed in prev step itself is new.
                            info[1] != release_issues_no + 1):
                        print "Testing is done and all issues are ready but" \
                              " their number has changed.\nAssumtion is " \
                              "these are bugs filed while testing which " \
                              "had been fixed before testing finished.\n" \
                              "Sending for retesting."
                        ready_straight_away = False

                    if not ready_straight_away:
                        # Ready again. Move on:
                        args.stage = "post-scope-review"
                        args.rc_num = args.rc_num + 1
                        return main(args)

                    break
                except Exception as e:
                    if ready_straight_away is True:
                        print "Waiting for fixes for issues discovered in " \
                              "testing"
                    ready_straight_away = False
                    sys.stdout.write(".")
                    sys.stdout.flush()
                    time.sleep(30)
                    pass

            mr = get_mr(gl, release_branch, master)
            print "\nWaiting for pipeline to finish"
            while (True):
                done = check_pipelines_status(mr)
                if done:
                    break
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)

            print "\nMerging to master..."
            while (True):
                try:
                    mr.merge()
                    break
                except Exception as e:
                    sys.stdout.write(".")
                    sys.stdout.flush()
                    time.sleep(30)
                    pass

            args.stage = "post-master-merge"

        if args.stage == "post-master-merge":
            tag(master, version, args.code)

            project = get_project(gl)
            if not args.debug:
                pipeline = project.pipelines.create(
                    {'ref': version,
                     'variables': [{'key': 'DRY_RUN', 'value': 'false'}]})
            else:
                pipeline = project.pipelines.create(
                    {'ref': version,
                     'variables': [{'key': 'DRY_RUN', 'value': 'true'}]})

            print "\nWait for publish to bintray job to finish"
            while (True):
                done = check_jobs_status(pipeline.jobs.list())
                if done:
                    break
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(30)

            for job in pipeline.jobs.list():
                if job.status != "success":
                    raise Exception('One of bintray publish job has failed!')

            jira.add_comment(release_issue,
            "Publish BINTRAY job has finished successfully.\n "\
            "Bintray links:\n" \
            "https://bintray.com/adblockplus/maven/adblock-android/{}\n" \
            "https://bintray.com/adblockplus/maven/adblock-android-settings/{}\n" \
            "https://bintray.com/adblockplus/maven/adblock-android-webview/{}".
                format(version, version, version))
            merge(release_branch, devel, args.code)

            print "\nRELEASED!"

    except Exception as e:
        print "Release failed at {} stage with '{}' error".format(
            args.stage, e)


# -------------------  ENTRY POINT -------------------#
if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--version', type=str, required=True,
                        help='Version of the APBWebView to be released. E.g. '
                             '3.18. It\'s mandatory.')
    parser.add_argument('--code', type=str, required=True, help='Path to the '
                        'libadblockplus-android git repo to operate on. '
                        'It\'s mandatory.')
    parser.add_argument('--stage', type=str, help='Allows to renter the script'
                        ' from an arbitraty stage i.e. allows to break and get'
                        ' back to it later or do some steps manually. Stages '
                        'are: start, post-bump, post-bump-review, '
                        'post-scope-review, post-testing,post-master-merge.'
                        ' It\'s optional. If not provided start stage is '
                        'assumed', default="start")
    parser.add_argument('--debug', type=bool,
                        help='An optional flag. It tells the script it\'s in '
                             'debug mode so it uses some artificial/testing '
                             'branches/versions.', default=False)
    parser.add_argument('--rc_num', type=int, help='Release candidate number. '
                        'Allows to force Release candidate numbering not to '
                        'start from 1. It\'s optional.', default=1)
    parser.add_argument('--uid', type=str, help='Unique id to add to branch/'
                        ' tag names when debugging so the script does not fail'
                        ' on creating branch/tag. Relevant only if debug arg '
                        'is true. It\'s optional.', default=time.time())
    args = parser.parse_args()
    main(args)
