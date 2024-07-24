# sbt-ci-release

![CI](https://github.com/sbt/sbt-ci-release/workflows/CI/badge.svg)

This is an sbt plugin to help automate releases to Sonatype and Maven Central
from GitHub Actions.

- git tag pushes are published as regular releases to Maven Central
- merge into main commits are published as -SNAPSHOT with a unique version
  number for every commit

Beware that publishing from GitHub Actions requires you to expose Sonatype
credentials as secret environment variables in GitHub Actions jobs. However,
secret environment variables are not accessible during pull requests.

Let's get started!

<!-- TOC depthFrom:2 depthTo:3 -->

- [Sonatype](#sonatype)
  - [Optional: create user tokens](#optional-create-user-tokens)
- [sbt](#sbt)
- [GPG](#gpg)
- [Secrets](#secrets)
  - [GitHub Actions](#github-actions)
  - [Travis](#travis)
- [Git](#git)
- [FAQ](#faq)
  - [How do I disable publishing in certain projects?](#how-do-i-disable-publishing-in-certain-projects)
  - [How do I publish cross-built projects?](#how-do-i-publish-cross-built-projects)
  - [How do I publish cross-built Scala.js projects?](#how-do-i-publish-cross-built-scalajs-projects)
  - [Can I depend on Maven Central releases immediately?](#can-i-depend-on-maven-central-releases-immediately)
  - [How do I depend on the SNAPSHOT releases?](#how-do-i-depend-on-the-snapshot-releases)
  - [What about other CIs environments than Travis?](#what-about-other-cis-environments-than-travis)
  - [Does sbt-ci-release work for sbt 0.13?](#does-sbt-ci-release-work-for-sbt-013)
  - [How do I publish sbt plugins?](#how-do-i-publish-sbt-plugins)
  - [java.io.IOException: secret key ring doesn't start with secret key tag: tag 0xffffffff](#javaioioexception-secret-key-ring-doesnt-start-with-secret-key-tag-tag-0xffffffff)
  - [java.io.IOException: PUT operation to URL https://s01.oss.sonatype.org/content/repositories/snapshots 400: Bad Request](#javaioioexception-put-operation-to-url-httpss01osssonatypeorgcontentrepositoriessnapshots-400-bad-request)
  - [java.io.IOException: Access to URL was refused by the server: Unauthorized](#javaioioexception-access-to-url-was-refused-by-the-server-unauthorized)
  - [Failed: signature-staging, failureMessage:Missing Signature:](#failed-signature-staging-failuremessagemissing-signature)
  - [How do I create release notes? Can they be automatically generated?](#how-do-i-create-release-notes-can-they-be-automatically-generated)
- [Adopters](#adopters)
- [Alternatives](#alternatives)

<!-- /TOC -->

## Sonatype

First, follow the instructions in
https://central.sonatype.org/pages/ossrh-guide.html to create a Sonatype account
and make sure you have publishing rights for a domain name. This is a one-time
setup per domain name.

If you don't have a domain name, you can use `io.github.<@your_username>`. Here
is a template you can use to write the Sonatype issue:

```
Title:
Publish rights for io.github.sbt
Description:
Hi, I would like to publish under the groupId: io.github.sbt.
It's my GitHub account https://github.com/sbt/
```

### Optional: create user tokens

If you prefer not to save your actual username and password in GitHub Actions
settings below, generate your user tokens:

- login to https://s01.oss.sonatype.org/ (or https://oss.sonatype.org/ if your
  Sonatype account was created before February 2021),
- click your username in the top right, then profiles,
- in the tab that was opened, click on the top left dropdown, and select "User
  Token",
- click "Access User Token", and save the name and password parts of the token
  somewhere safe.

## sbt

Next, install this plugin in `project/plugins.sbt`

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.sbt/sbt-ci-release/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.sbt/sbt-ci-release)

```scala
// sbt 1 only, see FAQ for 0.13 support
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "<version>")
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
  organization := "com.github.sbt",
  homepage := Some(url("https://github.com/sbt/sbt-ci-release")),
  // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
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

If your sonatype account is new (created after Feb 2021), then the default server
location inherited from the the `sbt-sonatype` plugin will not work, and you should
also include the following overrides in your publishing settings
```scala
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
```

## GPG

Next, create a fresh gpg key that you will share with GitHub Actions and only
use for this project.

```
gpg --gen-key
```

- For real name, you can use anything. For example, this repository uses
  "sbt-ci-release bot".
- For email, use your own email address
- For passphrase, generate a random password with a password manager. This will be the
  environment variables PGP_PASSPHRASE in your CI. Take note of `PGP_PASSPHRASE`.

At the end you'll see output like this

```
pub   rsa2048 2018-06-10 [SC] [expires: 2020-06-09]
      $LONG_ID
uid                      $PROJECT_NAME bot <$EMAIL>
```

Take note of `$LONG_ID`, make sure to replace this ID from the code examples
below. The ID will look something like (a)
`6E8ED79B03AD527F1B281169D28FC818985732D9` or something like (b)
`A4C9 75D9 9C05 E4C7 2163 4BBD ACA8 EB32 0BFE FE2C` (in which case delete the
spaces to make it look like (a)). A command like this one should do:

```bash
# On UNIX
LONG_ID=6E8ED79B03AD527F1B281169D28FC818985732D9

# On Windows
set LONG_ID=6E8ED79B03AD527F1B281169D28FC818985732D9
```

Next, copy the public gpg signature

```
# macOS
gpg --armor --export $LONG_ID | pbcopy
# linux
gpg --armor --export $LONG_ID | xclip
# Windows
gpg --armor --export %LONG_ID%
```

and post the signature to a keyserver: https://keyserver.ubuntu.com/

1. Select "Submit Key"
2. Paste in the exported public key
3. Click on "Submit Public Key".

![Ubuntu Keyserver](https://i.imgur.com/njvOpmq.png)

or run:

```bash
# macOS
gpg --keyserver hkp://keyserver.ubuntu.com --send-key $LONG_ID && \
 gpg --keyserver hkp://pgp.mit.edu --send-key $LONG_ID && \
 gpg --keyserver hkp://pool.sks-keyservers.net --send-key $LONG_ID
# linux
gpg --keyserver hkp://keyserver.ubuntu.com --send-key $LONG_ID && \
 gpg --keyserver hkp://pgp.mit.edu --send-key $LONG_ID && \
 gpg --keyserver hkp://pool.sks-keyservers.net --send-key $LONG_ID
# Windows
gpg --keyserver hkp://keyserver.ubuntu.com --send-key %LONG_ID% && \
 gpg --keyserver hkp://pgp.mit.edu --send-key %LONG_ID% && \
 gpg --keyserver hkp://pool.sks-keyservers.net --send-key %LONG_ID%
```

## Secrets

Next, you'll need to declare four environment variables in your CI. Open the
settings page for your CI provider.

- **GitHub Actions**:

  Select `Settings -> Secrets and variables -> Actions -> New repository secret` to add each of the
  required variables as shown in the next figure:

  ![github-secrets-2021-01-27](https://user-images.githubusercontent.com/933058/111891685-e0e12400-89b1-11eb-929c-24f5b48b24de.png)

  When complete, your secrets settings should look like the following:

  ![github-env-vars-2021-01-27](https://user-images.githubusercontent.com/933058/111891688-ec344f80-89b1-11eb-9037-9899e5183ad9.png)

- **Travis CI**:

  Make sure that "Build pushed branches" setting is enabled.

Add the following secrets:

- `PGP_PASSPHRASE`: The randomly generated password you used to create a fresh
  gpg key. **For Travis Only:** If the password contains bash special
  characters, make sure to escape it by wrapping it in single quotes
  `'my?pa$$word'`, see
  [Travis Environment Variables](https://docs.travis-ci.com/user/environment-variables/#defining-variables-in-repository-settings).
- `PGP_SECRET`: The base64 encoded secret of your private key that you can
  export from the command line like here below

```
# macOS
gpg --armor --export-secret-keys $LONG_ID | base64 | pbcopy
# Ubuntu (assuming GNU base64)
gpg --armor --export-secret-keys $LONG_ID | base64 -w0 | xclip
# Arch
gpg --armor --export-secret-keys $LONG_ID | base64 | sed -z 's;\n;;g' | xclip -selection clipboard -i
# FreeBSD (assuming BSD base64)
gpg --armor --export-secret-keys $LONG_ID | base64 | xclip
# Windows
gpg --armor --export-secret-keys %LONG_ID% | openssl base64
```

*If you try to display the base64 encoded string in the terminal, some shells (like zsh or fish)
may include an additional % character at the end, to mark the end of content which was not terminated by a newline character. This does not indicate a problem.*
- `SONATYPE_PASSWORD`: The password you use to log into
  https://s01.oss.sonatype.org/ (or https://oss.sonatype.org/ if your Sonatype
  account was created before February 2021). Alternatively, the password part of
  the user token if you generated one above. **For Travis Only:** If the
  password contains bash special characters, make sure to escape it by wrapping
  it in single quotes `'my?pa$$word'`, see
  [Travis Environment Variables](https://docs.travis-ci.com/user/environment-variables/#defining-variables-in-repository-settings).
- `SONATYPE_USERNAME`: The username you use to log into
  https://s01.oss.sonatype.org/ (or https://oss.sonatype.org/ if your Sonatype
  account was created before 2021). Alternatively, the name part of the user
  token if you generated one above.
- (optional) `CI_RELEASE`: the command to publish all artifacts for stable
  releases. Defaults to `+publishSigned` if not provided.
- (optional) `CI_SNAPSHOT_RELEASE`: the command to publish all artifacts for a
  SNAPSHOT releases. Defaults to `+publish` if not provided.
- (optional) `CI_SONATYPE_RELEASE`: the command called to close and promote the
  staged repository. Useful when, for example, also dealing with non-sbt
  projects to change to `sonatypeReleaseAll`. Defaults to
  `sonatypeBundleRelease` if not provided.

### GitHub Actions

Run the following command to install the same
[`release.yml`](https://github.com/sbt/sbt-ci-release/blob/main/.github/workflows/release.yml)
script that is used to release this repository.

```sh
mkdir -p .github/workflows && \
  curl -L https://raw.githubusercontent.com/sbt/sbt-ci-release/main/.github/workflows/release.yml > .github/workflows/release.yml
```

Commit the file and merge into main.

### Travis

> Skip this step if you're using GitHub Actions. > Unless you have a specific
> reason to use Travis, we recommend using GitHub Actions because > it's easier
> to configure.

Next, update `.travis.yml` to trigger `ci-release` on successful merge into
master and on tag push. There are many ways to do this, but I recommend using
[Travis "build stages"](https://docs.travis-ci.com/user/build-stages/). It's not
necessary to use build stages but they make it easy to avoid publishing the same
module multiple times from parallel jobs.

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
    if: ((branch = master AND type = push) OR (tag IS present)) AND NOT fork
```

- Lastly, define your build matrix with `ci-release` at the bottom, for example:

```yml
jobs:
  include:
    # stage="test" if no stage is specified
    - name: compile
      script: sbt compile
    - name: formatting
      script: ./bin/scalafmt --test
    # run ci-release only if previous stages passed
    - stage: release
      script: sbt ci-release
```

Notes:

- if we use `after_success` instead of build stages, we would run `ci-release`
  after both `formatting` and `compile`. As long as you make sure you don't
  publish the same module multiple times, you can use any Travis configuration
  you like
- the `name: compile` part is optional but it makes it easy to distinguish
  different jobs in the Travis UI

![build__48_-_sbt-ci-release_-_travis_ci](https://user-images.githubusercontent.com/1408093/41810442-a44ef526-76fe-11e8-92f4-4c4b61af4d38.jpg)

## Git

We're all set! Time to manually try out the new setup

- Open a PR and merge it to watch the CI release a -SNAPSHOT version
- Push a tag and watch the CI do a regular release

```
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

Note that the tag version MUST start with `v`.

It is normal that something fails on the first attempt to publish from CI. Even
if it takes 10 attempts to get it right, it's still worth it because it's so
nice to have automatic CI releases. If all is correctly setup, your Travis jobs
page will look like this:

<img width="1058" alt="screen shot 2018-06-23 at 15 48 43" src="https://user-images.githubusercontent.com/1408093/41810386-b8c11198-76fd-11e8-8be1-54b84181e60d.png">

Enjoy 👌

## FAQ

### How do I disable publishing in certain projects?

Add the following to the project settings (works only in sbt 1)

```scala
publish / skip := true
```

### How do I publish cross-built projects?

Make sure that projects that compile against multiple Scala versions declare the
`crossScalaVersions` setting in build.sbt, for example

```scala
lazy val core = project.settings(
  ...
  crossScalaVersions := List("2.13.1", "2.12.10", "2.11.12")
)
```

The command `+publishSigned` (default value for `CI_RELEASE`) will then publish
that project for 2.11, 2.12 and 2.13.

### How do I publish cross-built Scala.js projects?

If you publish for multiple Scala.js versions, start by disabling publishing of
the non-JS projects when the `SCALAJS_VERSION` environment variable is defined.

```diff
// build.sbt
+ val customScalaJSVersion = Option(System.getenv("SCALAJS_VERSION"))
lazy val myLibrary = crossProject(JSPlatform, JVMPlatform)
  .settings(
    // ...
  )
+  .jvmSettings(
+    skip.in(publish) := customScalaJSVersion.isDefined
+  )
```

Next, add an additional `ci-release` step in your CI config to publish the
custom Scala.js version

```diff
// .travis.yml
  sbt ci-release
+ SCALAJS_VERSION=0.6.31 sbt ci-release
```

### Can I depend on Maven Central releases immediately?

Yes! As soon as CI "closes" the staging repository you can depend on those
artifacts with

```scala
resolvers ++= Resolver.sonatypeOssRepos("staging")
```

Use this instead if your Sonatype account was created after February 2021

```scala
resolvers +=
  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"
```

(optional) Use the
[coursier](https://github.com/coursier/coursier/#command-line) command line
interface to check if a release was successful without opening sbt

```bash
coursier fetch com.github.sbt:scalafmt-cli_2.12:1.5.0 -r sonatype:public
```

Use `-r https://s01.oss.sonatype.org/content/repositories/releases` instead if your Sonatype account was created after February 2021.

### How do I depend on the SNAPSHOT releases?

Add the following setting

```scala
resolvers ++= Opts.resolver.sonatypeOssSnapshots
```

or

```scala
resolvers +=
  "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
```

if your Sonatype account was created after February 2021.

(optional) With coursier you can do the same thing with `-r sonatype:snapshots`

```bash
coursier fetch com.github.sbt:scalafmt-cli_2.12:1.5.0-SNAPSHOT -r sonatype:snapshots
```

Use `-r https://s01.oss.sonatype.org/content/repositories/snapshots` instead if your Sonatype account was created after February 2021.

### What about other CIs environments than Travis?

- This project uses a github workflow,
  [which you can review here](https://github.com/sbt/sbt-ci-release/tree/master/.github/workflows)
- [CircleCI](https://circleci.com/) is supported

You can try
[sbt-release-early](https://github.com/scalacenter/sbt-release-early).

Alternatively, the source code for sbt-ci-release is only ~50 loc, see
[CiReleasePlugin.scala](https://github.com/sbt/sbt-ci-release/blob/main/plugin/src/main/scala/com/geirsson/CiReleasePlugin.scala).
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
- Make sure the base64 exported secret GPG key is a single line (not line
  wrapped). If you use the GNU coreutils `base64` (default on Ubuntu), pass in
  the `-w0` flag to disable line wrapping.

### java.io.IOException: PUT operation to URL https://s01.oss.sonatype.org/content/repositories/snapshots 400: Bad Request

This error happens when you publish a non-SNAPSHOT version to the snapshot
repository. If you pushed a tag, make sure the tag version number starts with
`v`. This error can happen if you tag with the version `0.1.0` instead of
`v0.1.0`.

### java.io.IOException: Access to URL was refused by the server: Unauthorized

Make sure that `SONATYPE_PASSWORD` uses proper escaping if it contains special
characters as documented on
[Travis Environment Variables](https://docs.travis-ci.com/user/environment-variables/#defining-variables-in-repository-settings).

### Failed: signature-staging, failureMessage:Missing Signature:

Make sure to upgrade to the latest sbt-ci-release, which could fix this error.
This failure can happen in case you push a git tag immediately after merging a
branch into master. A manual workaround is to log into
https://s01.oss.sonatype.org/ (or https://oss.sonatype.org/ if your Sonatype
account was created before February 2021) and drop the failing repository from
the web UI. Alternatively, you can run `sonatypeDrop <staging-repo-id>` from the
sbt shell instead of using the web UI.

### How do I create release notes? Can they be automatically generated?

We think that the creation of release notes should not be fully automated
because commit messages don't often communicate the end user impact well. You
can use [Release Drafter](https://github.com/apps/release-drafter) github app
(or the Github Action) to help you craft release notes.

### My build suddenly fails with [info] gpg: no default secret key: No secret key

Make sure your pgp key did not expire. If it expired you have to change the
expiry date and reupload it. See:
https://github.com/sbt/sbt-ci-release#gpg.

## Adopters

Below is a non-exhaustive list of projects using sbt-ci-release. Don't see your
project?
[Add it in a PR!](https://github.com/sbt/sbt-ci-release/edit/main/readme.md)

- [AlexITC/scala-js-chrome](https://github.com/AlexITC/scala-js-chrome)
- [almond-sh/almond](https://github.com/almond-sh/almond/)
- [an-tex/sc8s](https://github.com/an-tex/sc8s)
- [bitcoin-s/bitcoin-s](https://github.com/bitcoin-s/bitcoin-s)
- [coursier/coursier](https://github.com/coursier/coursier/)
- [ekrich/sconfig](https://github.com/ekrich/sconfig/)
- [fd4s/fs2-kafka](https://github.com/fd4s/fs2-kafka)
- [fd4s/vulcan](https://github.com/fd4s/vulcan)
- [fthomas/refined](https://github.com/fthomas/refined/)
- [kubukoz/error-control](https://github.com/kubukoz/error-control/)
- [kubukoz/flawless](https://github.com/kubukoz/flawless/)
- [kubukoz/slick-effect](https://github.com/kubukoz/slick-effect/)
- [kubukoz/sup](https://github.com/kubukoz/sup/)
- [kubukoz/vivalidi](https://github.com/kubukoz/vivalidi/)
- [m2-oss/calypso](https://github.com/m2-oss/calypso)
- [snapi](https://github.com/raw-labs/snapi)
- [scalameta/metaconfig](https://github.com/scalameta/metaconfig/)
- [scala/sbt-scala-module](https://github.com/scala/sbt-scala-module)
- [scalacenter/scalafix](https://github.com/scalacenter/scalafix)
- [scalameta/metabrowse](https://github.com/scalameta/metabrowse/)
- [scalameta/metals](https://github.com/scalameta/metals/)
- [scalameta/scalafmt](https://github.com/scalameta/scalafmt/)
- [softwaremill/sttp](https://github.com/softwaremill/sttp)
- [softwaremill/tapir](https://github.com/softwaremill/tapir)
- [typelevel/paiges](https://github.com/typelevel/paiges/)
- [upb-uc4/hlf-api](https://github.com/upb-uc4/hlf-api)
- [vigoo/clipp](https://github.com/vigoo/clipp/)
- [vigoo/desert](https://github.com/vigoo/desert/)
- [vigoo/prox](https://github.com/vigoo/prox/)
- [vlovgr/ciris](https://github.com/vlovgr/ciris)
- [wiringbits/sjs-material-ui-facade](https://github.com/wiringbits/sjs-material-ui-facade)
- [zhongl/config-annotation](https://github.com/zhongl/config-annotation)
- [zhongl/akka-stream-netty](https://github.com/zhongl/akka-stream-netty)
- [zhongl/akka-stream-oauth2](https://github.com/zhongl/akka-stream-oauth2)

## Alternatives

There exist great alternatives to sbt-ci-release that may work better for your
setup.

- [sbt-ci-release-early](https://github.com/ShiftLeftSecurity/sbt-ci-release-early):
  very similar to sbt-ci-release except doesn't use SNAPSHOT versions.
- [sbt-release-early](https://github.com/scalacenter/sbt-release-early):
  additionally supports publishing to Bintray and other CI environments than
  Travis.
- [sbt-rig](https://github.com/Verizon/sbt-rig): additionally supporting
  publishing code coverage reports, managing test dependencies and publishing
  docs.

The biggest difference between these and sbt-ci-release wrt to publishing is the
base64 encoded `PGP_SECRET` variable. I never managed to get the encrypted files
and openssl working.
