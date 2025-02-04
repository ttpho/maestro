package maestro.android

import com.android.ide.common.xml.AndroidManifestParser
import com.android.ide.common.xml.ManifestData
import com.android.tools.apk.analyzer.Archive
import com.android.tools.apk.analyzer.Archives
import com.android.tools.apk.analyzer.BinaryXmlParser
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.xml.parsers.ParserConfigurationException

fun File.asManifest(): ManifestData {
    return Archives.open(toPath()).use { context -> getManifestData(context.archive) }
}

@Throws(IOException::class)
private fun getManifestData(archive: Archive): ManifestData {
    val manifestPath = archive.contentRoot.resolve("AndroidManifest.xml")
    val manifestBytes = BinaryXmlParser.decodeXml(
        "AndroidManifest.xml", Files.readAllBytes(manifestPath)
    )
    return try {
        AndroidManifestParser.parse(ByteArrayInputStream(manifestBytes))
    } catch (e: ParserConfigurationException) {
        throw IOException(e)
    } catch (e: SAXException) {
        throw IOException(e)
    }
}