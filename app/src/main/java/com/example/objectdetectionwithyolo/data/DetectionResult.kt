package com.example.objectdetectionwithyolo.data

import android.graphics.Rect

data class DetectionResult(
    val boundingBox: Rect,
    val className: String,
    val confidence: Float
)
