# sbt-ci-release

[![Build Status](https://travis-ci.org/olafurpg/sbt-ci-release.svg?branch=master)](https://travis-ci.org/olafurpg/sbt-ci-release)

This is an sbt plugin to help automate releases to Sonatype and Maven Central from Travis CI

- git tag pushes are published as regular releases to Maven Central
- merge into master commits are published as -SNAPSHOT with a unique version
  number for every commit

Beware that publishing from Travis CI requires you to expose Sonatype
credentials as secret environment variables in Travis CI jobs. However,
secret environment variables are not accessible during pull requests.

Let's get started!

<!-- TOC depthFrom:2 depthTo:2 -->

- [Sonatype](#sonatype)
- [sbt](#sbt)
- [GPG](#gpg)
- [Travis](#travis)
- [Git](#git)
- [FAQ](#faq)
- [Alternatives](#alternatives)

<!-- /TOC -->

## Sonatype

First, follow the instructions in
https://central.sonatype.org/pages/ossrh-guide.html to create a Sonatype account
and make sure you have publishing rights for a domain name. This is a one-time
setup per domain name.

If you don't have a domain name, you can use `com.github.<@your_username>`.
Here is a template you can use to write the Sonatype issue:

```
Title:
Publish rights for com.github.olafurpg
Description:
Hi, I would like to publish under the groupId: com.github.olafurpg.
It's my GitHub account https://github.com/olafurpg/
```

## sbt

Next, install this plugin in `project/plugins.sbt`

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.geirsson/sbt-ci-release/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.geirsson/sbt-ci-release)

```scala
// sbt 1 only, see FAQ for 0.13 support
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.1")
```

By installing `sbt-ci-release` the following sbt plugins are also brought in:

- [sbt-dynver](https://github.com/dwijnand/sbt-dynver): sets the version number
  based on your git history
- [sbt-pgp](https://github.com/sbt/sbt-pgp): to cryptographically sign the
  artifacts before publishing
- [sbt-sonatype](https://github.com/xerial/sbt-sonatype): to publish artifacts
  to Sonatype
- [sbt-git](https://github.com/sbt/sbt-git): to automatically populate `scmInfo`

Make sure `build.sbt` does not define any of the following settings

- `version`: handled by sbt-dynver
- `publishTo`: handled by sbt-ci-release
- `publishMavenStyle`: handled by sbt-ci-release
- `credentials`: handled by sbt-sonatype

Next, define publishing settings at the top of `build.sbt`

```scala
inThisBuild(List(
  organization := "com.geirsson",
  homepage := Some(url("https://github.com/scalameta/sbt-scalafmt")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "olafurpg",
      "Ólafur Páll Geirsson",
      "olafurpg@gmail.com",
      url("https://geirsson.com")
    )
  )
))
```

## GPG

Next, create a fresh gpg key that you will share with Travis CI and only use for
this project.

```
gpg --gen-key
```

- For real name, use "$PROJECT_NAME bot". For example, in Scalafmt I use
  "Scalafmt bot"
- For email, use your own email address
- For passphrase, generate a random password with a password manager

At the end you'll see output like this

```
pub   rsa2048 2018-06-10 [SC] [expires: 2020-06-09]
      $LONG_ID
uid                      $PROJECT_NAME bot <$EMAIL>
```

Take note of `$LONG_ID`, make sure to replace this ID from the code examples
below. The ID will look something like
`6E8ED79B03AD527F1B281169D28FC818985732D9`.

```bash
export LONG_ID=6E8ED79B03AD527F1B281169D28FC818985732D9
```

Next, copy the public gpg signature

```
# macOS
gpg --armor --export $LONG_ID | pbcopy
# linux
gpg --armor --export $LONG_ID | xclip
```

and post the signature to a keyserver: https://pgp.mit.edu/

![mit_pgp_key_server](https://user-images.githubusercontent.com/1408093/41208114-b8c89dce-6d1f-11e8-9280-9ab2b70bb0d7.jpg)

## Travis

Next, open the "Settings" panel for your project on Travis CI, for example
https://travis-ci.org/scalameta/sbt-scalafmt/settings.

Define four secret variables

![](https://user-images.githubusercontent.com/1408093/41207402-bbb3970a-6d15-11e8-8772-000cc194ee92.png)

- `PGP_PASSPHRASE`: The randomly generated password you used to create a fresh
  gpg key.
- `PGP_SECRET`: The base64 encoded secret of your private key that you can
  export from the command line like here below

```
# macOS
gpg --armor --export-secret-keys $LONG_ID | base64 | pbcopy
# Ubuntu (assuming GNU base64)
gpg --armor --export-secret-keys $LONG_ID | base64 -w0 | xclip
# FreeBSD (assuming BSD base64)
gpg --armor --export-secret-keys $LONG_ID | base64 | xclip
```

- `SONATYPE_PASSWORD`: The password you use to log into
  https://oss.sonatype.org/
- `SONATYPE_USERNAME`: The email you use to log into https://oss.sonatype.org/
- (optional) `CI_RELEASE`: the command to publish all artifacts for stable releases. Defaults to `+publishSigned` if not provided.
- (optional) `CI_SNAPSHOT_RELEASE`: the command to publish all artifacts for a SNAPSHOT releases. Defaults to `+publish` if not provided.

### .travis.yml

Next, update `.travis.yml` to trigger `ci-release` on successful merge into
master and on tag push. There are many ways to do this, but I recommend using
[Travis "build stages"](https://docs.travis-ci.com/user/build-stages/). It's not
necessary to use build stages but they make it easy to avoid publishing the
same module multiple times from parallel jobs.

- First, ensure that git tags are always fetched so that sbt-dynver can pick up
  the correct `version`

```yml
before_install:
  - git fetch --tags
```

- Next, define `test` and `release` build stages

```yml
stages:
  - name: test
  - name: release
    if: (branch = master AND type = push) OR (tag IS present)
```

- Lastly, define your build matrix with `ci-release` at the bottom, for example:

```yml
jobs:
  include:
    # stage="test" if no stage is specified
    - env: TEST="compile"
      script: sbt compile
    - env: TEST="formatting"
      script: ./bin/scalafmt --test
    # run ci-release only if previous stages passed
    - stage: release
      script: sbt ci-release
```

Notes:

- for a complete example of the Travis configuration, see the [.travis.yml in
  this repository](https://github.com/olafurpg/sbt-ci-release/blob/master/.travis.yml)
- if we use `after_success` instead of build stages, we would run `ci-release`
  after both `TEST="formatting"` and `TEST="compile"`. As long as you make sure
  you don't publish the same module multiple times, you can use any Travis
  configuration you like
- the `env: TEST="compile"` part is optional but it makes it easy to distinguish
  different jobs in the Travis UI

![build__48_-_olafurpg_sbt-ci-release_-_travis_ci](https://user-images.githubusercontent.com/1408093/41810442-a44ef526-76fe-11e8-92f4-4c4b61af4d38.jpg)

## Git

We're all set! Time to manually try out the new setup

- Open a PR and merge it to watch the CI release a -SNAPSHOT version
- Push a tag and watch the CI do a regular release

```
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

Note that the tag version MUST start with `v`.

It's is normal that something fails on the first attempt to publish from CI.
Even if it takes 10 attempts to get it right, it's still worth it because it's
so nice to have automatic CI releases. If all is correctly setup, your Travis
jobs page will look like this:

<img width="1058" alt="screen shot 2018-06-23 at 15 48 43" src="https://user-images.githubusercontent.com/1408093/41810386-b8c11198-76fd-11e8-8be1-54b84181e60d.png">

Enjoy 👌

## FAQ

### How do I disable publishing in certain projects?

Add the following to the project settings (works only in sbt 1)

```scala
skip in publish := true
```

### How do I publish cross-built projects?

Make sure that projects that compile against multiple Scala versions declare the `crossScalaVersions` setting in build.sbt, for example
```scala
lazy val core = project.settings(
  ...
  crossScalaVersions := List("2.12.6", "2.11.12")
)
```
The command `+publishSigned` (default value for `CI_RELEASE`) will then publish that project for both 2.11 and 2.12.

### Can I depend on Maven Central releases immediately?

Yes! As soon as CI "closes" the staging repository you can depend on those
artifacts with

```scala
resolvers += Resolver.sonatypeRepo("releases")
```

(optional) Use the
[coursier](https://github.com/coursier/coursier/#command-line) command line
interface to check if a release was successful without opening sbt

```bash
coursier fetch com.geirsson:scalafmt-cli_2.12:1.5.0 -r sonatype:releases
```

### How do I depend on the SNAPSHOT releases?

Add the following setting

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
```

(optional) With coursier you can do the same thing with `-r sonatype:snapshots`

```bash
coursier fetch com.geirsson:scalafmt-cli_2.12:1.5.0-SNAPSHOT -r sonatype:snapshots
```

### What about other CIs environments than Travis?

You can try
[sbt-release-early](https://github.com/scalacenter/sbt-release-early).

Alternatively, the source code for sbt-ci-release is only ~50 loc, see
[CiReleasePlugin.scala](https://github.com/olafurpg/sbt-ci-release/blob/master/plugin/src/main/scala/com/geirsson/CiReleasePlugin.scala).
You can copy-paste it to `project/` of your build and tweak the settings for
your environment.

### Does sbt-ci-release work for sbt 0.13?

Yes, but the plugin is not released for sbt 0.13. The plugin source code is a
single file which you can copy-paste into `project/CiReleasePlugin.scala` of
your 0.13 build. Make sure you also
`addSbtPlugin(sbt-dynver + sbt-sonatype + sbt-gpg + sbt-git)`.

### How do I publish sbt plugins?

You can publish sbt plugins to Maven Central like a normal library, no custom
setup required. It is not necessary to publish sbt plugins to Bintray.

### java.io.IOException: secret key ring doesn't start with secret key tag: tag 0xffffffff

- Make sure you exported the correct `LONG_ID` for the gpg key.
- Make sure the base64 exported secret GPG key is a single line (not line wrapped). If you use the GNU coreutils `base64` (default on Ubuntu), pass in the `-w0` flag to disable line wrapping.

### java.io.IOException: PUT operation to URL https://oss.sonatype.org/content/repositories/snapshots 400: Bad Request

This error happens when you publish a non-SNAPSHOT version to the snapshot
repository. If you pushed a tag, make sure the tag version number starts with
`v`. This error can happen if you tag with the version `0.1.0` instead of
`v0.1.0`.

### Failed: signature-staging, failureMessage:Missing Signature:

Make sure to upgrade to the latest sbt-ci-release, which could fix this error.
This failure can happen in case you push a git tag immediately after merging
a branch into master. A manual workaround is to log into https://oss.sonatype.org/
and drop the failing repository from the web UI.
Alternatively, you can run `sonatypeDrop <staging-repo-id>` from the sbt shell instead
of using the web UI.

## Alternatives

There exist great alternatives to sbt-ci-release that may work better for your
setup.

- [sbt-release-early](https://github.com/scalacenter/sbt-release-early):
  additionally supports publishing to Bintray and other CI environments than
  Travis.
- [sbt-rig](https://github.com/Verizon/sbt-rig): additionally supporting
  publishing code coverage reports, managing test dependencies and publishing
  docs.

The biggest difference between these and sbt-ci-release wrt to publishing is the
base64 encoded `PGP_SECRET` variable. I never managed to get the encrypted files
and openssl working.
