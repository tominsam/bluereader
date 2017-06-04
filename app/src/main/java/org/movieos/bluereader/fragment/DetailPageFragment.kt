package org.movieos.bluereader.fragment

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebView.HitTestResult
import io.realm.Realm
import org.movieos.bluereader.R
import org.movieos.bluereader.databinding.DetailPageFragmentBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.utilities.Web
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DateFormat
import java.util.*


class DetailPageFragment : DataBindingFragment<DetailPageFragmentBinding>() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailPageFragmentBinding {
        val binding = DetailPageFragmentBinding.inflate(inflater, container, false)

        // Try to minimize saved state
        binding.webView.isSaveEnabled = false
        // Needed for youtube embeds to work
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.setBackgroundColor(0x00000000)
        binding.webView.setWebChromeClient(object : WebChromeClient() {})
        binding.webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                Web.openInBrowser(activity, request.url.toString())
                return true
            }
        })

        binding.webView.addJavascriptInterface(this, "android")

        registerForContextMenu(binding.webView)

        val entry = Realm.getDefaultInstance().use { Entry.byId(it, arguments.getInt(ENTRY_ID)).findFirst() }
        binding.webView.loadDataWithBaseURL(entry.url, template
                .replace("{{body}}", safeContent(entry.content) ?: "")
                .replace("{{title}}", Html.escapeHtml(entry.title ?: ""))
                .replace("{{link}}", Html.escapeHtml(entry.url ?: ""))
                .replace("{{author}}", Html.escapeHtml(entry.displayAuthor))
                .replace("{{background}}", String.format("%08X", ContextCompat.getColor(activity, R.color.background)).substring(2, 8))
                .replace("{{textPrimary}}", String.format("%08X", ContextCompat.getColor(activity, R.color.text_primary)).substring(2, 8))
                .replace("{{textSecondary}}", String.format("%08X", ContextCompat.getColor(activity, R.color.text_secondary)).substring(2, 8))
                .replace("{{date}}", Html.escapeHtml(DateFormat.getDateTimeInstance().format(entry.published ?: Date())))
                , "text/html", "utf-8", "")

        return binding
    }

    @Suppress("unused")
    @JavascriptInterface
    fun back() {
        activity.runOnUiThread {
            activity.onBackPressed()
        }
    }

    private fun safeContent(content: String?): String? {
        // Stupid hack to stop people breaking my CSS override
        return content?.replace(Regex("rel=[\"']stylesheet['\"]"), "")
    }

    override fun onDestroyView() {
        // Trying really hard to release the webviews
        binding?.webView?.setWebChromeClient(null)
        binding?.webView?.setWebViewClient(null)
        binding?.webView?.removeJavascriptInterface("android")
        binding?.webView?.destroy()
        super.onDestroyView()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v !is WebView || menu == null)
            return

        val result = v.hitTestResult
        val uri = Uri.parse(result.extra)

        if (result.type == HitTestResult.IMAGE_TYPE || result.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            // Menu options for an image.

            // Send the link to something
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            val chooser = Intent.createChooser(shareIntent, result.extra)
            startActivity(chooser)

        } else if (result.type == HitTestResult.SRC_ANCHOR_TYPE) {
            // Menu options for a hyperlink.

            // Send the link to something
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, result.extra)
            val chooser = Intent.createChooser(shareIntent, result.extra)

            // Offer any app that can open the link above the share options
            val openIntent = Intent(Intent.ACTION_VIEW, uri)
            val resInfo = activity.packageManager.queryIntentActivities(openIntent, 0)
            val extra: MutableList<LabeledIntent> = mutableListOf()
            for (resolveInfo in resInfo) {
                val packageName = resolveInfo.activityInfo.packageName
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.component = ComponentName(packageName, resolveInfo.activityInfo.name)
                val label = resolveInfo.loadLabel(activity.packageManager)
                extra.add(LabeledIntent(intent, packageName, "Open in $label", 0))
            }
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray())

            startActivity(chooser)
        }
    }

    private val template: String by lazy {
        val inputStream = resources.openRawResource(R.raw.template)
        val inputStringBuilder = StringBuilder()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        var line: String? = bufferedReader.readLine()
        while (line != null) {
            inputStringBuilder.append(line)
            inputStringBuilder.append('\n')
            line = bufferedReader.readLine()
        }
        inputStringBuilder.toString()
    }

    companion object {

        private val ENTRY_ID = "entry_id"

        fun create(entryId: Int): DetailPageFragment {
            val fragment = DetailPageFragment()
            fragment.arguments = Bundle()
            fragment.arguments.putInt(ENTRY_ID, entryId)
            return fragment
        }
    }
}