package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.GoldenYellowHighlight
import com.example.ui.theme.NavyPrimary

/**
 * Live CameraX Rear-Camera Viewfinder for scanning Milk Machine Displays (LCD/LED) & Thermal Receipts.
 */
@Composable
fun CameraXLiveScanner(
    onBitmapCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var torchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(4f) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProviderRef = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCaptureUseCase = capture

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                cameraControlRef = camera
                val zs = camera.cameraInfo.zoomState.value
                if (zs != null) {
                    maxZoomRatio = zs.maxZoomRatio.coerceIn(1.5f, 8f)
                }
            } catch (e: Exception) {
                val msg = "कैमरा प्रीव्यू प्रारंभ नहीं हो सका: ${e.localizedMessage ?: "Hardware unavailable"}"
                cameraError = msg
                onError(msg)
            }
        }, mainExecutor)

        onDispose {
            try {
                cameraControlRef?.cameraControl?.enableTorch(false)
                cameraProviderRef?.unbindAll()
            } catch (e: Exception) {
                Log.w("CameraXLiveScanner", "Error unbinding CameraX", e)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(275.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = Color.Black,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (cameraError == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Framing Guide Box for Machine Screen / Thermal Slip
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.84f)
                        .height(145.dp)
                        .border(
                            BorderStroke(2.dp, GoldenYellowHighlight.copy(alpha = 0.85f)),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(bottomEnd = 8.dp)
                    ) {
                        Text(
                            text = "मशीन डिस्प्ले / पर्ची को इस बॉक्स में रखें",
                            color = GoldenYellowHighlight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = cameraError!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Toolbar: Flash & Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DairyGreenSecondary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "● LIVE CAMERA",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            cameraControlRef?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("camerax_torch_btn")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (torchEnabled) GoldenYellowHighlight else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("camerax_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Camera",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Zoom + Capture Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (cameraError == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zoom ${String.format("%.1f", zoomRatio)}x",
                            color = GoldenYellowHighlight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = zoomRatio,
                            onValueChange = { next ->
                                zoomRatio = next
                                cameraControlRef?.cameraControl?.setZoomRatio(next)
                            },
                            valueRange = 1f..maxZoomRatio,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldenYellowHighlight,
                                activeTrackColor = GoldenYellowHighlight
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .padding(horizontal = 8.dp)
                                .testTag("camerax_zoom_slider")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                Button(
                    enabled = !isCapturing && imageCaptureUseCase != null && cameraError == null,
                    onClick = {
                        val capture = imageCaptureUseCase ?: return@Button
                        isCapturing = true
                        val mainExecutor = ContextCompat.getMainExecutor(context)
                        capture.takePicture(
                            mainExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bmp = imageProxyToUprightBitmap(image)
                                        image.close()
                                        isCapturing = false
                                        if (bmp != null) {
                                            onBitmapCaptured(bmp)
                                        } else {
                                            onError("कैप्चर की गई छवि डिकोड नहीं हो सकी")
                                        }
                                    } catch (e: Exception) {
                                        image.close()
                                        isCapturing = false
                                        onError("फोटो प्रोसेसिंग त्रुटि: ${e.localizedMessage}")
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    onError("कैमरा कैप्चर विफल: ${exception.localizedMessage}")
                                }
                            }
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.testTag("camerax_capture_frame_btn")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCapturing) "स्कैन हो रहा है..." else "फोटो खींचें और OCR स्कैन करें",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                }
            }
        }
    }
}

private fun imageProxyToUprightBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees.toFloat()
    return if (rotation != 0f) {
        val matrix = Matrix().apply { postRotate(rotation) }
        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    } else {
        rawBitmap
    }
}
