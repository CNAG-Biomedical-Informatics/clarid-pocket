package org.cnag.clarid.pocket

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@SuppressLint("ClickableViewAccessibility") // The listener observes gestures; PreviewView handles them.
@Composable
internal fun QrCamera(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
    onFeedback: (ScanFeedback) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scanned = rememberUpdatedState(onScanned)
    val failed = rememberUpdatedState(onError)
    val feedback = rememberUpdatedState(onFeedback)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(previewView, owner) {
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val controller = LifecycleCameraController(context)
        val target = ScanTarget()
        var disposed = false
        var delivered = false
        var touching = false
        var analysisFailures = 0

        fun reset() {
            target.reset()
            feedback.value(ScanFeedback())
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) reset()
        }
        owner.lifecycle.addObserver(observer)
        previewView.setOnTouchListener { _, event ->
            touching = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            reset()
            false // Preserve the controller's tap-to-focus and pinch-to-zoom gestures.
        }
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.isTapToFocusEnabled = true
        controller.isPinchToZoomEnabled = true
        controller.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
            listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor,
        ) { result ->
            if (!disposed && !delivered) {
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                    touching) {
                    reset()
                } else if (result?.getThrowable(scanner) != null) {
                    reset()
                    if (++analysisFailures >= 5) {
                        delivered = true
                        failed.value("The QR scanner could not process the image. Try scanning again.")
                    }
                } else {
                    analysisFailures = 0
                    // CameraX maps ML Kit bounds into PreviewView coordinates, including
                    // rotation, cropping, zoom, and its shared preview/analysis viewport.
                    val candidates = result?.getValue(scanner).orEmpty().mapNotNull { barcode ->
                        barcode.boundingBox?.let { bounds ->
                            ScanCandidate(barcode.rawValue, ScanBounds(bounds.left.toFloat(), bounds.top.toFloat(),
                                bounds.right.toFloat(), bounds.bottom.toFloat()))
                        }
                    }
                    val update = target.update(candidates, previewView.width.toFloat(),
                        previewView.height.toFloat())
                    feedback.value(update)
                    update.value?.let {
                        delivered = true
                        scanned.value(it)
                    }
                }
            }
        })
        try {
            previewView.controller = controller
            controller.bindToLifecycle(owner)
            controller.initializationFuture.addListener({
                if (!disposed) {
                    try { controller.initializationFuture.get() }
                    catch (_: Exception) {
                        failed.value("Camera unavailable. Check camera permission and try again.")
                    }
                }
            }, executor)
        } catch (_: Exception) {
            failed.value("Camera unavailable. Check camera permission and try again.")
        }
        onDispose {
            disposed = true
            owner.lifecycle.removeObserver(observer)
            previewView.setOnTouchListener(null)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            previewView.controller = null
            scanner.close()
        }
    }
}
