package org.movieos.feeder.fragment

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.webkit.WebViewClient
import io.realm.Realm
import org.movieos.feeder.R
import org.movieos.feeder.databinding.DetailPageFragmentBinding
import org.movieos.feeder.model.Entry
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DateFormat


class DetailPageFragment : DataBindingFragment<DetailPageFragmentBinding>() {

    internal var entry: Entry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userVisibleHint = false
        val realm = Realm.getDefaultInstance()
        entry = Entry.byId(realm, arguments.getInt(ENTRY_ID))
        realm.close()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailPageFragmentBinding {
        val binding = DetailPageFragmentBinding.inflate(inflater, container, false)

        // Try to minimize saved state
        binding.webView.isSaveEnabled = false
        // Needed for youtube embeds to work
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.setWebChromeClient(object : WebChromeClient() {})
        binding.webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val builder = CustomTabsIntent.Builder()
                builder.setToolbarColor(ContextCompat.getColor(activity, R.color.primary))
                builder.addDefaultShareMenuItem()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(activity, request.url)
                return true
            }
        })
        registerForContextMenu(binding.webView)

        binding.webView.loadDataWithBaseURL(null, template
                .replace("{{body}}", entry!!.content!!)
                .replace("{{title}}", Html.escapeHtml(entry!!.title!!))
                .replace("{{link}}", Html.escapeHtml(entry!!.url!!))
                .replace("{{author}}", Html.escapeHtml(entry!!.displayAuthor))
                .replace("{{date}}", Html.escapeHtml(DateFormat.getDateTimeInstance().format(entry!!.published)))
                , "text/html", "utf-8", "")
        return binding
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

        } else if (result.type == HitTestResult.ANCHOR_TYPE || result.type == HitTestResult.SRC_ANCHOR_TYPE) {
            // Menu options for a hyperlink.

            // Send the link to something
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, result.extra)
            val chooser = Intent.createChooser(shareIntent, result.extra)

            // Offer any app that can open the link above the share options
            val openIntent = Intent(Intent.ACTION_VIEW, uri)
            val resInfo = activity.packageManager.queryIntentActivities(openIntent, 0);
            val extra: MutableList<LabeledIntent> = mutableListOf()
            for (resolveInfo in resInfo) {
                val packageName = resolveInfo.activityInfo.packageName
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.component = ComponentName(packageName, resolveInfo.activityInfo.name)
                val label = resolveInfo.loadLabel(activity.packageManager)
                extra.add(LabeledIntent(intent, packageName, "Open in $label", 0))
            }
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray());

            startActivity(chooser)
        }
    }

    private val template: String
        get() {
            if (sTemplate == null) {
                try {
                    val inputStream = resources.openRawResource(R.raw.template)

                    val inputStringBuilder = StringBuilder()
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    var line: String? = bufferedReader.readLine()

                    while (line != null) {
                        inputStringBuilder.append(line)
                        inputStringBuilder.append('\n')
                        line = bufferedReader.readLine()
                    }

                    sTemplate = inputStringBuilder.toString()

                } catch (e: Exception) {
                    throw RuntimeException(e)
                }

            }
            return sTemplate!!
        }

    override fun onResume() {
        super.onResume()
        if (isVisible) {
            Timber.i("entry $entry is visible")
        }

        entry?.let { e ->
            if (isVisible && e.locallyUnread) {
                val realm = Realm.getDefaultInstance()
                //Entry.setUnread(getContext(), realm, mEntry, false);
                realm.close()
            }
        }

    }

    companion object {

        private val ENTRY_ID = "entry_id"

        internal var sTemplate: String? = null

        fun create(entryId: Int): DetailPageFragment {
            val fragment = DetailPageFragment()
            fragment.arguments = Bundle()
            fragment.arguments.putInt(ENTRY_ID, entryId)
            return fragment
        }
    }
}