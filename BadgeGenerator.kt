// BadgeGenerator.kt
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.client.j2se.MatrixToImageConfig
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Paths

data class Template(
    val badgeWidth: Int,
    val badgeHeight: Int,
    val background: String,
    val nameFontSize: Int,
    val titleFontSize: Int,
    val companyFontSize: Int,
    val qrPosition: List<Int>,
    val textPosition: List<Int>,
    val textColor: String
)

fun defaultTemplate() = Template(
    badgeWidth = 600,
    badgeHeight = 400,
    background = "#FFFFFF",
    nameFontSize = 40,
    titleFontSize = 24,
    companyFontSize = 28,
    qrPosition = listOf(380, 80),
    textPosition = listOf(50, 100),
    textColor = "#000000"
)

fun loadTemplate(path: String): Template {
    val json = String(Files.readAllBytes(Paths.get(path)))
    return Gson().fromJson(json, Template::class.java)
}

fun hexToRgb(hex: String): Int {
    return hex.replace("#", "").toInt(16)
}

fun createQR(data: String, size: Int, color: String, logoPath: String?): BufferedImage {
    val writer = QRCodeWriter()
    val matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
    val config = MatrixToImageConfig(hexToRgb(color), 0xFFFFFFFF)
    var qr = MatrixToImageWriter.toBufferedImage(matrix, config)
    if (logoPath != null) {
        val logo = ImageIO.read(File(logoPath))
        val logoSize = size / 4
        val scaled = logo.getScaledInstance(logoSize, logoSize, Image.SCALE_SMOOTH)
        val logoImg = BufferedImage(logoSize, logoSize, BufferedImage.TYPE_INT_ARGB)
        val g = logoImg.createGraphics()
        g.drawImage(scaled, 0, 0, null)
        g.dispose()
        val x = (size - logoSize) / 2
        val y = (size - logoSize) / 2
        val g2 = qr.createGraphics()
        g2.drawImage(logoImg, x, y, null)
        g2.dispose()
    }
    return qr
}

fun generateBadge(data: String, name: String, title: String?, company: String?,
                  output: String, size: Int, color: String, logoPath: String?,
                  template: Template) {
    val qr = createQR(data, size, color, logoPath)
    val badge = BufferedImage(template.badgeWidth, template.badgeHeight, BufferedImage.TYPE_INT_RGB)
    val g = badge.createGraphics()
    g.color = Color.decode(template.background)
    g.fillRect(0, 0, template.badgeWidth, template.badgeHeight)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color.decode(template.textColor)
    val nameFont = Font("Arial", Font.PLAIN, template.nameFontSize)
    val titleFont = Font("Arial", Font.PLAIN, template.titleFontSize)
    val companyFont = Font("Arial", Font.PLAIN, template.companyFontSize)
    val tx = template.textPosition[0]
    val ty = template.textPosition[1]
    g.font = nameFont
    g.drawString(name, tx.toFloat(), ty.toFloat())
    if (!title.isNullOrEmpty()) {
        g.font = titleFont
        g.drawString(title, tx.toFloat(), (ty + template.nameFontSize + 10).toFloat())
    }
    if (!company.isNullOrEmpty()) {
        g.font = companyFont
        g.drawString(company, tx.toFloat(), (ty + template.nameFontSize + template.titleFontSize + 20).toFloat())
    }
    val qx = template.qrPosition[0]
    val qy = template.qrPosition[1]
    g.drawImage(qr, qx, qy, null)
    g.dispose()
    val ext = output.substringAfterLast('.')
    if (ext.lowercase() == "png") {
        ImageIO.write(badge, "png", File(output))
    } else {
        ImageIO.write(badge, "png", File(output)) // fallback
    }
    println("Badge saved to $output")
}

fun main(args: Array<String>) {
    var data: String? = null
    var name: String? = null
    var title: String? = null
    var company: String? = null
    var output = "badge.png"
    var size = 200
    var color = "#000000"
    var logoPath: String? = null
    var templatePath: String? = null
    var batchPath: String? = null
    var outputDir = "."

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--data" -> data = args[++i]
            "--name" -> name = args[++i]
            "--title" -> title = args[++i]
            "--company" -> company = args[++i]
            "--output" -> output = args[++i]
            "--size" -> size = args[++i].toInt()
            "--color" -> color = args[++i]
            "--logo" -> logoPath = args[++i]
            "--template" -> templatePath = args[++i]
            "--batch" -> batchPath = args[++i]
            "--output-dir" -> outputDir = args[++i]
        }
        i++
    }

    val template = if (templatePath != null) loadTemplate(templatePath) else defaultTemplate()

    if (batchPath != null) {
        // CSV parsing (simple split)
        val lines = File(batchPath).readLines()
        val header = lines.first().split(",")
        for (line in lines.drop(1)) {
            val row = line.split(",")
            val map = header.zip(row).toMap()
            val d = map["data"] ?: ""
            val n = map["name"] ?: d
            val t = map["title"] ?: ""
            val c = map["company"] ?: ""
            val out = map["output"] ?: "$n.png"
            val outPath = "$outputDir/$out"
            generateBadge(d, n, t, c, outPath, size, color, logoPath, template)
        }
    } else {
        if (data == null) { System.err.println("Error: --data required"); System.exit(1) }
        if (name == null) name = data
        generateBadge(data!!, name!!, title, company, output, size, color, logoPath, template)
    }
}
