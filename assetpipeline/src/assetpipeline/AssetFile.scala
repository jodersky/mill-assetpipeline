package assetpipeline

import mill.api.JsonFormatters.subPathRW

/** A file in an asset.
  *
  * @param path
  *   the file's path relative to the asset's root
  * @param encodings
  *   the content encodings, such as `br`, in which a precompressed version of
  *   the file exists. Each one is stored next to the file, with the encoding
  *   as an extra extension (`app.js` → `app.js.br`).
  */
case class AssetFile(
  path: os.SubPath,
  encodings: Seq[String]
) derives upickle.ReadWriter
