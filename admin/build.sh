#!/bin/bash

set -e

# Builds of tagged revisions are published to sonatype staging.

# Travis runs a build on new revisions and on new tags, so a tagged revision is built twice.
# Builds for a tag have TRAVIS_TAG defined, which we use for identifying tagged builds.
# Checking the local git clone would not work because git on travis does not fetch tags.

# The version number to be published is extracted from the tag, e.g., v1.2.3 publishes
# version 1.2.3 using all Scala versions in build.sbt's `crossScalaVersions`.

# When a new, binary incompatible Scala version becomes available, a previously released version
# can be released using that new Scala version by creating a new tag containing the Scala version
# after a hash, e.g., v1.2.3#2.13.0-M1.

verPat="[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)?"
tagPat="^v$verPat(#$verPat)?$"

scalaVer=$(echo $TRAVIS_TAG | sed s/[^#]*// | sed s/^#//)
if [[ $scalaVer == "2.11"* ]]; then
  publishJdk="openjdk6"
else
  publishJdk="oraclejdk8"
fi

if [ "$TRAVIS_JDK_VERSION" == "$publishJdk" ] && [[ "$TRAVIS_TAG" =~ $tagPat ]]; then

  echo "Going to release from tag $TRAVIS_TAG!"

  tagVer=$(echo $TRAVIS_TAG | sed s/#.*// | sed s/^v//)
  publishVersion='set every version := "'$tagVer'"'

  if [ "$scalaVer" != "" ]; then
    publishScalaVersion='set every crossScalaVersions := Seq("'$scalaVer'")'
  fi
  
  extraTarget="+publish-signed"

  cat admin/gpg.sbt >> project/plugins.sbt
  admin/decrypt.sh sensitive.sbt
  (cd admin/ && ./decrypt.sh secring.asc)
fi

sbt "$publishVersion" "$publishScalaVersion" clean update +test +publishLocal $extraTarget