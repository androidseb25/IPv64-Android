package de.rpicloud.ipv64net.helper

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val supportedImageFormats = listOf(
        ImageFormat.YUV_420_888,
        ImageFormat.YUV_422_888,
        ImageFormat.YUV_444_888,
    )

    // Nur auf dem einen Analyse-Thread genutzt; Hints einmalig setzen (decodeWithState behält sie)
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            if (image.format !in supportedImageFormats) return
            val plane = image.planes[0]
            val bytes = plane.buffer.toLuminance(plane.rowStride, image.width, image.height)
            val source = PlanarYUVLuminanceSource(
                bytes,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            val binaryBmp = BinaryBitmap(HybridBinarizer(source))
            try {
                onQrCodeScanned(reader.decodeWithState(binaryBmp).text)
            } catch (e: Exception) {
                // Kein QR-Code im Bild (ReaderException) oder ZXing-Fehler: pro Frame kein Logging, nie den Analyse-Thread beenden
            }
        }
    }

    // Y-Ebene zeilenweise kopieren, da rowStride größer als die Bildbreite sein kann (Padding)
    private fun ByteBuffer.toLuminance(rowStride: Int, width: Int, height: Int): ByteArray {
        val data = ByteArray(width * height)
        for (row in 0 until height) {
            position(row * rowStride)
            get(data, row * width, width)
        }
        return data
    }
}
