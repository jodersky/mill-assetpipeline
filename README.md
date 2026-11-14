# mill-assetpipeline

A [Mill](https://mill-build.org) plugin for managing static assets such as
stylesheets, scripts, fonts and images, under content-addressable paths.

A content-addressable path can be efficiently cached as an immutable resource in
web applications.

## Overview

When a browser receives an HTML page, it will then issue additional requests for
any resources declared as URLs in the page.

```html
<link rel="stylesheet" href="/style.css">
```

In many web applications, such resources are public and static. This means that
they never change between requests, and would greatly benefit from caching. In
the example above, the `style.css` would rarely ever change.

`mill-assetpipeline` makes perfect caching possible by storing assets in a
content-addressable way. In the example, this mill plugin would process the
stylesheet at build time and provide utilities for your application to generate
HTML of the following form:

```html
<link rel="stylesheet" href="/assets/style/523cf8a9.../style.css">
```

Your server would then serve such resources with the header `Cache-Control:
public, max-age=31536000, immutable`, and browsers would only ever request them
once. However, in the event an asset is updated, its URL would change and
browsers would immediately get the new version.

In addition to content-addressable storage, this plugin also compresses assets
with common encodings supported by browsers, for example the highly efficient
brotli compression.

## Usage

Add the plugin to your `build.mill`, then:

- define an asset producer module for each asset
- make your application an asset consumer

```scala
//| mvnDeps:
//| - io.crashbox::mill-assetpipeline::0.1.0
package build

import mill.*
import assetpipeline.*

object styles extends AssetModule  // files in styles/src
object scripts extends AssetModule // files in scripts/src

object app extends AssetScalaModule:
  def scalaVersion = "3.9.0"
  def assetModules = Seq(styles, scripts)
```

The files of each asset are compressed, made content-addressable, and put on the
classpath of the application. The application also gets generated constants with
the content-addressable paths, which it can use to generate HTML:

```scala
// generated
package assets

object styles:
  final val `css/main.css` = "/assets/styles/<hash>/css/main.css"
  final val `fonts/body.woff2` = "/assets/styles/<hash>/fonts/body.woff2"
```

An application server could then be written as follows, for example with
[keg](https://github.com/jodersky/keg):

```scala
@keg.get("/")
def index() =
  val html =
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <link rel="stylesheet" href="${assets.styles.`css/main.css`}">
       |  <script src="${assets.scripts.`main.js`}" defer></script>
       |</head>
       |<body><h1>Hello, world!</h1></body>
       |</html>
       |""".stripMargin
  keg.Response(html, headers = Seq("Content-Type" -> "text/html; charset=UTF-8"))

// Serves the assets from the class path. If the client accepts Brotli,
// the `.br` file is sent instead, with `Content-Encoding: br`.
@keg.get("/assets/*")
def assetFiles(req: keg.Request) =
  keg.Static.fromRequest(
    req,
    "/assets",
    headers = Seq(
      "Cache-Control" -> "public, max-age=31536000, immutable"
    )
  )

@main def main() =
  keg.Server(keg.routes()).start()
```

### Assets

An asset is a directory, or a single file, and gets one hash over all of its
files. Files that refer to each other by relative path, such as a stylesheet
and the fonts it loads, must be in the same asset. Keep files that change
independently in separate assets, so that changing one does not change the
paths of the others.

| Setting       | Default                     |
| ------------- | --------------------------- |
| `assetSource` | the `src` folder of the module |
| `assetName`   | the module's name           |

`assetSource` can be overridden with any task that returns a directory or a
file, such as the output of a bundler, or with a single source file:

```scala
object scripts extends AssetModule:
  def assetSource = Task.Source("main.js")
```

### Compression

Each file is also compressed with [Brotli](https://github.com/google/brotli),
and stored next to the original as `<file>.br`, when that makes it smaller.
The `brotli` command-line tool must be installed and on the `PATH`.

### Consumers

| Setting        | Default    |                                           |
| -------------- | ---------- | ----------------------------------------- |
| `assetModules` | none       | the assets to include                     |
| `assetPath`    | `"assets"` | the directory all assets are placed under |
| `assetPackage` | `"assets"` | the package of the generated constants    |

`AssetScalaModule` is for Scala modules. For anything else, extend
`AssetConsumerModule`, whose `allAssets` task is a directory holding all the
assets.

## Development

```sh
./mill assetpipeline.test          # run tests
./mill assetpipeline.publishLocal  # publish to ~/.ivy2/local
```
