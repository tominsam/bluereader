package org.movieos.feeder.api

import android.content.Context
import android.text.TextUtils
import com.google.gson.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.movieos.feeder.BuildConfig
import org.movieos.feeder.model.Entry
import org.movieos.feeder.model.Subscription
import org.movieos.feeder.model.Tagging
import org.movieos.feeder.utilities.Settings
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

class Feedbin(context: Context) {

    private var mApi: RawApi

    init {
        mApi = api(context, Settings.getCredentials(context))
    }

    fun subscriptions(since: Date?): Call<List<Subscription>> {
        return mApi.subscriptions(formatDate(since))
    }

    fun subscriptionsPaginate(next: String): Call<List<Subscription>> {
        return mApi.subscriptionsPaginate(next)
    }

    fun entries(since: Date?): Call<List<Entry>> {
        return mApi.entries(formatDate(since))
    }

    fun entriesPaginate(url: String): Call<List<Entry>> {
        return mApi.entriesPaginate(url)
    }

    fun starred(): Call<List<Int>> {
        return mApi.starred()
    }

    fun unread(): Call<List<Int>> {
        return mApi.unread()
    }

    fun taggings(): Call<List<Tagging>> {
        return mApi.taggings()
    }

    fun addStarred(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("starred_entries", jsonIds)
        return mApi.addStarred(base)
    }

    fun removeStarred(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("starred_entries", jsonIds)
        return mApi.removeStarred(base)
    }

    fun addUnread(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("unread_entries", jsonIds)
        return mApi.addUnread(base)
    }

    fun removeUnread(ids: Iterable<Int>): Call<Void> {
        val base = JsonObject()
        val jsonIds = JsonArray()
        for (id in ids) {
            jsonIds.add(id)
        }
        base.add("unread_entries", jsonIds)
        return mApi.removeUnread(base)
    }

    fun entries(ids: List<Int>): Call<List<Entry>> {
        return mApi.entriesById(TextUtils.join(",", ids))
    }

    private interface RawApi {
        @GET("authentication.json")
        fun authentication(): Call<Void>

        @GET("subscriptions.json")
        fun subscriptions(@Query("since") since: String?): Call<List<Subscription>>

        @GET
        fun subscriptionsPaginate(@Url url: String): Call<List<Subscription>>

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

        protected val CACHE_SIZE_BYTES = 1024 * 1024 * 2
        protected val UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

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

            val client = OkHttpClient.Builder()
                    .cache(Cache(context.cacheDir, CACHE_SIZE_BYTES.toLong()))
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                                .addHeader("Authorization", credentials)
                                .build()
                        chain.proceed(request)
                    }
                    .sslSocketFactory(dangerousSocketFactory()!!)
                    .build()

            val sRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.feedbin.com/v2/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

            return sRetrofit.create(RawApi::class.java)
        }

        private fun dangerousSocketFactory(): SSLSocketFactory? {
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
                return sslContext.socketFactory
            } catch (e: NoSuchAlgorithmException) {
                Timber.e(e)
            } catch (e: KeyManagementException) {
                Timber.e(e)
            }

            return null
        }
    }


}
