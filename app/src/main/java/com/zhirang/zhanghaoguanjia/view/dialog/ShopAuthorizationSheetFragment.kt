package com.zhirang.zhanghaoguanjia.view.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetShopAuthorizationBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class ShopAuthorizationSheetFragment : DialogFragment() {
    private var _binding: BottomSheetShopAuthorizationBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_STREAM_URL = "stream_url"
        private const val ARG_CONTROL_URL = "control_url"
        private const val ARG_SHOP_NAME = "shop_name"
        private const val ARG_USER_PHONE = "user_phone"
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_AUTHORIZATION_URL = "authorization_url"
        private const val TAG = "ShopAuthSheet"
        private const val DIALOG_WIDTH_RATIO = 0.92f
        private const val DIALOG_HEIGHT_RATIO = 0.67f
        private const val REMOTE_BROWSER_SCALE = 1.25f

        fun newInstance(
            title: String,
            streamUrl: String,
            controlUrl: String,
            shopName: String,
            userPhone: String,
            shopId: String,
            authorizationUrl: String?
        ): ShopAuthorizationSheetFragment {
            return ShopAuthorizationSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_STREAM_URL, streamUrl)
                    putString(ARG_CONTROL_URL, controlUrl)
                    putString(ARG_SHOP_NAME, shopName)
                    putString(ARG_USER_PHONE, userPhone)
                    putString(ARG_SHOP_ID, shopId)
                    putString(ARG_AUTHORIZATION_URL, authorizationUrl.orEmpty())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.ShopAuthorizationDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_shop_authorization, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout(
                (resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).roundToInt(),
                (resources.displayMetrics.heightPixels * DIALOG_HEIGHT_RATIO).roundToInt()
            )
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetShopAuthorizationBinding.bind(view)

        binding.tvShopAuthorizationTitle.text =
            arguments?.getString(ARG_TITLE).orEmpty().ifBlank {
                getString(R.string.shop_authorization_title)
            }

        binding.webShopAuthorization.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.textZoom = 100
            setInitialScale(100)
            setOnTouchListener { webView, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (isKeyboardRegion(event.x, event.y, webView.width, webView.height)) {
                        webView.requestFocus()
                        inputMethodManager()?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                    } else {
                        inputMethodManager()?.hideSoftInputFromWindow(webView.windowToken, 0)
                    }
                }
                false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    binding.tvShopAuthorizationStatus.visibility = View.VISIBLE
                    binding.tvShopAuthorizationStatus.text =
                        getString(R.string.shop_authorization_loading)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.tvShopAuthorizationStatus.visibility = View.GONE
                    installXpraStabilizer(view)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == false) {
                        return
                    }
                    binding.tvShopAuthorizationStatus.visibility = View.VISIBLE
                    binding.tvShopAuthorizationStatus.text =
                        getString(R.string.shop_authorization_load_failed)
                }
            }
        }

        val streamUrl = arguments?.getString(ARG_STREAM_URL).orEmpty()
        val controlUrl = arguments?.getString(ARG_CONTROL_URL).orEmpty()
        val userPhone = arguments?.getString(ARG_USER_PHONE).orEmpty()
        val shopId = arguments?.getString(ARG_SHOP_ID).orEmpty()
        val authorizationUrl = arguments?.getString(ARG_AUTHORIZATION_URL).orEmpty()
        if (streamUrl.isBlank()) {
            binding.tvShopAuthorizationStatus.text =
                getString(R.string.shop_authorization_load_failed)
        } else {
            binding.webShopAuthorization.post {
                val currentBinding = _binding ?: return@post
                val viewport = remoteViewportSize(currentBinding.webShopAuthorization)
                openRemoteBrowserThenLoad(
                    controlUrl = controlUrl,
                    streamUrl = streamUrl,
                    userPhone = userPhone,
                    shopId = shopId,
                    authorizationUrl = authorizationUrl,
                    viewportWidth = viewport.first,
                    viewportHeight = viewport.second
                )
            }
        }
    }

    private fun openRemoteBrowserThenLoad(
        controlUrl: String,
        streamUrl: String,
        userPhone: String,
        shopId: String,
        authorizationUrl: String,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        binding.tvShopAuthorizationStatus.visibility = View.VISIBLE
        binding.tvShopAuthorizationStatus.text = getString(R.string.shop_authorization_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val browserReady = requestRemoteBrowser(
                controlUrl,
                userPhone,
                shopId,
                authorizationUrl,
                viewportWidth,
                viewportHeight
            )
            val currentBinding = _binding ?: return@launch
            if (browserReady) {
                currentBinding.webShopAuthorization.loadUrl(toFixedXpraClientUrl(streamUrl))
            } else {
                currentBinding.tvShopAuthorizationStatus.visibility = View.VISIBLE
                currentBinding.tvShopAuthorizationStatus.text =
                    getString(R.string.shop_authorization_load_failed)
            }
        }
    }

    private suspend fun requestRemoteBrowser(
        controlUrl: String,
        userPhone: String,
        shopId: String,
        authorizationUrl: String,
        viewportWidth: Int,
        viewportHeight: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (controlUrl.isBlank()) {
            return@withContext true
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(
                toControlOpenUrl(
                    controlUrl,
                    userPhone,
                    shopId,
                    authorizationUrl,
                    viewportWidth,
                    viewportHeight
                )
            ).openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 25_000
            }
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.use { stream ->
                stream.bufferedReader().readText()
            }.orEmpty()
            if (code !in 200..299) {
                Log.w(TAG, "remote browser open failed code=$code body=$body")
            }
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "remote browser open request failed", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun toControlOpenUrl(
        controlUrl: String,
        userPhone: String,
        shopId: String,
        authorizationUrl: String,
        viewportWidth: Int,
        viewportHeight: Int
    ): String {
        val base = controlUrl.trim().trimEnd('/')
        val phone = URLEncoder.encode(userPhone.ifBlank { "unknown-phone" }, "UTF-8")
        val safeShopId = URLEncoder.encode(shopId.ifBlank { "unknown-shop" }, "UTF-8")
        val encodedUrl = authorizationUrl
            .takeIf { it.isNotBlank() }
            ?.let { URLEncoder.encode(it, "UTF-8") }
            ?.let { "&url=$it" }
            .orEmpty()
        return "$base/open?phone=$phone&shopId=$safeShopId&width=$viewportWidth&height=$viewportHeight&scale=$REMOTE_BROWSER_SCALE$encodedUrl"
    }

    private fun remoteViewportSize(webView: WebView): Pair<Int, Int> {
        val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val width = (webView.width / density).roundToInt().coerceIn(360, 640)
        val height = (webView.height / density).roundToInt().coerceIn(520, 900)
        return width to height
    }

    private fun toFixedXpraClientUrl(streamUrl: String): String {
        val separator = if (streamUrl.contains("?")) "&" else "?"
        return "$streamUrl${separator}autohide=true&touchaction=scroll&sound=false&video=false&clipboard=false&printing=false&file_transfer=false"
    }

    private fun isKeyboardRegion(x: Float, y: Float, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }
        val relativeX = x / width
        val relativeY = y / height
        val desktopLoginInput = relativeX in 0.28f..0.75f && relativeY in 0.34f..0.50f
        val mobileLoginInput = relativeX in 0.04f..0.46f && relativeY in 0.15f..0.33f
        return desktopLoginInput || mobileLoginInput
    }

    private fun inputMethodManager(): InputMethodManager? {
        return context?.getSystemService(InputMethodManager::class.java)
    }

    private fun installXpraStabilizer(view: WebView?) {
        val script = """
            (function() {
                if (!document.getElementById('duodian-xpra-stable-style')) {
                    var style = document.createElement('style');
                    style.id = 'duodian-xpra-stable-style';
                    style.textContent = [
                        'html,body{margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;overflow:hidden!important;background:#fff!important;}',
                        '#screen{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;overflow:hidden!important;background:#fff!important;}',
                        '#float_menu,.windowhead,.windowbuttons,.windowicon,.windowtitle,.ui-resizable-handle{display:none!important;}',
                        'div.window{position:absolute!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;margin:0!important;padding:0!important;border:0!important;box-shadow:none!important;overflow:hidden!important;background:#fff!important;transform:none!important;}',
                        'div.window canvas{position:absolute!important;left:0!important;top:0!important;width:100%!important;height:100%!important;display:block!important;border:0!important;touch-action:none!important;}',
                        '#pasteboard{position:absolute!important;left:-9999px!important;top:-9999px!important;width:1px!important;height:1px!important;opacity:0!important;}'
                    ].join('');
                    document.head.appendChild(style);
                }
                if (window.XpraClient && !window.XpraClient.__duodianStableResize) {
                    window.XpraClient.prototype._screen_resized = function() {};
                    window.XpraClient.__duodianStableResize = true;
                }
                function setKeyboardEnabled(enabled) {
                    var pasteboard = document.getElementById('pasteboard');
                    if (enabled) {
                        window.__duodianKeyboardAllowUntil = Date.now() + 120000;
                    } else {
                        window.__duodianKeyboardAllowUntil = 0;
                    }
                    if (window.client) {
                        window.client.capture_keyboard = !!enabled;
                    }
                    if (!pasteboard) {
                        return;
                    }
                    pasteboard.removeAttribute('autofocus');
                    if (enabled) {
                        pasteboard.focus();
                        pasteboard.click();
                    } else {
                        pasteboard.blur();
                    }
                }
                function eventPoint(event) {
                    var source = event.touches && event.touches.length ? event.touches[0] : event;
                    var canvas = document.querySelector('div.window canvas');
                    if (!canvas || !source) {
                        return null;
                    }
                    var rect = canvas.getBoundingClientRect();
                    if (!rect.width || !rect.height) {
                        return null;
                    }
                    var x = (source.clientX - rect.left) * ((canvas.width || rect.width) / rect.width);
                    var y = (source.clientY - rect.top) * ((canvas.height || rect.height) / rect.height);
                    return {x: x, y: y};
                }
                function isLoginInputPoint(point) {
                    if (!point) {
                        return false;
                    }
                    var leftMobileLogin = (
                        point.x >= 40 && point.x <= 460 &&
                        ((point.y >= 160 && point.y <= 245) || (point.y >= 245 && point.y <= 340))
                    );
                    var canvas = document.querySelector('div.window canvas');
                    var canvasWidth = canvas ? (canvas.width || canvas.getBoundingClientRect().width || 1) : 1;
                    var canvasHeight = canvas ? (canvas.height || canvas.getBoundingClientRect().height || 1) : 1;
                    var centeredDesktopLogin = (
                        point.x >= canvasWidth * 0.28 && point.x <= canvasWidth * 0.75 &&
                        point.y >= canvasHeight * 0.34 && point.y <= canvasHeight * 0.50
                    );
                    return leftMobileLogin || centeredDesktopLogin;
                }
                function handleRemoteTouch(event) {
                    setKeyboardEnabled(isLoginInputPoint(eventPoint(event)));
                }
                function enforceKeyboardGate() {
                    if (!window.__duodianKeyboardAllowUntil || Date.now() > window.__duodianKeyboardAllowUntil) {
                        setKeyboardEnabled(false);
                    }
                }
                if (!window.__duodianXpraKeyboardGate) {
                    window.__duodianXpraKeyboardGate = true;
                    document.addEventListener('touchstart', handleRemoteTouch, true);
                    document.addEventListener('mousedown', handleRemoteTouch, true);
                    setKeyboardEnabled(false);
                    window.__duodianKeyboardGateTimer = window.setInterval(enforceKeyboardGate, 500);
                }
                function pinWindow() {
                    var windows = document.querySelectorAll('div.window');
                    for (var i = 0; i < windows.length; i++) {
                        windows[i].style.left = '0px';
                        windows[i].style.top = '0px';
                        windows[i].style.width = '100vw';
                        windows[i].style.height = '100vh';
                    }
                }
                pinWindow();
                if (!window.__duodianXpraPinTimer) {
                    window.__duodianXpraPinTimer = window.setInterval(pinWindow, 1000);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    override fun onDestroyView() {
        binding.webShopAuthorization.apply {
            stopLoading()
            loadUrl("about:blank")
            webViewClient = WebViewClient()
        }
        super.onDestroyView()
        _binding = null
    }
}
