package com.example.objectdetectionwithyolo.presentation.widget

import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.objectdetectionwithyolo.data.DetectionResult
import kotlin.math.min

@Composable
fun DetectionBoxes(
    detectionResults: List<DetectionResult>,
    previewSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val widthRatio = size.width / previewSize.width.toFloat()
        val heightRatio = size.height / previewSize.height.toFloat()
        val scaleFactor = min(widthRatio, heightRatio)

        for (detection in detectionResults) {
            val boundingBox = detection.boundingBox
            val left = boundingBox.left * scaleFactor
            val top = boundingBox.top * scaleFactor
            val right = boundingBox.right * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor

            // Çerçeve çiz
            drawRect(
                color = Color.Red,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )

            // Sınıf adı ve güveni
            drawRect(
                color = Color.Red.copy(alpha = 0.3f),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, 30.dp.toPx())
            )

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 12.sp.toPx()
                }
                canvas.nativeCanvas.drawText(
                    "${detection.className} ${(detection.confidence * 100).toInt()}%",
                    left + 5.dp.toPx(),
                    top + 20.dp.toPx(),
                    paint
                )
            }
        }
    }
}