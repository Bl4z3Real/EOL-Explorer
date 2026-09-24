package it.eol.explorer

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

const val HOME = "file:///android_asset/home.html"

class MainActivity : Activity() {

    data class Item(val t: String, val u: String)

    inner class Tab(val web: WebView) {
        var title = "Nuova scheda"
        var url = ""
        var progress = 100
        var loading = false
    }

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private val engines = linkedMapOf(
        "google" to Pair("Google", "https://www.google.com/search?q=%s"),
        "ddg" to Pair("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        "bing" to Pair("Bing", "https://www.bing.com/search?q=%s"),
        "wiki" to Pair("Wikipedia", "https://it.wikipedia.org/w/index.php?search=%s")
    )

    private val tabs = mutableListOf<Tab>()
    private lateinit var cur: Tab
    private val prefs by lazy { getSharedPreferences("eol", MODE_PRIVATE) }
    private var favs = mutableListOf<Item>()
    private var history = mutableListOf<Item>()
    private var tm = false
    private var desktop = false
    private var engine = "google"
    private var visits = 0

    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var urlBox: EditText
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var progressStrip: ProgressStrip
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private lateinit var reloadBtn: TextView
    private lateinit var fullscreenHolder: FrameLayout
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null

    // ------------------------------------------------------------------ ciclo di vita

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPrefs()
        buildUi()
        val first = intent?.dataString
        newTab(if (first != null && first.startsWith("http")) first else HOME)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val u = intent?.dataString
        if (u != null && u.startsWith("http")) newTab(u)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onBackPressed() {
        if (customView != null) {
            hideCustom()
            return
        }
        if (cur.web.canGoBack()) {
            cur.web.goBack()
            return
        }
        if (tabs.size > 1) {
            closeTab(cur)
            return
        }
        super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCb = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    // ------------------------------------------------------------------ dati salvati

    private fun loadPrefs() {
        tm = prefs.getBoolean("tm", false)
        desktop = prefs.getBoolean("desktop", false)
        engine = prefs.getString("engine", "google") ?: "google"
        if (!engines.containsKey(engine)) engine = "google"
        visits = prefs.getInt("visits", 0) + 1
        prefs.edit().putInt("visits", visits).apply()
        favs = if (prefs.contains("favs")) readList("favs") else mutableListOf(
            Item("Google", "https://www.google.com/"),
            Item("Wikipedia", "https://it.wikipedia.org/"),
            Item("Wayback Machine", "https://web.archive.org/")
        )
        history = readList("hist")
    }

    private fun readList(key: String): MutableList<Item> {
        val out = mutableListOf<Item>()
        try {
            val a = JSONArray(prefs.getString(key, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Item(o.getString("t"), o.getString("u")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun writeList(key: String, list: List<Item>) {
        val a = JSONArray()
        for (entry in list) a.put(JSONObject().put("t", entry.t).put("u", entry.u))
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun addHistory(url: String) {
        history.removeAll { it.u == url }
        history.add(0, Item(hostOf(url), url))
        while (history.size > 150) history.removeAt(history.size - 1)
        writeList("hist", history)
    }

    private fun updateHistoryTitle(url: String, title: String) {
        val i = history.indexOfFirst { it.u == url }
        if (i >= 0) {
            history[i] = Item(title, url)
            writeList("hist", history)
        }
    }

    // ------------------------------------------------------------------ interfaccia

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun bevelStates(fill: Int = Pal.FACE): StateListDrawable {
        val sd = StateListDrawable()
        sd.addState(intArrayOf(android.R.attr.state_pressed), BevelDrawable(fill, true, dp(1).toFloat()))
        sd.addState(intArrayOf(), BevelDrawable(fill, false, dp(1).toFloat()))
        return sd
    }

    private fun button(label: String, onClick: (View) -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.textSize = 17f
        tv.setTextColor(Color.BLACK)
        tv.isClickable = true
        tv.isFocusable = true
        tv.setPadding(dp(6), dp(4), dp(6), dp(4))
        tv.background = bevelStates()
        tv.setOnClickListener { onClick(it) }
        return tv
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setBackgroundColor(Pal.FACE)

        // barra del titolo
        val title = LinearLayout(this)
        title.orientation = LinearLayout.HORIZONTAL
        title.gravity = Gravity.CENTER_VERTICAL
        title.setPadding(dp(8), dp(3), dp(8), dp(3))
        title.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Pal.NAVY, Pal.BLUE))
        val icon = ImageView(this)
        icon.setImageResource(R.mipmap.ic_launcher)
        title.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        titleView = TextView(this)
        titleView.setTextColor(Color.WHITE)
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.textSize = 13f
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.setPadding(dp(8), 0, 0, 0)
        title.addView(titleView, LinearLayout.LayoutParams(0, wrap, 1f))
        main.addView(title, LinearLayout.LayoutParams(match, wrap))

        // schede
        tabScroll = HorizontalScrollView(this)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.setBackgroundColor(Pal.FACE)
        tabScroll.setPadding(dp(4), dp(4), dp(4), 0)
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.gravity = Gravity.BOTTOM
        tabScroll.addView(tabBar, FrameLayout.LayoutParams(wrap, wrap))
        main.addView(tabScroll, LinearLayout.LayoutParams(match, wrap))

        // pulsanti di navigazione
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.setPadding(dp(4), dp(4), dp(4), dp(2))
        backBtn = button("◄") { if (cur.web.canGoBack()) cur.web.goBack() }
        fwdBtn = button("►") { if (cur.web.canGoForward()) cur.web.goForward() }
        reloadBtn = button("↻") { if (cur.loading) cur.web.stopLoading() else cur.web.reload() }
        val homeBtn = button("🏠") { load(cur, HOME) }
        val favBtn = button("⭐") { showFavs() }
        val menuBtn = button("⋮") { showMenu(it) }
        for (b in listOf(backBtn, fwdBtn, reloadBtn, homeBtn, favBtn, menuBtn)) {
            val lp = LinearLayout.LayoutParams(0, dp(42), 1f)
            lp.setMargins(dp(2), 0, dp(2), 0)
            nav.addView(b, lp)
        }
        main.addView(nav, LinearLayout.LayoutParams(match, wrap))

        // barra dell'indirizzo
        val addr = LinearLayout(this)
        addr.orientation = LinearLayout.HORIZONTAL
        addr.gravity = Gravity.CENTER_VERTICAL
        addr.setPadding(dp(6), dp(2), dp(6), dp(6))
        urlBox = EditText(this)
        urlBox.setSingleLine(true)
        urlBox.textSize = 14f
        urlBox.setTextColor(Color.BLACK)
        urlBox.hint = "Scrivi un indirizzo o cerca…"
        urlBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlBox.imeOptions = EditorInfo.IME_ACTION_GO
        urlBox.setSelectAllOnFocus(true)
        urlBox.setPadding(dp(8), dp(8), dp(8), dp(8))
        urlBox.background = BevelDrawable(Color.WHITE, true, dp(1).toFloat())
        urlBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submit()
                true
            } else {
                false
            }
        }
        addr.addView(urlBox, LinearLayout.LayoutParams(0, dp(42), 1f))
        val go = button("Vai") { submit() }
        go.textSize = 14f
        val golp = LinearLayout.LayoutParams(wrap, dp(42))
        golp.setMargins(dp(6), 0, 0, 0)
        addr.addView(go, golp)
        main.addView(addr, LinearLayout.LayoutParams(match, wrap))

        progressStrip = ProgressStrip(this)
        main.addView(progressStrip, LinearLayout.LayoutParams(match, dp(4)))

        content = FrameLayout(this)
        content.setBackgroundColor(Color.WHITE)
        main.addView(content, LinearLayout.LayoutParams(match, 0, 1f))

        statusView = TextView(this)
        statusView.textSize = 11f
        statusView.setTextColor(Color.BLACK)
        statusView.maxLines = 1
        statusView.ellipsize = TextUtils.TruncateAt.END
        statusView.text = "Fatto"
        statusView.setPadding(dp(8), dp(3), dp(8), dp(3))
        statusView.background = BevelDrawable(Pal.FACE, true, dp(1).toFloat())
        main.addView(statusView, LinearLayout.LayoutParams(match, wrap))

        fullscreenHolder = FrameLayout(this)
        fullscreenHolder.setBackgroundColor(Color.BLACK)
        fullscreenHolder.visibility = View.GONE

        root.addView(main, FrameLayout.LayoutParams(match, match))
        root.addView(fullscreenHolder, FrameLayout.LayoutParams(match, match))
        setContentView(root)
    }

    private fun status(msg: String) {
        statusView.text = msg
    }

    private fun titleOf(t: Tab): String = when {
        t.url.isEmpty() -> "Nuova scheda"
        t.url.startsWith(HOME) -> "Pagina iniziale"
        else -> t.title
    }

    private fun refreshTabs() {
        tabBar.removeAllViews()
        var activeView: View? = null
        for (t in tabs) {
            val active = t === cur
            val item = LinearLayout(this)
            item.orientation = LinearLayout.HORIZONTAL
            item.gravity = Gravity.CENTER_VERTICAL
            val vpad = if (active) dp(8) else dp(6)
            item.setPadding(dp(8), vpad, dp(2), vpad)
            item.background = BevelDrawable(if (active) Pal.FACE else Pal.TAB, false, dp(1).toFloat())
            val label = TextView(this)
            label.text = titleOf(t)
            label.textSize = 13f
            label.setTextColor(Color.BLACK)
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.maxWidth = dp(140)
            label.minWidth = dp(60)
            if (active) label.typeface = Typeface.DEFAULT_BOLD
            val close = TextView(this)
            close.text = "✕"
            close.textSize = 12f
            close.setTextColor(Color.BLACK)
            close.setPadding(dp(10), dp(4), dp(8), dp(4))
            close.setOnClickListener { closeTab(t) }
            item.addView(label)
            item.addView(close)
            item.setOnClickListener { select(t) }
            val lp = LinearLayout.LayoutParams(wrap, wrap)
            lp.setMargins(0, if (active) 0 else dp(3), dp(2), 0)
            tabBar.addView(item, lp)
            if (active) activeView = item
        }
        val plus = button("+") { newTab(HOME) }
        val plp = LinearLayout.LayoutParams(dp(40), dp(32))
        plp.setMargins(dp(2), 0, 0, dp(2))
        tabBar.addView(plus, plp)
        val av = activeView
        if (av != null) tabScroll.post { tabScroll.smoothScrollTo(av.left, 0) }
    }

    private fun refreshUi() {
        if (!::cur.isInitialized) return
        val home = cur.url.startsWith(HOME)
        titleView.text = titleOf(cur) + " - EOL Explorer"
        if (!urlBox.hasFocus()) urlBox.setText(if (home) "" else cur.url)
        backBtn.alpha = if (cur.web.canGoBack()) 1f else 0.4f
        fwdBtn.alpha = if (cur.web.canGoForward()) 1f else 0.4f
        reloadBtn.text = if (cur.loading) "✕" else "↻"
        progressStrip.progress = if (cur.loading) cur.progress else 0
        refreshTabs()
    }

    // ------------------------------------------------------------------ schede

    private fun newTab(url: String?): Tab {
        val t = Tab(WebView(this))
        configure(t)
        tabs.add(t)
        select(t)
        if (url != null) load(t, url)
        return t
    }

    private fun select(t: Tab) {
        cur = t
        content.removeAllViews()
        content.addView(t.web, FrameLayout.LayoutParams(match, match))
        refreshUi()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        if (i < 0) return
        val wasCurrent = t === cur
        tabs.removeAt(i)
        content.removeView(t.web)
        t.web.stopLoading()
        t.web.destroy()
        if (tabs.isEmpty()) {
            newTab(HOME)
        } else if (wasCurrent) {
            select(tabs[minOf(i, tabs.size - 1)])
        } else {
            refreshUi()
        }
    }

    private fun resolve(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t == "about:home") return HOME
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*").matches(t)) return t
        if (Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$").matches(t)) return "http://$t"
        if (Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$").matches(t)) return "https://$t"
        val pattern = engines[engine]?.second ?: "https://www.google.com/search?q=%s"
        return pattern.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }

    private fun load(t: Tab, raw: String) {
        val u = resolve(raw)
        if (u == HOME) {
            t.web.loadUrl(HOME)
            return
        }
        val target = if (tm && !u.contains("web.archive.org")) "https://web.archive.org/web/1999/$u" else u
        t.web.loadUrl(target)
    }

    private fun submit() {
        val text = urlBox.text.toString()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBox.windowToken, 0)
        urlBox.clearFocus()
        cur.web.requestFocus()
        if (text.isNotBlank()) load(cur, text)
    }

    private fun hostOf(u: String): String = try {
        Uri.parse(u).host?.removePrefix("www.") ?: u
    } catch (e: Exception) {
        u
    }

    // ------------------------------------------------------------------ WebView

    private fun currentUA(): String {
        val def = WebSettings.getDefaultUserAgent(this)
        if (!desktop) return def.replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
        val chrome = Regex("Chrome/[\\d.]+").find(def)?.value ?: "Chrome/124.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    private fun configure(t: Tab) {
        val w = t.web
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(true)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.userAgentString = currentUA()
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val s = uri.toString()
                return when (uri.scheme ?: "") {
                    "http", "https", "about", "data", "blob", "javascript" -> false
                    "file" -> !s.startsWith("file:///android_asset/")
                    "eol" -> {
                        if (uri.host == "tm") toggleTm()
                        true
                    }
                    else -> {
                        openExternalScheme(s)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                t.loading = true
                t.progress = 5
                if (url != null) {
                    t.url = url
                    if (!url.startsWith(HOME)) t.title = hostOf(url)
                }
                if (t === cur) {
                    status("Apertura di " + hostOf(t.url) + "…")
                    refreshUi()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                t.loading = false
                t.progress = 100
                if (url != null) t.url = url
                if (t.url.startsWith(HOME)) injectHome(t)
                if (t === cur) {
                    status("Fatto")
                    refreshUi()
                } else {
                    refreshTabs()
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url == null) return
                t.url = url
                if (!isReload && url.startsWith("http")) addHistory(url)
                if (t === cur) refreshUi()
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                t.progress = newProgress
                if (t === cur) progressStrip.progress = if (t.loading) newProgress else 0
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (title.isNullOrBlank() || t.url.startsWith(HOME)) return
                t.title = title
                updateHistoryTitle(t.url, title)
                if (t === cur) refreshUi() else refreshTabs()
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false
                if (!isUserGesture) {
                    Toast.makeText(this@MainActivity, "Pop-up bloccato", Toast.LENGTH_SHORT).show()
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val nt = newTab(null)
                transport.webView = nt.web
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                val t2 = tabs.firstOrNull { it.web === window } ?: return
                closeTab(t2)
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                customView = view
                customCb = callback
                fullscreenHolder.addView(view, FrameLayout.LayoutParams(match, match))
                fullscreenHolder.visibility = View.VISIBLE
                setImmersive(true)
            }

            override fun onHideCustomView() {
                hideCustom()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = filePathCallback
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(fileChooserParams!!.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCb = null
                    false
                }
            }
        }
    }

    private fun hideCustom() {
        val v = customView ?: return
        fullscreenHolder.removeView(v)
        fullscreenHolder.visibility = View.GONE
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        setImmersive(false)
    }

    @Suppress("DEPRECATION")
    private fun setImmersive(on: Boolean) {
        window.decorView.systemUiVisibility = if (on) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun openExternalScheme(u: String) {
        try {
            val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(u))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Nessuna app può aprire questo link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val r = DownloadManager.Request(Uri.parse(url))
            if (mime != null) r.setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.let { r.addRequestHeader("Cookie", it) }
            if (ua != null) r.addRequestHeader("User-Agent", ua)
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
            Toast.makeText(this, "Download: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download non riuscito", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ pagina iniziale

    private fun injectHome(t: Tab) {
        val fav = JSONArray()
        for (f in favs) fav.put(JSONObject().put("t", f.t).put("u", f.u))
        val d = JSONObject().put("favs", fav).put("tm", tm).put("visits", visits)
        t.web.evaluateJavascript("window.eolInit && window.eolInit($d)", null)
    }

    private fun toggleTm() {
        tm = !tm
        prefs.edit().putBoolean("tm", tm).apply()
        Toast.makeText(
            this,
            if (tm) "Macchina del tempo attiva: i siti si aprono come nel 1999" else "Macchina del tempo disattivata",
            Toast.LENGTH_SHORT
        ).show()
        if (cur.url.startsWith(HOME)) injectHome(cur)
    }

    // ------------------------------------------------------------------ menu e finestre

    private fun addFav() {
        if (cur.url.isEmpty() || cur.url.startsWith(HOME)) {
            Toast.makeText(this, "Apri una pagina per aggiungerla ai preferiti", Toast.LENGTH_SHORT).show()
            return
        }
        if (favs.none { it.u == cur.url }) favs.add(Item(cur.title, cur.url))
        writeList("favs", favs)
        Toast.makeText(this, "Aggiunto ai preferiti", Toast.LENGTH_SHORT).show()
    }

    private fun showFavs() {
        if (favs.isEmpty()) {
            Toast.makeText(this, "Nessun preferito: usa ⋮ › Aggiungi ai preferiti", Toast.LENGTH_LONG).show()
            return
        }
        val list = favs.toList()
        val names = list.map { it.t }.toTypedArray()
        val d = AlertDialog.Builder(this)
            .setTitle("⭐ Preferiti (tocco lungo per rimuovere)")
            .setItems(names) { dlg, i ->
                dlg.dismiss()
                load(cur, list[i].u)
            }
            .setNegativeButton("Chiudi", null)
            .create()
        d.show()
        d.listView.setOnItemLongClickListener { _, _, pos, _ ->
            favs.remove(list[pos])
            writeList("favs", favs)
            d.dismiss()
            showFavs()
            true
        }
    }

    private fun showHistory() {
        if (history.isEmpty()) {
            Toast.makeText(this, "La cronologia è vuota", Toast.LENGTH_SHORT).show()
            return
        }
        val list = history.toList()
        val names = list.map { it.t }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🕘 Cronologia")
            .setItems(names) { dlg, i ->
                dlg.dismiss()
                load(cur, list[i].u)
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showEngines() {
        val keys = engines.keys.toList()
        val names = keys.map { engines[it]?.first ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Motore di ricerca")
            .setSingleChoiceItems(names, keys.indexOf(engine)) { d, which ->
                engine = keys[which]
                prefs.edit().putString("engine", engine).apply()
                d.dismiss()
            }
            .show()
    }

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        val m = pm.menu
        m.add(0, 1, 0, "Nuova scheda")
        m.add(0, 2, 0, "Chiudi scheda")
        m.add(0, 3, 0, "Aggiungi ai preferiti")
        m.add(0, 4, 0, "Preferiti")
        m.add(0, 5, 0, "Cronologia")
        m.add(0, 6, 0, "Condividi pagina")
        val tmItem = m.add(0, 7, 0, "Macchina del tempo (web 1999)")
        tmItem.isCheckable = true
        tmItem.isChecked = tm
        val dtItem = m.add(0, 8, 0, "Versione desktop")
        dtItem.isCheckable = true
        dtItem.isChecked = desktop
        m.add(0, 9, 0, "Motore di ricerca")
        m.add(0, 10, 0, "Cancella cronologia")
        m.add(0, 11, 0, "Informazioni")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> newTab(HOME)
                2 -> closeTab(cur)
                3 -> addFav()
                4 -> showFavs()
                5 -> showHistory()
                6 -> share()
                7 -> toggleTm()
                8 -> toggleDesktop()
                9 -> showEngines()
                10 -> {
                    history.clear()
                    writeList("hist", history)
                    Toast.makeText(this, "Cronologia cancellata", Toast.LENGTH_SHORT).show()
                }
                11 -> AlertDialog.Builder(this)
                    .setTitle("EOL Explorer")
                    .setMessage("Versione 1.0\n\nIl browser con l'aspetto degli anni '90 e il motore di oggi.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            true
        }
        pm.show()
    }

    private fun share() {
        if (cur.url.isEmpty() || cur.url.startsWith(HOME)) return
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, cur.url)
        startActivity(Intent.createChooser(i, "Condividi"))
    }

    private fun toggleDesktop() {
        desktop = !desktop
        prefs.edit().putBoolean("desktop", desktop).apply()
        for (t in tabs) t.web.settings.userAgentString = currentUA()
        if (!cur.url.startsWith(HOME)) cur.web.reload()
        Toast.makeText(this, if (desktop) "Versione desktop attiva" else "Versione mobile attiva", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_FILE = 42
    }
}
