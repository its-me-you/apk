package com.broraa.messenger

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File

/**
 * Wraps the Broraa web messenger (its-me-you.github.io/cutie) in a WebView so it can
 * be installed as a normal Android app, with native permission prompts for camera,
 * microphone, notifications, and storage/media access.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val startUrl = "https://its-me-you.github.io/cutie"
    private val webAppHost = "its-me-you.github.io"

    /** Every permission this app asks for, chosen per Android version. */
    private val permissionsToRequest: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms += Manifest.permission.POST_NOTIFICATIONS
                perms += Manifest.permission.READ_MEDIA_IMAGES
                perms += Manifest.permission.READ_MEDIA_VIDEO
                perms += Manifest.permission.READ_MEDIA_AUDIO
            } else {
                perms += Manifest.permission.READ_EXTERNAL_STORAGE
                perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            return perms.toTypedArray()
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var results: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                results = when {
                    data?.data != null -> arrayOf(data.data!!)
                    cameraImageUri != null -> arrayOf(cameraImageUri!!)
                    else -> null
                }
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
            cameraImageUri = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsLauncher.launch(permissionsToRequest)

        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                return if (url.host == webAppHost) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val granted = request.resources.filter { resource ->
                        when (resource) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                hasPermission(Manifest.permission.CAMERA)
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                hasPermission(Manifest.permission.RECORD_AUDIO)
                            else -> false
                        }
                    }.toTypedArray()

                    if (granted.isNotEmpty()) request.grant(granted) else request.deny()
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                cameraImageUri = null

                // Camera option, only offered if a camera app + permission are available
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                val canUseCamera = hasPermission(Manifest.permission.CAMERA) &&
                    cameraIntent.resolveActivity(packageManager) != null
                if (canUseCamera) {
                    val photoFile = File.createTempFile("capture_", ".jpg", cacheDir)
                    val photoUri = FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", photoFile
                    )
                    cameraImageUri = photoUri
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }

                // Generic document/gallery picker
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = params.acceptTypes?.firstOrNull { it.isNotBlank() } ?: "*/*"
                }

                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, pickIntent)
                    putExtra(Intent.EXTRA_TITLE, "Choose an option")
                    if (cameraImageUri != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }
                }

                fileChooserLauncher.launch(chooser)
                return true
            }
        }

        // Anything the page tries to download (e.g. saving a shared photo) goes through
        // the system Download Manager - this is what the storage permission is for.
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
