package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContract
import com.github.aiselp.autox.activity.TransparentActivity
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CancellationException

class ScreenCaptureManager : ScreenCaptureRequester {
    @Volatile
    override var screenCapture: ScreenCapturer? = null
    private var mediaProjection: MediaProjection? = null

    override suspend fun requestScreenCapture(context: Context, orientation: Int) {
        if (screenCapture?.available == true) {
            screenCapture?.setOrientation(orientation, context)
            return
        }

        val result = run {
            val result = CompletableDeferred<Intent>()
            TransparentActivity.requestNewActivity(context) { activity ->
                activity.registerForActivityResult(ScreenCaptureRequester()) { data ->
                    activity.finish()
                    if (data != null) {
                        result.complete(data)
                    } else result.completeExceptionally(CancellationException("data is null"))
                }.launch(activity)
            }
            result.await()
        }

        // 使用服务绑定确保服务就绪
        val serviceConnected = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    // 服务已连接，安全获取mediaProjection
                    mediaProjection =
                        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                            .getMediaProjection(Activity.RESULT_OK, result)
                    CaptureForegroundService.setMediaProjection(context, mediaProjection!!)
                    screenCapture = ScreenCapturer(mediaProjection!!, orientation)
                } finally {
                    serviceConnected.complete(Unit)
                    context.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 服务意外断开时处理
                serviceConnected.completeExceptionally(IllegalStateException("Service disconnected unexpectedly"))
            }
        }

        // 绑定服务并等待连接
        context.startService(Intent(context, CaptureForegroundService::class.java))
        context.bindService(
            Intent(context, CaptureForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        serviceConnected.await()
    }

    class ScreenCaptureRequester : ActivityResultContract<Context, Intent?>() {
        override fun createIntent(context: Context, input: Context): Intent {
            return (input.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode != Activity.RESULT_OK) {
                null
            } else intent
        }
    }

    override fun recycle() {
        screenCapture?.release()
        screenCapture = null
        mediaProjection?.stop()
        mediaProjection = null
    }
}