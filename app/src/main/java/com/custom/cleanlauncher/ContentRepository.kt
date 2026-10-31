package com.custom.cleanlauncher

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

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

class ContentRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    fun recordWatchedMediaForActiveProfile(profileId: String, tmdbMovieId: Int) {
        prefs.edit().putInt("last_watched_tmdb_$profileId", tmdbMovieId).apply()
    }

    fun fetchPersonalizedRecommendations(profile: UserProfile, callback: (List<MediaItem>) -> Unit) {
        val apiKey = prefs.getString("tmdb_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            callback(emptyList())
            return
        }
        val lastWatchedId = prefs.getInt("last_watched_tmdb_${profile.id}", -1)
        val usePersonalized = prefs.getBoolean("personalized_recs", true)

        val endpoint = if (usePersonalized && lastWatchedId != -1) {
            "https://api.themoviedb.org/3/movie/$lastWatchedId/recommendations?api_key=$apiKey&language=pt-BR"
        } else {
            "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=pt-BR&sort_by=popularity.desc&with_genres=${profile.favoriteGenres}"
        }
        fetchTmdbList(endpoint, callback)
    }

    fun fetchTrending(callback: (List<MediaItem>) -> Unit) {
        val apiKey = prefs.getString("tmdb_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            callback(emptyList())
            return
        }
        val endpoint = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=pt-BR"
        fetchTmdbList(endpoint, callback)
    }

    private fun fetchTmdbList(urlStr: String, callback: (List<MediaItem>) -> Unit) {
        thread {
            val items = mutableListOf<MediaItem>()
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                val response = conn.inputStream.bufferedReader().readText()
                val results = JSONObject(response).getJSONArray("results")

                for (i in 0 until minOf(results.length(), 15)) {
                    val obj = results.getJSONObject(i)
                    val title = obj.optString("title").ifEmpty { obj.optString("name") }
                    val poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path")
                    val backdrop = "https://image.tmdb.org/t/p/w1280" + obj.optString("backdrop_path")
                    val mediaType = obj.optString("media_type", "movie")

                    items.add(
                        MediaItem(
                            id = obj.getInt("id"),
                            imdbId = null,
                            title = title,
                            overview = obj.optString("overview"),
                            posterUrl = poster,
                            backdropUrl = backdrop,
                            mediaType = mediaType
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                    list.add(
                        MediaItem(
                            id = it.position,
                            imdbId = null,
                            title = if (titleIdx >= 0) it.getString(titleIdx) ?: "" else "",
                            overview = if (descIdx >= 0) it.getString(descIdx) ?: "" else "",
                            posterUrl = if (posterIdx >= 0) it.getString(posterIdx) ?: "" else "",
                            backdropUrl = if (posterIdx >= 0) it.getString(posterIdx) ?: "" else "",
                            deepLinkUri = if (intentUriIdx >= 0) it.getString(intentUriIdx) else null,
                            sourcePackage = if (pkgIdx >= 0) it.getString(pkgIdx) else null
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun openMediaInStremioOrApp(profileId: String, item: MediaItem) {
        if (item.id > 0) {
            recordWatchedMediaForActiveProfile(profileId, item.id)
        }
        if (!item.deepLinkUri.isNullOrEmpty()) {
            try {
                val intent = Intent.parseUri(item.deepLinkUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        val stremioUri = Uri.parse("stremio:///search?search=${Uri.encode(item.title)}")
        val stremioIntent = Intent(Intent.ACTION_VIEW, stremioUri).apply {
            setPackage("com.stremio.one")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(stremioIntent)
        } catch (_: Exception) {
            val fallback = context.packageManager.getLeanbackLaunchIntentForPackage("com.stremio.one")
            fallback?.let { context.startActivity(it) }
        }
    }
}
