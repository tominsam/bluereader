package org.movieos.bluereader.api

import android.content.Context
import android.text.TextUtils
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.google.gson.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.movieos.bluereader.BuildConfig
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.model.Subscription
import org.movieos.bluereader.model.Tagging
import org.movieos.bluereader.utilities.Settings
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import timber.log.Timber
import java.lang.reflect.Type
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Suppress("LoopToCallChain")
class Feedbin(context: Context) {

    private var api: RawApi

    init {
        api = api(context, Settings.getCredentials(context))
    }

    fun subscriptions(): Call<List<Subscription>> {
        return api.subscriptions()
    }

    fun entries(since: Date?): Call<List<Entry>> {
        return api.entries(formatDate(since))
    }

    fun entriesPaginate(url: String): Call<List<Entry>> {
        return api.entriesPaginate(url)
    }

    fun starred(): Call<List<Int>> {
        return api.starred()
    }

    fun unread(): Call<List<Int>> {
        return api.unread()
    }

    fun taggings(): Call<List<Tagging>> {
        return api.taggings()
    }

    fun addStarred(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("starred_entries", jsonIds)
        return api.addStarred(base)
    }

    fun removeStarred(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("starred_entries", jsonIds)
        return api.removeStarred(base)
    }

    fun addUnread(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("unread_entries", jsonIds)
        return api.addUnread(base)
    }

    fun removeUnread(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("unread_entries", jsonIds)
        return api.removeUnread(base)
    }

    fun entries(ids: List<Int>): Call<List<Entry>> {
        return api.entriesById(TextUtils.join(",", ids))
    }

    private interface RawApi {
        @GET("authentication.json")
        fun authentication(): Call<Void>

        @GET("subscriptions.json")
        fun subscriptions(): Call<List<Subscription>>

        @GET("entries.json")
        fun entries(@Query("since") since: String?): Call<List<Entry>>

        @GET
        fun entriesPaginate(@Url url: String): Call<List<Entry>>

        @GET("starred_entries.json")
        fun starred(): Call<List<Int>>

        @GET("unread_entries.json")
        fun unread(): Call<List<Int>>

        @POST("starred_entries.json")
        fun addStarred(@Body json: JsonObject): Call<Void>

        @POST("starred_entries/delete.json")
        fun removeStarred(@Body json: JsonObject): Call<Void>

        @POST("unread_entries.json")
        fun addUnread(@Body json: JsonObject): Call<Void>

        @POST("unread_entries/delete.json")
        fun removeUnread(@Body json: JsonObject): Call<Void>

        @GET("entries.json")
        fun entriesById(@Query("ids") ids: String): Call<List<Entry>>

        @GET("taggings.json")
        fun taggings(): Call<List<Tagging>>
    }

    private class JavaDateDeserializer : JsonDeserializer<Date> {

        @Throws(JsonParseException::class)
        override fun deserialize(je: JsonElement, type: Type, jdc: JsonDeserializationContext): Date {
            val myDate = je.asString
            try {
                val localFormat = SimpleDateFormat(UTC_DATE_FORMAT, Locale.US)
                localFormat.timeZone = TimeZone.getTimeZone("UTC")
                return localFormat.parse(myDate)
            } catch (e: ParseException) {
                throw JsonParseException(e)
            }

        }
    }

    companion object {

        private val CACHE_SIZE_BYTES = 1024 * 1024 * 2
        private val UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        fun authenticate(context: Context, credentials: String, callback: Callback<Void>) {
            api(context, credentials).authentication().enqueue(callback)
        }


        private fun formatDate(date: Date?): String? {
            if (date == null) {
                return null
            }
            val localFormat = SimpleDateFormat(UTC_DATE_FORMAT, Locale.US)
            localFormat.timeZone = TimeZone.getTimeZone("UTC")
            return localFormat.format(date)
        }

        private fun api(context: Context, credentials: String?): RawApi {

            val gson = GsonBuilder()
                    .setDateFormat(UTC_DATE_FORMAT)
                    .registerTypeAdapter(Date::class.java, JavaDateDeserializer())
                    .create()


            var builder = OkHttpClient.Builder()
                    .cache(Cache(context.cacheDir, CACHE_SIZE_BYTES.toLong()))
                    .addNetworkInterceptor(StethoInterceptor())
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                        if (credentials != null) {
                            request.addHeader("Authorization", credentials)
                        }
                        chain.proceed(request.build())
                    }

            val dangerousSocketFactory = dangerousSocketFactory()
            if (dangerousSocketFactory != null) {
                builder = builder.sslSocketFactory(dangerousSocketFactory.first, dangerousSocketFactory.second)
            }

            val client = builder.build()

            val sRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.feedbin.com/v2/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

            return sRetrofit.create(RawApi::class.java)
        }

        private fun dangerousSocketFactory(): Pair<SSLSocketFactory, X509TrustManager>? {
            if (!BuildConfig.DEBUG) {
                return null
            }
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            })

            try {
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                val trustManager = trustAllCerts[0]
                if (trustManager is X509TrustManager) {
                    return Pair(sslContext.socketFactory, trustManager)
                }
            } catch (e: NoSuchAlgorithmException) {
                Timber.e(e)
            } catch (e: KeyManagementException) {
                Timber.e(e)
            }

            return null
        }
    }


}
