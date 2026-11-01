package com.custom.cleanlauncher

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

data class StreamOption(
    val title: String,
    val providerName: String,
    val actionIntent: Intent
)

data class MediaItem(
    val id: Int,
    val imdbId: String?,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val mediaType: String = "movie",
    val deepLinkUri: String? = null,
    val sourcePackage: String? = null
)

object ImageLoader {
    private val handler = Handler(Looper.getMainLooper())
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(url: String, imageView: ImageView, crossfade: Boolean = true) {
        imageView.tag = url
        if (url.isEmpty() || !url.startsWith("http")) {
            imageView.setImageDrawable(null)
            return
        }

        val cached = memoryCache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            imageView.alpha = 1f
            return
        }

        val appCtx = imageView.context.applicationContext
        thread {
            try {
                val diskFile = File(appCtx.cacheDir, "img_" + hashKey(url))
                val bmp = if (diskFile.exists()) {
                    BitmapFactory.decodeFile(diskFile.absolutePath)
                } else {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    val downloaded = BitmapFactory.decodeStream(conn.inputStream)
                    if (downloaded != null) {
                        FileOutputStream(diskFile).use { out ->
                            downloaded.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }
                    downloaded
                }

                if (bmp != null) {
                    memoryCache.put(url, bmp)
                    handler.post {
                        if (imageView.tag == url) {
                            if (crossfade) {
                                imageView.alpha = 0.15f
                                imageView.setImageBitmap(bmp)
                                imageView.animate().alpha(1f).setDuration(240).start()
                            } else {
                                imageView.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun hashKey(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }
}

class ContentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    fun recordWatchedMediaForActiveProfile(profileId: String, movieId: Int) {
        prefs.edit().putInt("last_watched_tmdb_$profileId", movieId).apply()
    }

    fun fetchPersonalizedRecommendations(
        profile: UserProfile,
        categoryFilter: String = "ALL",
        callback: (List<MediaItem>) -> Unit
    ) {
        val apiKey = prefs.getString("tmdb_api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) {
            callback(emptyList())
            return
        }
        val lastId = prefs.getInt("last_watched_tmdb_${profile.id}", -1)
        val endpoint = when (categoryFilter) {
            "MOVIES" -> "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=pt-BR&sort_by=popularity.desc&with_genres=${profile.favoriteGenres}"
            "SERIES" -> "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=pt-BR&sort_by=popularity.desc"
            else -> {
                if (prefs.getBoolean("personalized_recs", true) && lastId != -1) {
                    "https://api.themoviedb.org/3/movie/$lastId/recommendations?api_key=$apiKey&language=pt-BR"
                } else {
                    "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=pt-BR&sort_by=popularity.desc&with_genres=${profile.favoriteGenres}"
                }
            }
        }
        val defaultType = if (categoryFilter == "SERIES") "tv" else "movie"
        fetchTmdbList(endpoint, defaultType, callback)
    }

    fun fetchTrending(categoryFilter: String = "ALL", callback: (List<MediaItem>) -> Unit) {
        val apiKey = prefs.getString("tmdb_api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) {
            callback(emptyList())
            return
        }
        val path = when (categoryFilter) {
            "MOVIES" -> "trending/movie/week"
            "SERIES" -> "trending/tv/week"
            else -> "trending/all/week"
        }
        fetchTmdbList("https://api.themoviedb.org/3/$path?api_key=$apiKey&language=pt-BR", "movie", callback)
    }

    private fun fetchTmdbList(urlStr: String, fallbackType: String, callback: (List<MediaItem>) -> Unit) {
        thread {
            val items = mutableListOf<MediaItem>()
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val response = conn.inputStream.bufferedReader().readText()
                val results = JSONObject(response).getJSONArray("results")
                for (i in 0 until minOf(results.length(), 15)) {
                    val obj = results.getJSONObject(i)
                    val title = obj.optString("title").ifEmpty { obj.optString("name") }
                    val posterPath = obj.optString("poster_path", "")
                    val backdropPath = obj.optString("backdrop_path", "")
                    val poster = if (posterPath.isNotEmpty() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else ""
                    val backdrop = if (backdropPath.isNotEmpty() && backdropPath != "null") "https://image.tmdb.org/t/p/w1280$backdropPath" else poster
                    val type = obj.optString("media_type", fallbackType)
                    if (title.isNotEmpty()) {
                        items.add(
                            MediaItem(
                                id = obj.optInt("id", 0),
                                imdbId = null,
                                title = title,
                                overview = obj.optString("overview", ""),
                                posterUrl = poster,
                                backdropUrl = backdrop,
                                mediaType = if (type.isEmpty()) fallbackType else type
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
            callback(items)
        }
    }

    fun getContinueWatchingList(profileId: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val uri = Uri.parse("content://android.media.tv/watch_next_program")
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val titleIdx = it.getColumnIndex("title")
                val descIdx = it.getColumnIndex("short_description")
                val posterIdx = it.getColumnIndex("poster_art_uri")
                val intentUriIdx = it.getColumnIndex("intent_uri")
                val pkgIdx = it.getColumnIndex("package_name")

                while (it.moveToNext()) {
                    val poster = if (posterIdx >= 0) it.getString(posterIdx) ?: "" else ""
                    list.add(
                        MediaItem(
                            id = it.position,
                            imdbId = null,
                            title = if (titleIdx >= 0) it.getString(titleIdx) ?: "" else "",
                            overview = if (descIdx >= 0) it.getString(descIdx) ?: "" else "",
                            posterUrl = poster,
                            backdropUrl = poster,
                            deepLinkUri = if (intentUriIdx >= 0) it.getString(intentUriIdx) else null,
                            sourcePackage = if (pkgIdx >= 0) it.getString(pkgIdx) else null
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Consulta em tempo real a API do TMDb para descobrir onde o filme/série está disponível no Brasil
     * e busca o trailer oficial no YouTube.
     */
    fun fetchAvailableStreamsAndTrailer(item: MediaItem, callback: (List<StreamOption>) -> Unit) {
        val apiKey = prefs.getString("tmdb_api_key", "")?.trim() ?: ""
        thread {
            val options = mutableListOf<StreamOption>()
            val typeStr = if (item.mediaType == "tv") "tv" else "movie"

            // 1. Sempre adiciona a opção padrão do Stremio para reprodução direta
            try {
                val stremioType = if (item.mediaType == "tv") "series" else "movie"
                val stremioUri = Uri.parse("stremio:///detail/$stremioType/tmdb:${item.id}")
                val stremioIntent = Intent(Intent.ACTION_VIEW, stremioUri).apply {
                    setPackage("com.stremio.one")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                options.add(StreamOption("Assistir no Stremio", "Stremio (Reprodutor Principal)", stremioIntent))
            } catch (_: Exception) {}

            if (apiKey.isNotEmpty() && item.id > 0) {
                // 2. Consulta provedores de streaming oficiais no Brasil (Watch/Providers) via TMDb API
                try {
                    val provUrl = URL("https://api.themoviedb.org/3/$typeStr/${item.id}/watch/providers?api_key=$apiKey")
                    val conn = provUrl.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    val res = conn.inputStream.bufferedReader().readText()
                    val brObj = JSONObject(res).optJSONObject("results")?.optJSONObject("BR")

                    fun parseProviders(arrayName: String, badgePrefix: String) {
                        val arr = brObj?.optJSONArray(arrayName)
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val p = arr.getJSONObject(i)
                                val name = p.optString("provider_name", "Streaming")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=assistir+${Uri.encode(item.title)}+no+$name")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                options.add(StreamOption("$badgePrefix: $name", name, intent))
                            }
                        }
                    }
                    parseProviders("flatrate", "Streaming")
                    parseProviders("rent", "Alugar")
                    parseProviders("buy", "Comprar")
                } catch (_: Exception) {}

                // 3. Busca o trailer oficial no YouTube via TMDb Videos API
                try {
                    val vidUrl = URL("https://api.themoviedb.org/3/$typeStr/${item.id}/videos?api_key=$apiKey&language=pt-BR")
                    val vConn = vidUrl.openConnection() as HttpURLConnection
                    vConn.connectTimeout = 5000
                    val vRes = vConn.inputStream.bufferedReader().readText()
                    val vResults = JSONObject(vRes).optJSONArray("results")
                    var youtubeKey = ""
                    if (vResults != null) {
                        for (i in 0 until vResults.length()) {
                            val vObj = vResults.getJSONObject(i)
                            if (vObj.optString("site") == "YouTube") {
                                youtubeKey = vObj.optString("key")
                                if (vObj.optString("type") == "Trailer") break
                            }
                        }
                    }
                    if (youtubeKey.isNotEmpty()) {
                        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        options.add(StreamOption("▶ Ver Trailer Oficial", "YouTube (Grátis)", ytIntent))
                    }
                } catch (_: Exception) {}
            }

            // Fallback de trailer caso a API não retorne chave
            if (options.none { it.title.contains("Trailer") }) {
                val ytFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(item.title + " trailer oficial")}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                options.add(StreamOption("▶ Pesquisar Trailer", "YouTube", ytFallback))
            }

            callback(options)
        }
    }
}
