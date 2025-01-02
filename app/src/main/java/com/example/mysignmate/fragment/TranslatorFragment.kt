package com.example.mysignmate.fragment

import androidx.fragment.app.Fragment
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import android.content.Context
import com.example.mysignmate.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.pytorch.IValue

import java.io.File
import java.io.FileOutputStream


class TranslatorFragment : Fragment() {
    object Translator {
        private val sequence = mutableListOf<List<Float>>()
        var sentence = mutableListOf<String>()

        private val predictions = mutableListOf<Int>()
        private var module: Module? = null
        private var outputTensor: Tensor? = null
        private var _fragmentCameraBinding: FragmentCameraBinding? = null

        private val fragmentCameraBinding
            get() = _fragmentCameraBinding!!

        fun processLandmarks(
            poseResult: PoseLandmarkerResult?,
            handResult: HandLandmarkerResult?,
            context: Context
        ): Pair<MutableList<String>, String>? {
            val poseLandmarks = poseResult?.landmarks()?.flatten() ?: emptyList()

            // separate left and right hand landmarks
            val handLandmarksTemp = handResult?.landmarks()
            val handedness = handResult?.handedness()

            var leftHandResult: List<NormalizedLandmark>? = null
            var rightHandResult: List<NormalizedLandmark>? = null

            if (handLandmarksTemp != null && handedness != null) {
                for (i in handedness.indices) {
                    val categories = handedness[i]
                    if (categories.isNotEmpty()) {
                        val handLabel = categories[0].categoryName()
                        if (handLabel.equals("Left", ignoreCase = true)) {
                            leftHandResult = handLandmarksTemp[i]
                        } else if (handLabel.equals("Right", ignoreCase = true)) {
                            rightHandResult = handLandmarksTemp[i]
                        }
                    }
                }
            }

            val leftHandLandmarks = leftHandResult ?: emptyList()
            val rightHandLandmarks = rightHandResult ?: emptyList()

            val finalLandmarks = (poseLandmarks.let { poseLandmarksList ->
                // Flatten pose landmarks: x, y, z, visibility
                val poseList = poseLandmarksList.flatMap { res ->
                    listOf(
                        res.x() ?: 0.00000000e+00f,
                        res.y() ?: 0.00000000e+00f,
                        res.z() ?: 0.00000000e+00f,
                        res.visibility()?.orElse(0.00000000e+00f) ?: 0.00000000e+00f
                    )
                }
                // Pad the pose landmarks to ensure they have 132 elements
                val posePadded = poseList + List(132 - poseList.size) { 0.00000000e+00f }
                posePadded
            } + leftHandLandmarks.let { handLandmarksList ->
                // Flatten hand landmarks: x, y, z
                val leftHandList = handLandmarksList.flatMap { res ->
                    listOf(
                        res.x() ?: 0.00000000e+00f,
                        res.y() ?: 0.00000000e+00f,
                        res.z() ?: 0.00000000e+00f
                    )
                }
                // Pad the hand landmarks to ensure they have 63 elements
                val leftHandPadded = leftHandList + List(63 - leftHandList.size) { 0.00000000e+00f }
                leftHandPadded
            } + rightHandLandmarks.let { handLandmarksList ->
                // Flatten hand landmarks: x, y, z
                val rightHandList = handLandmarksList.flatMap { res ->
                    listOf(
                        res.x() ?: 0.00000000e+00f,
                        res.y() ?: 0.00000000e+00f,
                        res.z() ?: 0.00000000e+00f
                    )
                }
                // Pad the hand landmarks to ensure they have 63 elements
                val rightHandPadded = rightHandList + List(63 - rightHandList.size) { 0.00000000e+00f }
                rightHandPadded
            }).toList()

            // Normalize the landmarks before adding to the sequence
            val normalizedLandmarks = normalizeLandmarks(listOf(finalLandmarks))

//            println(normalizedLandmarks.map { it.toList() })

            sequence.addAll(normalizedLandmarks.map { it.toList() })

//            sequence.add(finalLandmarks as List<Float>)

            // Ensure the sequence doesn't exceed 30
            if (sequence.size > 30) {
                sequence.subList(0, sequence.size - 30).clear()
            }

            // Perform gesture inference if the sequence size is 30
            if (sequence.size == 30 && finalLandmarks.isNotEmpty()) {
                try {
                    // Load the model if not already loaded
                    if (module == null) {
                        module = LiteModuleLoader.load(assetFilePath(context, "lstm_model_6.95.ptl"))
                    }

                    if (module != null) {
                        // Preprocess the sequence of landmarks
                        val inputTensor = preprocessLandmarks(sequence)

                        val confidenceThreshold = 0.8f

                        // Perform inference or other operations with the loaded model and tensor
                        outputTensor = module!!.forward(IValue.from(inputTensor)).toTensor()
                        val floatArray = outputTensor?.getDataAsFloatArray()
                        val scoresList: List<Float> = floatArray?.toList() ?: emptyList()

                        var maxScore = -Float.MAX_VALUE
                        var maxScoreIdx = -1

                        for ((index, score) in scoresList.withIndex()) {
                            if (score > maxScore) {
                                maxScore = score
                                maxScoreIdx = index
                            }
                        }

                        // Check if max score exceeds the threshold
                        if (maxScore >= confidenceThreshold) {
                            predictions.add(maxScoreIdx)
                            val gesture = GestureClasses.GESTURE_CLASSES[maxScoreIdx]

                            val lastPredictions = predictions.takeLast(10)
                            val uniquePredictions = lastPredictions.distinct()

                            if (uniquePredictions.size == 1 && uniquePredictions[0] == maxScoreIdx) {
                                val currentGesture = gesture
                                if (sentence.isNotEmpty() && currentGesture != sentence.last()) {
                                    sentence.add(currentGesture)
                                } else if (sentence.isEmpty()) {
                                    sentence.add(currentGesture)
                                }
                            }

                            if (sentence.size > 4) {
                                sentence = sentence.takeLast(4).toMutableList()
                            }

                            return Pair(sentence, gesture)
                        }
                    } else {
                        // Model loading failed
                        println("Failed to load the model")
                    }
                } catch (e: SecurityException) {
                    // Handle the exception
                    e.printStackTrace()
                }
            } else {
                println("Received empty landmarks or sequence size less than 30 in Translator")
            }
            return null // Return null for conditions where gesture inference couldn't happen
        }

        // Dirty Scaling since webcam and phone camera has different resolution
        private fun normalizeLandmarks(sequence: List<List<Float>>): List<FloatArray> {
            val LEFT_SHOULDER = 11
            val RIGHT_SHOULDER = 12
            val NOSE = 0
            val LEFT_WRIST = 15
            val RIGHT_WRIST = 16

            val normalizedSequence = mutableListOf<FloatArray>()

            for (keypoints in sequence) {
                val poseKeypoints = keypoints.subList(0, 33 * 4).chunked(4)
                val leftHandKeypoints = keypoints.subList(33 * 4, 33 * 4 + 21 * 3).chunked(3)
                val rightHandKeypoints = keypoints.subList(33 * 4 + 21 * 3, keypoints.size).chunked(3)

                // Calculate neck and normalization factor
                val leftShoulder = poseKeypoints[LEFT_SHOULDER].subList(0, 2)
                val rightShoulder = poseKeypoints[RIGHT_SHOULDER].subList(0, 2)
                val neck = listOf(
                    (leftShoulder[0] + rightShoulder[0]) / 2f,
                    (leftShoulder[1] + rightShoulder[1]) / 2f
                )
                val nose = poseKeypoints[NOSE].subList(0, 2)
                val normFactor = Math.sqrt(
                    Math.pow((nose[0] - neck[0]).toDouble(), 2.0) +
                            Math.pow((nose[1] - neck[1]).toDouble(), 2.0)
                ).toFloat().takeIf { it != 0f } ?: 1f

                // Scale factors for x, y, z
                val xScale = 1 / 10f
                val yScale = 1 / 1f
                val zScale = 1 / 8f

                // Normalize pose keypoints
                val normalizedPose = poseKeypoints.map {
                    floatArrayOf(
                        (it[0] - neck[0]) / normFactor * xScale,
                        (it[1] - neck[1]) / normFactor * yScale,
                        it[2] / normFactor * zScale,
                        it[3] // Visibility remains unchanged
                    )
                }

                // Normalize hand keypoints
                fun normalizeHand(handKeypoints: List<List<Float>>, wristKeypoint: List<Float>): List<FloatArray> {
                    return if (handKeypoints.all { it == listOf(0f, 0f, 0f) }) {
                        handKeypoints.map { it.toFloatArray() }
                    } else {
                        val baseKeypoint = handKeypoints[0]
                        handKeypoints.map {
                            floatArrayOf(
                                ((it[0] - baseKeypoint[0]) / normFactor + (wristKeypoint[0] - neck[0]) / normFactor) * xScale,
                                ((it[1] - baseKeypoint[1]) / normFactor + (wristKeypoint[1] - neck[1]) / normFactor) * yScale,
                                it[2] / normFactor * zScale
                            )
                        }
                    }
                }

                val normalizedLeftHand = normalizeHand(leftHandKeypoints, poseKeypoints[LEFT_WRIST].subList(0, 2))
                val normalizedRightHand = normalizeHand(rightHandKeypoints, poseKeypoints[RIGHT_WRIST].subList(0, 2))

                // Concatenate normalized keypoints
                val normalizedLandmarks = normalizedPose.flatMap { it.toList() } +
                        normalizedLeftHand.flatMap { it.toList() } +
                        normalizedRightHand.flatMap { it.toList() }

                normalizedSequence.add(normalizedLandmarks.toFloatArray())
            }
            return normalizedSequence
        }



        private fun preprocessLandmarks(sequence: List<List<Float>>): Tensor {
            // Assuming each sublist in the sequence contains the landmarks
            val last30Sequence = sequence.takeLast(30)
            val reshapedSequence = MutableList(30) { FloatArray(258) { 0.0f } }

            // Convert the last 30 landmarks into the desired format
            for (i in last30Sequence.indices) {
                val landmarks = last30Sequence[i]
                for (j in landmarks.indices) {
                    reshapedSequence[i][j] = landmarks[j]
                }
            }

            // Flatten the reshaped sequence manually
            val flatSequence = mutableListOf<Float>()
            for (array in reshapedSequence) {
                flatSequence.addAll(array.toList())
            }

            // Convert the flattened sequence into a PyTorch tensor
            val tensorData = flatSequence.toFloatArray()
            return Tensor.fromBlob(tensorData, longArrayOf(1, 30, 258))
        }


        private fun assetFilePath(context: Context, assetName: String): String {
            val file = File(context.filesDir, assetName)
            if (!file.exists()) {
                context.assets.open(assetName).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            return file.absolutePath
        }
    }

}


