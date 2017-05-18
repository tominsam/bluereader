package org.movieos.feeder.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
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
        entry = Entry.byId(arguments.getInt(ENTRY_ID))
        realm.close()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailPageFragmentBinding {
        val binding = DetailPageFragmentBinding.inflate(inflater, container, false)

        // Needed for youtube embeds to work
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.setWebChromeClient(object : WebChromeClient() {

        })
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

        binding.webView.loadDataWithBaseURL(null, template
                .replace("{{body}}", entry!!.content!!)
                .replace("{{title}}", entry!!.title!!)
                .replace("{{link}}", entry!!.url!!)
                .replace("{{author}}", entry!!.displayAuthor)
                .replace("{{date}}", DateFormat.getDateTimeInstance().format(entry!!.published)), "text/html", "utf-8", "")
        return binding
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
            if (isVisible && e.isLocallyUnread) {
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