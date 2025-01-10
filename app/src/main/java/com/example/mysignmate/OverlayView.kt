
package com.example.mysignmate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var poseResults: PoseLandmarkerResult? = null
    private var handResults: HandLandmarkerResult? = null

    private var posePointPaint = Paint()
    private var poseLinePaint = Paint()
    private var handPointPaint = Paint()
    private var handLinePaint = Paint()

    // Initialize separate paints for left and right hands
    private var leftHandPointPaint = Paint()
    private var rightHandPointPaint = Paint()

    private var rectangle = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var overlayEnabled: Boolean = true

    init {
        initPaints()
    }

    fun clear() {
        poseResults = null
        handResults = null
        posePointPaint.reset()
        poseLinePaint.reset()
        handPointPaint.reset()
        handLinePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        poseLinePaint.color = Color.BLUE
        poseLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        poseLinePaint.style = Paint.Style.STROKE
//        poseLinePaint.alpha = 85

        posePointPaint.color = Color.GREEN
        posePointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        posePointPaint.style = Paint.Style.FILL
//        posePointPaint.alpha = 85

        handLinePaint.color = Color.YELLOW
        handLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        handLinePaint.style = Paint.Style.STROKE
//        handLinePaint.alpha = 85

        handPointPaint.color = Color.RED
        handPointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        handPointPaint.style = Paint.Style.FILL
//        handPointPaint.alpha = 85

        leftHandPointPaint.color = Color.CYAN
        leftHandPointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        leftHandPointPaint.style = Paint.Style.FILL
//        leftHandPointPaint.alpha = 85

        rightHandPointPaint.color = Color.MAGENTA
        rightHandPointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        rightHandPointPaint.style = Paint.Style.FILL
//        rightHandPointPaint.alpha = 85

        rectangle.color = Color.RED
        rectangle.strokeWidth = 10f
        rectangle.style = Paint.Style.STROKE
        rectangle.alpha = 75
    }

    // Check handedness
    private fun isLeftHand(handLandmarkerResult: HandLandmarkerResult, index: Int): Boolean {
        val handednessList = handLandmarkerResult.handedness()

        // Ensure the index is within bounds and the handedness list is not empty
        if (index >= 0 && index < handednessList.size) {
            val categories = handednessList[index]
            if (categories.isNotEmpty()) {
                return categories[0].categoryName().equals("Left", ignoreCase = true)
            }
        }

        // Default to false if no handedness information is available
        return false
    }

    fun setOverlayEnabled(enabled: Boolean) {
        overlayEnabled = enabled
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if(!overlayEnabled) return

        // Draw a bounding box in the center of the view
        val left = width * 0.35f
        val top = height * 0.25f
        val right = width * 0.65f
        val bottom = height * 0.45f
        canvas.drawRect(left, top, right, bottom, rectangle)

        poseResults?.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                for(normalizedLandmark in landmark) {
                    canvas.drawCircle(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        CIRCLE_RADIUS,
                        posePointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        landmark[it!!.start()].x() * imageWidth * scaleFactor,
                        landmark[it.start()].y() * imageHeight * scaleFactor,
                        landmark[it.end()].x() * imageWidth * scaleFactor,
                        landmark[it.end()].y() * imageHeight * scaleFactor,
                        poseLinePaint
                    )
                }
            }
        }
        handResults?.let { handLandmarkerResult ->
            for (index in handLandmarkerResult.landmarks().indices) {
                val landmark = handLandmarkerResult.landmarks()[index]
                val paint = if (isLeftHand(handLandmarkerResult, index)) leftHandPointPaint else rightHandPointPaint

                for (normalizedLandmark in landmark) {
                    canvas.drawCircle(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        CIRCLE_RADIUS,
                        paint
                    )
                }

                HandLandmarker.HAND_CONNECTIONS.forEach {
                    canvas.drawLine(
                        landmark[it!!.start()].x() * imageWidth * scaleFactor,
                        landmark[it.start()].y() * imageHeight * scaleFactor,
                        landmark[it.end()].x() * imageWidth * scaleFactor,
                        landmark[it.end()].y() * imageHeight * scaleFactor,
                        paint
                    )
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult?,
        handLandmarkerResults: HandLandmarkerResult?,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        gesture: String
    ) {
        this.poseResults = poseLandmarkerResults
        this.handResults = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
        private const val CIRCLE_RADIUS = 10F
    }
}
