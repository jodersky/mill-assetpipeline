package assetpipeline

import mill.*

/** A module which uses the assets of other modules. */
trait AssetConsumerModule extends Module:

  /** The assets this module uses. */
  def assetModules: Seq[AssetModule] = Seq()

  /** The manifests of all [[assetModules]], in the order they are listed. */
  def allAssetManifests: T[Seq[AssetManifest]] = Task:
    Task.traverse(assetModules)(_.assetManifest)()

  /** The directory, relative to the root of [[allAssets]], under which all
    * assets are placed.
    */
  def assetPath: T[String] = "assets"

  /** A directory holding the files of all [[assetModules]], each at
    * `<assetPath>/<name>/<hash>/<file>`, together with their compressed
    * versions (`<file>.br`).
    *
    * Fails if two assets have the same name.
    */
  def allAssets: T[PathRef] = Task:
    val manifests = allAssetManifests()
    val sources = Task.traverse(assetModules)(_.assetSource)()
    val compressed = Task.traverse(assetModules)(_.assetCompress)()

    val duplicates = manifests.groupBy(_.name).collect:
      case (name, ms) if ms.size > 1 => name
    if duplicates.nonEmpty then
      Task.fail(s"Asset name(s) used by more than one asset module: ${duplicates.toSeq.sorted.mkString(", ")}")

    val base = Task.dest / os.SubPath(assetPath())
    for ((manifest, source), compressed) <- manifests.zip(sources).zip(compressed) do
      val target = base / manifest.name / manifest.sha256
      for (rel, file) <- AssetModule.listFiles(source.path) do
        os.copy(file, target / rel, createFolders = true)
      os.copy(compressed.path, target, mergeFolders = true)

    PathRef(Task.dest)
