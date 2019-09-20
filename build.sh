#!/bin/bash

set -ex

# Builds of tagged revisions are published to sonatype staging.

# Travis runs a build on new revisions and on new tags, so a tagged revision is built twice.
# Builds for a tag have TRAVIS_TAG defined, which we use for identifying tagged builds.

# sbt-dynver sets the version number from the tag
# sbt-travisci sets the Scala version from the travis job matrix

# When a new Scala version becomes available, a previously released version can be released using
# that new Scala version by creating a new tag containing the Scala version and the components
# to be released after hashes, e.g., v1.2.3#2.13.0-M3#plugin. The component can be either 'plugin'
# (to release only the plugin) or 'all' (to also release the library).

# For normal tags that are cross-built, we release on JDK 8 for Scala 2.x
isReleaseJob() {
  if [[ "$ADOPTOPENJDK" == "8" ]]; then
    true
  else
    false
  fi
}

# For tags that define a Scala version, we pick the jobs of one Scala version (2.13.x) to do the releases
isTagScalaReleaseJob() {
  if [[ "$ADOPTOPENJDK" == "8" && "$TRAVIS_SCALA_VERSION" == "2.12.10" ]]; then
    true
  else
    false
  fi
}

verPat="[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)?"
tagPat="^v$verPat(#$verPat#[a-z]+)?$"

if [[ "$TRAVIS_TAG" =~ $tagPat ]]; then
  releaseTask="ci-release"
  tagSuffix=$(echo $TRAVIS_TAG | sed s/[^#]*// | sed s/^#//)
  if [[ "$tagSuffix" == "" ]]; then
    if ! isReleaseJob; then
      echo "Not releasing on Java $ADOPTOPENJDK with Scala $TRAVIS_SCALA_VERSION"
      exit 0
    fi
    # projectPrefix is empty if we release both the plugin and the library
    if [[ " ${LIBRARY_PUBLISH_SCALA_VERSIONS[*]} " == *" $TRAVIS_SCALA_VERSION "* ]]; then
      projectPrefix=""
    else
      projectPrefix="plugin/"
    fi
  else
    if isTagScalaReleaseJob; then
      tagScalaVer=$(echo $tagSuffix | sed s/#.*//)
      component=$(echo $tagSuffix | sed s/[^#]*// | sed s/^#//)
      if [[ "$component" == "all" ]]; then
        projectPrefix=""
      elif [[ "$component" == "plugin" ]]; then
        projectPrefix="plugin/"
      else
        echo "When using a tag with a Scala version suffix, also specify the components to"
        echo "be released: 'plugin' (release only the plugin) or 'all' (plugin + library)"
        echo "Example: v1.2.3#2.13.2#plugin"
        exit 1
      fi
      setTagScalaVersion='set every scalaVersion := "'$tagScalaVer'"'
    else
      echo "The releases for Scala $tagSuffix are built by other jobs in the travis job matrix"
      exit 0
    fi
  fi
fi

# default is +publishSigned; we cross-build with travis jobs, not sbt's crossScalaVersions
export CI_RELEASE="${projectPrefix}publishSigned"
export CI_SNAPSHOT_RELEASE="${projectPrefix}publish"

# default is sonatypeBundleRelease, which closes and releases the staging repo
# see https://github.com/xerial/sbt-sonatype#commands
# for now, until we're confident in the new release scripts, just close the staging repo.
export CI_SONATYPE_RELEASE="; sonatypePrepare; sonatypeBundleUpload; sonatypeClose"

sbt "$setTagScalaVersion" clean test publishLocal $releaseTask
