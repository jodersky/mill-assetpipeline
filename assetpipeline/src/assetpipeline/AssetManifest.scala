package assetpipeline

case class AssetManifest(
  name: String,
  hash: String,
  files: Seq[AssetFile]
) derives upickle.ReadWriter
