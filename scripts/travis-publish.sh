#!/bin/bash

# Example setting to use at command line for testing:
# export TRAVIS_SCALA_VERSION=2.10.5;export TRAVIS_PULL_REQUEST="false";export TRAVIS_BRANCH="master"

SBT="sbt ++${TRAVIS_SCALA_VERSION}"
GPG_DIR=deploy

if [[ "${TRAVIS_PULL_REQUEST}" == "false" &&
      "${TRAVIS_BRANCH}" == "master" &&
      ! $(cat version.sbt) =~ "-SNAPSHOT"
]]; then
  openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $GPG_DIR/pubring.gpg.enc -out $GPG_DIR/pubring.gpg -d
  openssl aes-256-cbc -pass pass:$ENCRYPTION_PASSWORD -in $GPG_DIR/secring.gpg.enc -out $GPG_DIR/secring.gpg -d
  PUBLISH=publish-signed
else
  PUBLISH=publishLocal
fi

${SBT} ${PUBLISH}
