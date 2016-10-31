#/bin/bash

# if the current commit has a tag named like v(\d+\.\d+\.\d+.*),
# and we're running on the right jdk/branch,
# echo the sbt commands that publish a release with the version derived from the tag
publishJdk=oraclejdk8
publishBranch=master
publishScalaVersion=2.12.0

unset tag version

# Exit without error when no (annotated) tag is found.
tag=$(git describe HEAD --exact-match 2>/dev/null || exit 0)

version=$(echo $tag | perl -pe 's/v(\d+\.\d+\.\d+.*)/$1/')

if [[ "$version"                !=  "" &&\
      "${TRAVIS_PULL_REQUEST}"  == "false" &&\
      "${JAVA_HOME}"            == "$(jdk_switcher home $publishJdk)" &&\
      "${TRAVIS_BRANCH}"        == "${publishBranch}" &&\
      "${TRAVIS_SCALA_VERSION}" == "${publishScalaVersion}" ]]; then
  echo \'"set every version := $version"\' publish-signed
fi