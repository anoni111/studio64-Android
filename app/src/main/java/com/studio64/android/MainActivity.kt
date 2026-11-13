package com.studio64.android

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    companion object {
        const val PORT = 8064
        const val BASE_URL = "http://127.0.0.1:$PORT/"
        const val TAG = "Studio64"
    }

    private lateinit var root: FrameLayout
    private var web: WebView? = null
    private var statusView: TextView? = null
    private var chooserCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val pickEngine = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importEngine(uri)
    }

    private val pickRom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importRom(uri)
    }

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooserCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(0xFF1E2327.toInt()) }
        setContentView(root)
        hideSystemBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (customView != null) {
                    // salir de la pantalla completa del juego
                    w?.evaluateJavascript("document.fullscreenElement&&document.exitFullscreen()", null)
                    hideCustom()
                } else if (w != null && w.canGoBack()) w.goBack() else moveTaskToBack(true)
            }
        })

        showPanel("Preparando Studio 64…", spinner = true)
        Thread {
            try {
                Installer.unpackApp(this)
                runOnUiThread { route() }
            } catch (e: Exception) {
                Log.e(TAG, "unpackApp", e)
                runOnUiThread { showPanel("No se pudo preparar la app:\n${e.message}") }
            }
        }.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    // ---------------------------------------------------------------- paneles simples

    private fun showPanel(
        body: String,
        spinner: Boolean = false,
        button: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        root.removeAllViews()
        web = null
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Studio 64"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        val msg = TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(0xFFC9D1D9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }
        statusView = msg
        col.addView(msg)
        if (spinner) col.addView(ProgressBar(this))
        if (button != null && onClick != null) {
            col.addView(Button(this).apply {
                text = button
                isAllCaps = false
                setOnClickListener { onClick() }
            })
        }
        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    private fun showImportPanel(error: String? = null) {
        val intro = "Falta el motor del juego.\n\n" +
            "Studio 64 compila Super Mario 64 desde TU propia ROM (US) y eso no se puede hacer dentro del telefono. " +
            "Hazlo una vez en un PC con el instalador oficial de Studio 64 (v0.2.4-beta), " +
            "comprime las carpetas «web» y «server» de esa carpeta en un .zip, " +
            "pasalo al telefono y elígelo aqui."
        showPanel(
            (if (error != null) "⚠ $error\n\n" else "") + intro,
            button = "Elegir .zip del motor",
        ) {
            pickEngine.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
    }

    private fun importRom(uri: Uri) {
        toast("Importando la ROM…")
        Thread {
            try {
                Installer.importRom(this, uri)
                toast("ROM importada. Ya puedes exportar ROM / BPS desde Studio 64.")
            } catch (e: Exception) {
                Log.e(TAG, "importRom", e)
                toast("No se pudo importar la ROM: ${e.message}")
            }
        }.start()
    }

    private fun showMenu() {
        val rom = if (Installer.romReady(this)) "✔ ROM de SM64 importada" else "✘ Falta la ROM de SM64"
        android.app.AlertDialog.Builder(this)
            .setTitle("Studio 64")
            .setMessage("Exportar ROM/BPS necesita tu ROM de Super Mario 64 (US).\n$rom")
            .setPositiveButton("Importar ROM…") { _, _ ->
                pickRom.launch(arrayOf("*/*"))
            }
            .setNeutralButton("Reimportar motor (.zip)…") { _, _ ->
                pickEngine.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun route() {
        if (Installer.engineReady(this)) startServer() else showImportPanel()
    }

    private fun importEngine(uri: Uri) {
        showPanel("Importando el motor…\nNo cierres la app.", spinner = true)
        Thread {
            val error: String? = try {
                Installer.importEngine(this, uri) { n ->
                    if (n % 25 == 0) runOnUiThread { statusView?.text = "Importando el motor…\n$n archivos" }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "importEngine", e)
                e.message ?: e.toString()
            }
            runOnUiThread { if (error == null) startServer() else showImportPanel("No se pudo importar: $error") }
        }.start()
    }

    // ---------------------------------------------------------------- Node

    private fun startNodeOnce() {
        synchronized(NodeBridge) {
            if (NodeBridge.started) return
            NodeBridge.started = true
        }
        val appDir = Installer.appDir(this)
        val home = File(filesDir, "home").apply { mkdirs() }
        try {
            Os.setenv("HOME", home.absolutePath, true)
            Os.setenv("TMPDIR", cacheDir.absolutePath, true)
            Os.setenv("PORT", PORT.toString(), true)
            Os.setenv("HOST", "127.0.0.1", true)
            Os.setenv("STUDIO_SETUP", "off", true)
        } catch (e: Exception) {
            Log.w(TAG, "setenv", e)
        }
        val script = File(appDir, "android-main.mjs").absolutePath
        Thread(null, {
            val rc = NodeBridge.startNodeWithArguments(arrayOf("node", script))
            Log.i(TAG, "Node termino con codigo $rc")
        }, "node-main", 16L * 1024 * 1024).start()
    }

    private fun serverUp(): Boolean = try {
        val c = URL(BASE_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 800
        c.readTimeout = 2000
        val code = c.responseCode
        c.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }

    private fun startServer() {
        showPanel("Iniciando Studio 64…", spinner = true)
        startNodeOnce()
        Thread {
            var ok = false
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                if (serverUp()) { ok = true; break }
                Thread.sleep(500)
            }
            runOnUiThread {
                if (ok) showWebView()
                else showPanel(
                    "El servidor interno no respondio.\nCierra la app del todo y vuelve a abrirla. " +
                        "Si sigue igual, mira logcat (etiqueta Studio64Node).",
                    button = "Reintentar",
                ) { startServer() }
            }
        }.start()
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun showWebView() {
        root.removeAllViews()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val wv = WebView(this)
        web = wv
        wv.setBackgroundColor(0xFF1E2327.toInt())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Layout de escritorio: la pagina pide width=1280 y el WebView la escala para ajustarla a la pantalla
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
        }
        wv.addJavascriptInterface(Bridge(), "AndroidBridge")
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                chooserCallback?.onReceiveValue(null)
                chooserCallback = filePathCallback
                return try {
                    chooser.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    chooserCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }

            // Pantalla completa del juego (boton del marco): el elemento ocupa toda la pantalla, sin bordes
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) { callback?.onCustomViewHidden(); return }
                customView?.let { root.removeView(it) }
                customView = view
                customCb = callback
                view.setBackgroundColor(Color.BLACK)
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                hideSystemBars()
            }

            override fun onHideCustomView() = hideCustom()

            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                if (m != null) Log.d(TAG, "${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.host == "127.0.0.1" || u.scheme == "blob" || u.scheme == "about" || u.scheme == "data") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) toast("Error cargando Studio 64: ${error?.description}")
            }
        }
        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            handleDownload(url, contentDisposition, mimeType)
        }

        root.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // barra de teclas en pantalla + boton para mostrarla/ocultarla
        val bar = KeyBar(this) { type, k, ctrl, shift -> sendKey(type, k, ctrl, shift) }
        root.addView(
            bar.view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        val toggle = TextView(this).apply {
            text = "⌨"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener {
                bar.view.visibility = if (bar.view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        root.addView(
            toggle,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(8), dp(52))
            },
        )

        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { showMenu() }
        }
        root.addView(
            gear,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(8), dp(100))
            },
        )

        wv.loadUrl(BASE_URL)
    }

    private fun hideCustom() {
        customView?.let { root.removeView(it) }
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        hideSystemBars()
    }

    private fun sendKey(type: String, k: KeyBar.Key, ctrl: Boolean, shift: Boolean) {
        val js = "(function(){var e=new KeyboardEvent('$type',{key:${JSONObject.quote(k.key)},code:'${k.code}'," +
            "keyCode:${k.kc},which:${k.kc},ctrlKey:$ctrl,shiftKey:$shift,bubbles:true,cancelable:true});" +
            "(document.activeElement||document.body).dispatchEvent(e);})()"
        web?.evaluateJavascript(js, null)
    }

    // ---------------------------------------------------------------- descargas

    private fun handleDownload(url: String, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        if (url.startsWith("blob:")) {
            val js = "(async()=>{try{const r=await fetch(${JSONObject.quote(url)});const b=await r.blob();" +
                "const f=new FileReader();f.onloadend=()=>AndroidBridge.saveBase64(${JSONObject.quote(name)}," +
                "b.type||'application/octet-stream',String(f.result).split(',')[1]);f.readAsDataURL(b);}" +
                "catch(e){AndroidBridge.showToast('Error: '+e);}})()"
            web?.evaluateJavascript(js, null)
            return
        }
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                c.connect()
                val type = mime ?: c.contentType
                saveToDownloads(name, type) { out -> c.inputStream.use { it.copyTo(out) } }
            } catch (e: Exception) {
                toast("No se pudo descargar: ${e.message}")
            }
        }.start()
    }

    private fun saveToDownloads(name: String, mime: String?, write: (OutputStream) -> Unit) {
        val type = mime?.substringBefore(';')?.ifBlank { null } ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, type)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Studio64")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore no disponible")
            val out = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("No se pudo escribir")
            out.use(write)
            toast("Guardado en Descargas/Studio64/$name")
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Studio64").apply { mkdirs() }
            File(dir, name).outputStream().use(write)
            toast("Guardado en ${dir.absolutePath}/$name")
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun saveBase64(name: String, mime: String, b64: String) {
            Thread {
                try {
                    saveToDownloads(name, mime) { it.write(Base64.decode(b64, Base64.DEFAULT)) }
                } catch (e: Exception) {
                    toast("No se pudo guardar: ${e.message}")
                }
            }.start()
        }

        @JavascriptInterface
        fun showToast(msg: String) = toast(msg)
    }
}
