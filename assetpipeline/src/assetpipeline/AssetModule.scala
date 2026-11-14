package assetpipeline

import mill.*

/** A module which produces a content-addressable asset.
  */
trait AssetModule extends Module:

  /** This asset's files: either a directory, whose files all belong to the
    * asset, or a single file. Files that refer to each other by relative path
    * must be in the same directory tree. Defaults to `src` in the module's
    * directory.
    */
  def assetSource: T[PathRef] = Task.Source("src")

  /** The asset's name, which is the first segment of the paths its files are
    * stored under. Defaults to the module's name, e.g. `web` for a module
    * `frontend.web`.
    */
  def assetName: T[String] = Task:
    moduleSegments.parts.lastOption.getOrElse(
      Task.fail("The root module has no name; override assetName to give the asset one")
    )

  /** Compresses the files of [[assetSource]] with Brotli, by running the
    * `brotli` command-line tool, which must be on the `PATH`.
    *
    * For each file `<path>` the returned directory holds `<path>.br`, but only
    * if the compressed file is smaller than the original. Files in formats
    * that are already compressed, such as images and fonts, are skipped
    * without running `brotli` on them.
    */
  def assetCompress: T[PathRef] = Task:
    val root = assetSource().path
    for (rel, file) <- AssetModule.listFiles(root) do
      if !AssetModule.isCompressed(rel) then
        val out = Task.dest / AssetModule.encoded(rel, "br")
        os.makeDir.all(out / os.up)
        try
          os.proc("brotli", "--quality=11", "--force", "--no-copy-stat", s"--output=$out", "--", file)
            .call(stdout = os.Inherit, stderr = os.Inherit)
        catch
          case e: java.io.IOException =>
            Task.fail(s"Could not run brotli (is it installed and on the PATH?): ${e.getMessage}")
        if os.size(out) >= os.size(file) then os.remove(out)
    PathRef(Task.dest)

  /** Lists every file in [[assetSource]] and computes one hash over all of
    * them.
    *
    * Files are sorted by their path relative to the source directory, so the
    * hash does not depend on the order in which the filesystem lists
    * directories. Each file's path and contents both go into the hash, so
    * renaming a file changes the hash as well. Compressed versions from
    * [[assetCompress]] are recorded per file, but are not part of the hash.
    */
  def assetManifest: T[AssetManifest] = Task:
    val root = assetSource().path
    val sorted = AssetModule.listFiles(root)
    val compressed = assetCompress().path

    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val buffer = new Array[Byte](8192)
    val longBytes = new Array[Byte](8)
    def putLong(n: Long): Unit =
      longBytes(0) = (n >>> 56).toByte
      longBytes(1) = (n >>> 48).toByte
      longBytes(2) = (n >>> 40).toByte
      longBytes(3) = (n >>> 32).toByte
      longBytes(4) = (n >>> 24).toByte
      longBytes(5) = (n >>> 16).toByte
      longBytes(6) = (n >>> 8).toByte
      longBytes(7) = n.toByte
      digest.update(longBytes)

    for (rel, file) <- sorted do
      // length-prefix both the path and the contents, so that the boundary
      // between one file and the next is unambiguous
      val pathBytes = rel.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      putLong(pathBytes.length)
      digest.update(pathBytes)
      putLong(os.size(file))
      val in = os.read.inputStream(file)
      try
        var n = in.read(buffer)
        while n != -1 do
          digest.update(buffer, 0, n)
          n = in.read(buffer)
      finally in.close()

    val hash = digest.digest().map(b => f"${b & 0xff}%02x").mkString

    AssetManifest(
      name = assetName(),
      sha256 = hash,
      files = sorted.map: (rel, _) =>
        val encodings =
          if os.exists(compressed / AssetModule.encoded(rel, "br")) then Seq("br")
          else Seq()
        AssetFile(rel, encodings)
    )

object AssetModule:

  /** The files of the asset source `root`, with their paths relative to the
    * asset's root, sorted by relative path. A directory's files are relative
    * to the directory; a single file is at the asset's root, under its own
    * name.
    */
  private[assetpipeline] def listFiles(root: os.Path): Seq[(os.SubPath, os.Path)] =
    if os.isFile(root) then Seq(os.sub / root.last -> root)
    else if os.isDir(root) then
      os.walk(root)
        .filter(os.isFile(_))
        .map(file => file.subRelativeTo(root) -> file)
        .sortBy(_._1.toString)
    else throw new java.io.FileNotFoundException(s"Asset source does not exist: $root")

  /** The path of the version of `rel` stored with the given content encoding. */
  private def encoded(rel: os.SubPath, encoding: String): os.SubPath =
    rel / os.up / s"${rel.last}.$encoding"

  /** File extensions of formats whose contents are already compressed, so
    * compressing them again gains (almost) nothing.
    */
  private val compressedExtensions: Set[String] = Set(
    "png", "jpg", "jpeg", "gif", "webp", "avif", "heic",
    "woff", "woff2",
    "mp3", "mp4", "m4a", "ogg", "opus", "webm",
    "gz", "br", "zst", "zip", "jar", "7z", "xz", "bz2"
  )

  private def isCompressed(rel: os.SubPath): Boolean =
    compressedExtensions.contains(rel.ext.toLowerCase)
