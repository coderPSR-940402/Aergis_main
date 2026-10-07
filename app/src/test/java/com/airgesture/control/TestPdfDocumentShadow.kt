package com.airgesture.control

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Robolectric 4.17 does not implement PdfDocument's native backend. Keep the
 * production report drawing on real native Canvas/Bitmap, and substitute only
 * the platform PDF container. This produces an inspectable layout fixture.
 * Android's native PDF output still requires a device smoke test.
 */
@Implements(PdfDocument::class)
class TestPdfDocumentShadow {
    private val bitmaps = mutableListOf<Bitmap>()
    private var current: PdfDocument.Page? = null
    private var closed = false
    @Implementation fun __constructor__() = Unit
    @Implementation fun startPage(info: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed && current == null)
        val bitmap = Bitmap.createBitmap(info.pageWidth, info.pageHeight, Bitmap.Config.ARGB_8888)
        bitmaps.add(bitmap)
        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(Canvas(bitmap), info).also { current = it }
    }
    @Implementation fun finishPage(page: PdfDocument.Page) {
        check(current === page && !closed)
        current = null
    }
    @Implementation fun close() { check(current == null); closed = true }
    @Implementation fun writeTo(output: OutputStream) {
        check(!closed && current == null)
        val objects = mutableListOf<ByteArray>()
        fun bytes(value: String) = value.toByteArray(Charsets.ISO_8859_1)
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"))
        val kids = bitmaps.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
        objects.add(bytes("<< /Type /Pages /Count ${bitmaps.size} /Kids [$kids] >>"))
        for ((index, bitmap) in bitmaps.withIndex()) {
            val contentId = 4 + index * 3; val imageId = 5 + index * 3
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${bitmap.width} ${bitmap.height}] /Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>"))
            val commands = "q ${bitmap.width} 0 0 ${bitmap.height} 0 0 cm /Im0 Do Q"
            objects.add(bytes("<< /Length ${commands.length} >>\nstream\n$commands\nendstream"))
            val jpeg = ByteArrayOutputStream().apply { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, this)) }.toByteArray()
            val stream = ByteArrayOutputStream()
            stream.write(bytes("<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n"))
            stream.write(jpeg); stream.write(bytes("\nendstream"))
            objects.add(stream.toByteArray())
        }
        val document = ByteArrayOutputStream()
        document.write(bytes("%PDF-1.4\n%âãÏÓ\n"))
        val offsets = mutableListOf(0)
        for ((index, body) in objects.withIndex()) {
            offsets.add(document.size())
            document.write(bytes("${index + 1} 0 obj\n")); document.write(body); document.write(bytes("\nendobj\n"))
        }
        val xref = document.size()
        document.write(bytes("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n"))
        offsets.drop(1).forEach { document.write(bytes(String.format(java.util.Locale.US, "%010d 00000 n \n", it))) }
        document.write(bytes("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n"))
        document.writeTo(output)
    }
}
