package assetpipeline

case class AssetManifest(
  name: String,
  sha256: String,
  files: Seq[AssetFile]
) derives upickle.ReadWriter
