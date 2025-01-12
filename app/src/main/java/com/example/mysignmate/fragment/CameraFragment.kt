/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.mysignmate.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.example.mysignmate.PoseLandmarkerHelper
import com.example.mysignmate.PoseViewModel
import com.example.mysignmate.HandLandmarkerHelper
import com.example.mysignmate.HandViewModel
import com.example.mysignmate.R
import com.example.mysignmate.databinding.FragmentCameraBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class CameraFragment : Fragment(), PoseLandmarkerHelper.PoseLandmarkerListener, HandLandmarkerHelper.HandLandmarkerListener {

    companion object {
        private const val TAG = "CameraFragment"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private val poseViewModel: PoseViewModel by activityViewModels()
    private val handViewModel: HandViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private var isOverLayEnabled = true

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the PoseLandmarkerHelper and HandLandmarkerHelper again when users come back to the foreground.
        backgroundExecutor.execute {
            if(this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }

            if(this::handLandmarkerHelper.isInitialized) {
                if (handLandmarkerHelper.isClose()) {
                    handLandmarkerHelper.setupHandLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::poseLandmarkerHelper.isInitialized) {
            poseViewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            poseViewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            poseViewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            poseViewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }

        if(this::handLandmarkerHelper.isInitialized) {
            handViewModel.setMaxHands(handLandmarkerHelper.maxNumHands)
            handViewModel.setMinHandDetectionConfidence(handLandmarkerHelper.minHandDetectionConfidence)
            handViewModel.setMinHandTrackingConfidence(handLandmarkerHelper.minHandTrackingConfidence)
            handViewModel.setMinHandPresenceConfidence(handLandmarkerHelper.minHandPresenceConfidence)
            handViewModel.setDelegate(handLandmarkerHelper.currentDelegate)

            // Close the HandLandmarkerHelper and release resources
            backgroundExecutor.execute { handLandmarkerHelper.clearHandLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    // Temporary
    private var xScale = 31 / 100f
    private var yScale = 1f
//    private var zScale = 18 / 100f

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the PoseLandmarkerHelper and HandLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = poseViewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = poseViewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = poseViewModel.currentMinPosePresenceConfidence,
                currentDelegate = poseViewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                maxNumHands = handViewModel.currentMaxHands,
                minHandDetectionConfidence = handViewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = handViewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = handViewModel.currentMinHandPresenceConfidence,
                currentDelegate = handViewModel.currentDelegate,
                handLandmarkerHelperListener = this
            )
        }

        // Set up the listeners for the buttons
        fragmentCameraBinding.btnToggleCamera.setOnClickListener {
            toggleCamera()
        }

        // temporary
        val xScaleSeekBar = view.findViewById<SeekBar>(R.id.x_scale_seekbar)
        val yScaleSeekBar = view.findViewById<SeekBar>(R.id.y_scale_seekbar)
//        val zScaleSeekBar = view.findViewById<SeekBar>(R.id.z_scale_seekbar)

        fragmentCameraBinding.btnClearSentence.setOnClickListener {
            clearSentence()

            // Reset SeekBars to default values
            xScaleSeekBar.progress = 31
            yScaleSeekBar.progress = 100
//            zScaleSeekBar.progress = 18

            // Update scaling factors in TranslatorFragment
            TranslatorFragment.Translator.updateScalingFactors(0.31f, 1.0f)
        }

        fragmentCameraBinding.toggleOverlayButton.setOnClickListener {
            isOverLayEnabled = !isOverLayEnabled
            fragmentCameraBinding.overlay.setOverlayEnabled(isOverLayEnabled)
        }

        xScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                xScale = progress / 100f
                TranslatorFragment.Translator.updateScalingFactors(xScale, yScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        yScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                yScale = progress / 100f
                TranslatorFragment.Translator.updateScalingFactors(xScale, yScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

//        zScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                zScale = progress / 100f
//                TranslatorFragment.Translator.updateScalingFactors(xScale, yScale, zScale)
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
//        })
    }

    // function to clear the sentence
    private fun clearSentence() {
        TranslatorFragment.Translator.sentence.clear()
        fragmentCameraBinding.overlay.clear()
    }

    // function to toggle between front and back camera
    private fun toggleCamera() {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        // Rebind the camera use cases with the updated cameraFacing value
        bindCameraUseCases()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError", "VisibleForTests")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {analyzer ->
                    analyzer.setAnalyzer(backgroundExecutor) { image ->
                        try {
                            val bitmap = convertImageProxyToBitmap(image, cameraFacing == CameraSelector.LENS_FACING_FRONT)
                            val mpImage = BitmapImageBuilder(bitmap).build()
                            poseLandmarkerHelper.detectAsync(mpImage, SystemClock.uptimeMillis())
                            handLandmarkerHelper.detectAsync(mpImage, SystemClock.uptimeMillis())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during image analysis", e)
                        } finally {
                            image.close()
                        }
                    }
                }
        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // 2) Keep track of the latest landmarker results
    private var latestPoseResult: PoseLandmarkerResult? = null
    private var latestHandResult: HandLandmarkerResult? = null
    private var latestImageHeight: Int = 0
    private var latestImageWidth: Int = 0

    // This is called when your PoseLandmarkerHelper produces a result.
    override fun onPoseResults(resultBundle: PoseLandmarkerHelper.PoseResultBundle) {
        // Capture the pose result and its metadata
        latestPoseResult = resultBundle.results.firstOrNull()
        latestImageHeight = resultBundle.inputImageHeight
        latestImageWidth = resultBundle.inputImageWidth

        // Merge with hand results (if any) and update UI
        onPoseAndHandResults(
            poseResults = latestPoseResult,
            handResults = latestHandResult,
            imageHeight = latestImageHeight,
            imageWidth = latestImageWidth,
            poseInferenceTime = resultBundle.inferenceTime,
            handInferenceTime = null
        )
    }

    // This is called when your HandLandmarkerHelper produces a result.
    override fun onHandResults(resultBundle: HandLandmarkerHelper.HandResultBundle) {
        // Capture the hand result and its metadata
        latestHandResult = resultBundle.results.firstOrNull()
        latestImageHeight = resultBundle.inputImageHeight
        latestImageWidth = resultBundle.inputImageWidth

        // Merge with pose results (if any) and update UI
        onPoseAndHandResults(
            poseResults = latestPoseResult,
            handResults = latestHandResult,
            imageHeight = latestImageHeight,
            imageWidth = latestImageWidth,
            poseInferenceTime = null,
            handInferenceTime = resultBundle.inferenceTime
        )
    }

    private var signGesture: String = ""

    @SuppressLint("SetTextI18n")    private fun onPoseAndHandResults(
        poseResults: PoseLandmarkerResult?,
        handResults: HandLandmarkerResult?,
        imageHeight: Int,
        imageWidth: Int,
        poseInferenceTime: Long?,
        handInferenceTime: Long?
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // Show inference time (for benchmarking purposes)
                val poseTime = poseInferenceTime ?: 0L
                val handTime = handInferenceTime ?: 0L

//                fragmentCameraBinding.bottomSheetLayout.poseInferenceTimeVal.text =
//                    "$poseTime ms (Pose)"
//
//                fragmentCameraBinding.bottomSheetLayout.handInferenceTimeVal.text =
//                    "$handTime ms (Hand)"

                val totalTime = poseTime + handTime
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    "$totalTime ms (Total)"


                // Prediction for the sign gesture
                val context = requireContext()
                val pairResult = TranslatorFragment.Translator.processLandmarks(poseResults, handResults, context)
                val sentences = pairResult?.first
                var gesture = pairResult?.second

                if (sentences != null) {
                    signGesture = sentences.joinToString(" ")
                }

                if (gesture == null || gesture == "RESET") {
                    gesture = "Predicting..."
                }

                fragmentCameraBinding.gestureTextView.text = gesture
                fragmentCameraBinding.bottomSheetLayout.resultValue.text = String.format("%s", gesture)

                // Pass both results into the overlay
                fragmentCameraBinding.overlay.setResults(
                    poseLandmarkerResults = poseResults, // could be null
                    handLandmarkerResults = handResults, // could be null
                    imageHeight = imageHeight,
                    imageWidth = imageWidth,
                    runningMode = RunningMode.LIVE_STREAM,
                    gesture = " "
//                    gesture = TranslatorFragment.Translator.sentence.joinToString(" ")
                )

                // Force the overlay to redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onPoseError(error: String, errorCode: Int) {
    }

    override fun onHandError(error: String, errorCode: Int) {
    }

}