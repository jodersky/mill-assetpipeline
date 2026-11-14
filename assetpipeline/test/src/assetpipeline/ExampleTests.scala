package assetpipeline

import mill.testkit.IntegrationTester
import utest.*

/** Runs the build in `resources/example`, which has two assets:
  *
  *   - `scripts`, a single JavaScript file
  *   - `styles`, a stylesheet which loads a font by relative path
  *
  * and a Scala module `app` which uses both.
  */
object ExampleTests extends TestSuite:

  def tester() = IntegrationTester(
    daemonMode = false,
    workspaceSourcePath = os.Path(sys.env("MILL_TEST_RESOURCE_DIR")) / "example",
    millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
  )

  def manifest(tester: IntegrationTester, module: String): AssetManifest =
    val res = tester.eval(("show", s"$module.assetManifest"))
    assert(res.isSuccess)
    upickle.read[AssetManifest](res.out)

  def tests = Tests:

    test("manifests"):
      val t = tester()

      val scripts = manifest(t, "scripts")
      assert(scripts.name == "scripts")
      assert(scripts.sha256.matches("[0-9a-f]{64}"))
      assert(scripts.files == Seq(AssetFile(os.sub / "main.js", Seq("br"))))

      // the font is not compressed, because of its extension
      val styles = manifest(t, "styles")
      assert(styles.name == "styles")
      assert(
        styles.files == Seq(
          AssetFile(os.sub / "css" / "main.css", Seq("br")),
          AssetFile(os.sub / "fonts" / "body.woff2", Seq())
        )
      )

    test("app finds the assets, and relative references between them, on its class path"):
      val t = tester()
      val scripts = manifest(t, "scripts").sha256
      val styles = manifest(t, "styles").sha256
      val res = t.eval("app.run")
      assert(res.isSuccess)
      val lines = res.out.linesIterator.toSeq
      assert(lines.contains(s"/assets/scripts/$scripts/main.js found=true br=true"))
      assert(lines.contains(s"/assets/styles/$styles/css/main.css found=true br=true"))
      assert(lines.contains(s"/assets/styles/$styles/fonts/body.woff2 found=true br=false"))
      assert(lines.contains(s"../fonts/body.woff2 -> /assets/styles/$styles/fonts/body.woff2 found=true"))

    test("changing a file changes only its own asset's hash"):
      val t = tester()
      val scriptsBefore = manifest(t, "scripts").sha256
      val stylesBefore = manifest(t, "styles").sha256

      t.modifyFile(t.workspacePath / "styles" / "src" / "css" / "main.css", _ + "\nh1 { margin-top: 2rem; }\n")

      val scriptsAfter = manifest(t, "scripts").sha256
      val stylesAfter = manifest(t, "styles").sha256
      assert(scriptsAfter == scriptsBefore)
      assert(stylesAfter != stylesBefore)

      val res = t.eval("app.run")
      assert(res.isSuccess)
      assert(res.out.contains(s"/assets/styles/$stylesAfter/css/main.css found=true br=true"))
