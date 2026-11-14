import java.net.URI

/** Prints each generated asset reference and whether the file and its Brotli
  * version are on the class path. Then follows every `url(...)` in the
  * stylesheet, relative to the stylesheet's own path, and prints whether the
  * file it points to is on the class path too.
  */
@main def main(): Unit =
  def exists(path: String) = getClass.getResource(path) != null

  for path <- Seq(assets.scripts.`main.js`, assets.styles.`css/main.css`, assets.styles.`fonts/body.woff2`) do
    println(s"$path found=${exists(path)} br=${exists(s"$path.br")}")

  val css = assets.styles.`css/main.css`
  val text = String(getClass.getResourceAsStream(css).readAllBytes(), "UTF-8")
  for url <- """url\("([^"]+)"\)""".r.findAllMatchIn(text).map(_.group(1)) do
    val target = URI(css).resolve(url).toString
    println(s"$url -> $target found=${exists(target)}")
