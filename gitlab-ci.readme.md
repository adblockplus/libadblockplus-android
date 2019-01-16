# Manually provisioning a server with the gitlab-runner

These are rudimentary instructions for setting up a gitlab-runner service on a linux server.

Preconditions:

- Ubuntu 18.04 server (other distros may work as well)
- Git
- Docker

The GitLab project must be configured by a maintainer with the following settings:

Settings --> CI / CD -->  Runners --> Disable "Shared Runners" (note the token for later use)

## Register the gitlab-runner at gitlab.com

Create a directory to store the registration configuration. This will be used to persist the gitlab-runner configuration received during registration across restarts of the container. The directory will be mounted to `/etc/gitlab-runner` inside the container.

    mkdir -p /opt/eyeo/libadblockplus-android-gitlab-runner/etc_gitlab-runner

We use the official gitlab-runner image for the registration, for the sake of simplicity. However such schema may not work, in this case please register the corresponding gitlab-runner from the actual runner environment. Run the registration in a one-off docker container:

```
docker run --rm -t -i \
  -v /opt/eyeo/libadblockplus-android-gitlab-runner/etc_gitlab-runner:/etc/gitlab-runner \
  gitlab/gitlab-runner \
  register --non-interactive \
  --url "https://gitlab.com/" \
  --registration-token "$REGISTRATION_TOKEN" \
  --description "libadblockplus-android gitlab-runner on the AA DP team build server" \
  --executor "shell" \
  --tag-list "ubuntu,gitlab-runner" \
  --run-untagged \
  --locked="false"
```

The following parameter should be replaced with the registration token from the GitLab project CI/CD settings:

    --registration-token "$REGISTRATION_TOKEN"

The `--tag-list` parameter is used to identify or describe the environment within the runtime of the gitlab-runner.

## Preparing the gitlab-runner image integration

Clone the gitlab:eyeo/adblockplus/libadblockplus-android.git into this directory.

    cd /opt/eyeo/libadblockplus-android-gitlab-runner
    git clone git@gitlab.com:eyeo/adblockplus/libadblockplus-android.git

## Build custom gitlab-runner image and run it

Switch to the directory of the gitlab-runner Dockerfile:

    cd libadblockplus-android/docker

Build the new image based on the generated configuration

    docker build --rm -t eyeo-aa-dp/libadblockplus-android_gitlab-runner .

Finally, run the gitlab-runner container. In order to provide more flexible disk capacity required for building the projects, we mount a volume `eyeo-aa-dp-libadblockplus-android-vol` into `/opt/ci` where the gitlab-runner will create its working directories:

```
docker run --rm -t -i \
  -v /opt/eyeo/libadblockplus-android-gitlab-runner/etc_gitlab-runner:/etc/gitlab-runner \
  -v eyeo-aa-dp-libadblockplus-android-vol:/opt/ci \
   eyeo-aa-dp/libadblockplus-android_gitlab-runner
```

# Future improvements

- Automatically start the service after reboot
- Automate this provisioning (using https://gitlab.com/eyeo/devops/ansible-role-gitlab-runner)
- Restrict the gitlab runner to use a limited set of resources
- Investigate triggers for this CI (for example run it just for MR: see https://gitlab.com/gitlab-org/gitlab-ce/issues/25099)
- Investigate use of `docker` executor instead of `shell` executor