package uk.ac.cam.energy.common.execution

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import uk.ac.cam.energy.common.operations.globalWebsiteDisplayedLock
import uk.ac.cam.energy.common.waitUntilPredicateTrue

/**
 * Service to run the [WebOperation] as an overlay (even when there's no activity or service
 * currently running).
 */
class WebOperationService : Service() {

    private var webView: WebView? = null
    private var url: String? = null
    private var websiteLoaded = false

    override fun onCreate() {
        Log.i("WebOperationService", "onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("WebOperationService", "onStartCommand()")
        url = intent!!.getStringExtra("url")!!

        val backgroundRunnable = Runnable {
            try {
                // Load website on main thread
                Handler(mainLooper).post {
                    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                    createWindowAndWebView(windowManager, url!!)
                }

                // Wait in background until the website has been loaded successfully
                waitUntilPredicateTrue(30_000) { websiteLoaded }
                Log.i("WebOperationService", "websiteLoaded=$websiteLoaded")
                if (!websiteLoaded) {
                    throw IllegalStateException("Website was NOT loaded: $url")
                }
            } finally {
                // Finally: indicate that we are finish and stop service
                globalWebsiteDisplayedLock.decrementAndGet()
                stopSelf()
            }
        }
        Thread(backgroundRunnable).start()
        Log.i("WebOperationService", "onStartCommand() finished")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("WebOperationService", "onDestroy()")

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.removeView(webView)

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i("WebOperationService", "onBind()")
        return null
    }

    private fun createWindowAndWebView(windowManager:WindowManager, url: String) {
        Log.i("WebOperationService", "createWindowAndWebView()")

        val params =
            WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100
        params.width = 600
        params.height = 600

        webView = WebView(this)
        webView!!.clearCache(true)
        webView!!.setWebViewClient(object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                Log.e("WebOperation", "loading web view: request: $request error: $error")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i("WebOperation", "loaded: $url")
                websiteLoaded = true
            }
        })
        windowManager.addView(webView, params)
        webView!!.loadUrl(url)
    }

}
