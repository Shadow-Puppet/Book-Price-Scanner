package com.example.bookscanprice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BookInfo(
    val isbn: String,
    val title: String = "Unknown title",
    val authors: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pageCount: Int? = null,
    // Free price signals
    val googleListPrice: Double? = null,
    val googleRetailPrice: Double? = null,
    val currency: String = "USD",
    // Optional: filled only if an eBay token is configured
    val ebayActiveMin: Double? = null,
    val ebayActiveMedian: Double? = null,
    val ebayActiveCount: Int? = null,
    val error: String? = null
) {
    /** Rough expected sell price: prefer eBay data, else discount retail. */
    val estimatedSellPrice: Double?
        get() = when {
            ebayActiveMedian != null -> ebayActiveMedian * 0.85 // sold prices run below active asks
            googleRetailPrice != null -> googleRetailPrice * 0.4 // used books ~30-50% of retail
            googleListPrice != null -> googleListPrice * 0.4
            else -> null
        }
}

class BookRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun lookup(isbn: String): BookInfo = withContext(Dispatchers.IO) {
        coroutineScope {
            val googleDeferred = async { fetchGoogleBooks(isbn) }
            val openLibDeferred = async { fetchOpenLibrary(isbn) }
            val ebayDeferred = async { fetchEbayActive(isbn) }

            val google = googleDeferred.await()
            val openLib = openLibDeferred.await()
            val ebay = ebayDeferred.await()

            if (google == null && openLib == null) {
                return@coroutineScope BookInfo(isbn = isbn, error = "Book not found in Google Books or Open Library.")
            }

            BookInfo(
                isbn = isbn,
                title = google?.title ?: openLib?.title ?: "Unknown title",
                authors = google?.authors ?: openLib?.authors ?: "",
                publisher = google?.publisher ?: openLib?.publisher ?: "",
                publishedDate = google?.publishedDate ?: openLib?.publishedDate ?: "",
                pageCount = google?.pageCount ?: openLib?.pageCount,
                googleListPrice = google?.googleListPrice,
                googleRetailPrice = google?.googleRetailPrice,
                currency = google?.currency ?: "USD",
                ebayActiveMin = ebay?.first,
                ebayActiveMedian = ebay?.second,
                ebayActiveCount = ebay?.third
            )
        }
    }

    private fun fetchGoogleBooks(isbn: String): BookInfo? {
        val body = get("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&country=US") ?: return null
        return try {
            val root = JSONObject(body)
            if (root.optInt("totalItems", 0) == 0) return null
            val item = root.getJSONArray("items").getJSONObject(0)
            val vol = item.getJSONObject("volumeInfo")
            val sale = item.optJSONObject("saleInfo")

            var list: Double? = null
            var retail: Double? = null
            var currency = "USD"
            sale?.optJSONObject("listPrice")?.let {
                list = it.optDouble("amount").takeIf { a -> !a.isNaN() }
                currency = it.optString("currencyCode", "USD")
            }
            sale?.optJSONObject("retailPrice")?.let {
                retail = it.optDouble("amount").takeIf { a -> !a.isNaN() }
            }

            val authors = vol.optJSONArray("authors")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.getString(it) }
            } ?: ""

            BookInfo(
                isbn = isbn,
                title = vol.optString("title", "Unknown title"),
                authors = authors,
                publisher = vol.optString("publisher", ""),
                publishedDate = vol.optString("publishedDate", ""),
                pageCount = vol.optInt("pageCount").takeIf { it > 0 },
                googleListPrice = list,
                googleRetailPrice = retail,
                currency = currency
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchOpenLibrary(isbn: String): BookInfo? {
        val body = get("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data")
            ?: return null
        return try {
            val root = JSONObject(body)
            val key = "ISBN:$isbn"
            if (!root.has(key)) return null
            val book = root.getJSONObject(key)
            val authors = book.optJSONArray("authors")?.let { arr ->
                (0 until arr.length()).joinToString(", ") { arr.getJSONObject(it).optString("name") }
            } ?: ""
            val publisher = book.optJSONArray("publishers")?.let { arr ->
                if (arr.length() > 0) arr.getJSONObject(0).optString("name") else ""
            } ?: ""
            BookInfo(
                isbn = isbn,
                title = book.optString("title", "Unknown title"),
                authors = authors,
                publisher = publisher,
                publishedDate = book.optString("publish_date", ""),
                pageCount = book.optInt("number_of_pages").takeIf { it > 0 }
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * OPTIONAL eBay integration. Returns (min, median, count) of ACTIVE listings.
     * Only runs if you set EBAY_APP_TOKEN in app/build.gradle.kts.
     * Get a token: https://developer.ebay.com (Browse API, client-credentials OAuth).
     * Note: active asking prices skew ~15-30% above realized sold prices.
     */
    private fun fetchEbayActive(isbn: String): Triple<Double, Double, Int>? {
        val token = BuildConfig.EBAY_APP_TOKEN
        if (token.isBlank()) return null
        val body = get(
            "https://api.ebay.com/buy/browse/v1/item_summary/search?gtin=$isbn&limit=50",
            mapOf(
                "Authorization" to "Bearer $token",
                "X-EBAY-C-MARKETPLACE-ID" to "EBAY_US"
            )
        ) ?: return null
        return try {
            val items = JSONObject(body).optJSONArray("itemSummaries") ?: return null
            val prices = (0 until items.length()).mapNotNull { i ->
                items.getJSONObject(i).optJSONObject("price")
                    ?.optDouble("value")?.takeIf { !it.isNaN() }
            }.sorted()
            if (prices.isEmpty()) return null
            Triple(prices.first(), prices[prices.size / 2], prices.size)
        } catch (e: Exception) {
            null
        }
    }
}
