// ! https://github.com/recloudstream/extensions/blob/master/InvidiousProvider/src/main/kotlin/recloudstream/InvidiousProvider.kt

package com.recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URLEncoder

class YouTube : MainAPI() {
    override var mainUrl              = "https://inv.nadeko.net/feed/popular"
    override var name                 = "YouTube"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Others)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val trending = tryParseJson<List<SearchEntry>>(
            app.get("${mainUrl}/api/v1/trending?region=${lang.uppercase()}&type=news&fields=videoId,title").text
        )
        val music = tryParseJson<List<SearchEntry>>(
            app.get("${mainUrl}/api/v1/trending?region=${lang.uppercase()}&type=music&fields=videoId,title").text
        )
        val movies = tryParseJson<List<SearchEntry>>(
            app.get("${mainUrl}/api/v1/trending?region=${lang.uppercase()}&type=movies&fields=videoId,title").text
        )
        val gaming = tryParseJson<List<SearchEntry>>(
            app.get("${mainUrl}/api/v1/trending?region=${lang.uppercase()}&type=gaming&fields=videoId,title").text
        )

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Trend",
                    trending?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                ),
                HomePageList(
                    "Müzik",
                    music?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                ),
                HomePageList(
                    "Film",
                    movies?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                ),
                HomePageList(
                    "Oyun",
                    gaming?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                )
            ),
            false
        )
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val res = tryParseJson<List<SearchEntry>>(
            app.get("${mainUrl}/api/v1/search?q=${query.encodeUri()}&region=${lang.uppercase()}&page=1&type=video&fields=videoId,title").text
        )
        return res?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = Regex("watch\\?v=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)
        val res     = tryParseJson<VideoEntry>(
            app.get("${mainUrl}/api/v1/videos/${videoId}?region=${lang.uppercase()}&fields=videoId,title,description,recommendedVideos,author,authorThumbnails,formatStreams").text
        )
        return res?.toLoadResponse(this)
    }

    private data class SearchEntry(val title: String, val videoId: String) {
        fun toSearchResponse(provider: YouTube): SearchResponse {
            return provider.newMovieSearchResponse(
                title,
                "${provider.mainUrl}/watch?v=${videoId}",
                TvType.Others
            ) {
                this.posterUrl = "${provider.mainUrl}/vi/${videoId}/mqdefault.jpg"
            }
        }
    }

    private data class VideoEntry(
        val title: String,
        val description: String,
        val videoId: String,
        val recommendedVideos: List<SearchEntry>,
        val author: String,
        val authorThumbnails: List<Thumbnail>
    ) {
        suspend fun toLoadResponse(provider: YouTube): LoadResponse {
            return provider.newMovieLoadResponse(
                title,
                "${provider.mainUrl}/watch?v=${videoId}",
                TvType.Others,
                "${videoId}"
            ) {
                plot            = description
                posterUrl       = "${provider.mainUrl}/vi/${videoId}/hqdefault.jpg"
                recommendations = recommendedVideos.map { it.toSearchResponse(provider) }
                actors          = listOf(ActorData(Actor(
                    author,
                    if (authorThumbnails.isNotEmpty()) authorThumbnails.last().url else ""
                )))
            }
        }
    }

    private data class Thumbnail(val url: String)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        loadExtractor("https://youtube.com/watch?v=${data}", subtitleCallback, callback)
        callback(
            ExtractorLink(
                "YouTube",
                "YouTube",
                "${mainUrl}/api/manifest/dash/id/${data}",
                "",
                Qualities.Unknown.value,
                false,
                mapOf(),
                null,
                true
            )
        )
        return true
    }

    companion object {
        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
    }
}
