package com.skodamusic.app.emby

import android.os.SystemClock
import com.skodamusic.app.model.AuthByNameResult
import com.skodamusic.app.model.HttpResult
import okhttp3.Dns
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URL
import java.net.UnknownHostException
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

class EmbyApi(
    private val log: (String) -> Unit
) {
    private val dnsCacheLock = Any()
    private val cfPreferredIpv4Cache = LinkedHashMap<String, Pair<Long, List<InetAddress>>>()
    private val jsonMediaType: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("json media type parse failed")

    fun authenticateByName(
        embyBase: String,
        username: String,
        password: String,
        log: (String) -> Unit,
        httpClient: OkHttpClient
    ): AuthByNameResult? {
        return try {
            val endpoint = buildAuthenticateByNameUrl(embyBase)
            log("POST $endpoint")
            val body = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString()
            val requestBody = RequestBody.create(jsonMediaType, body)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val callClient = httpClient.newBuilder()
                .connectTimeout(6000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                .build()
            callClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
                log("POST $endpoint -> HTTP $code")
                log("auth body=${previewPayload(payload)}")
                if (code !in 200..299) {
                    return null
                }
                val root = JSONObject(payload)
                val token = root.optString("AccessToken").trim()
                val userId = root.optJSONObject("User")?.optString("Id")?.trim().orEmpty()
                if (token.isEmpty() || userId.isEmpty()) {
                    log("auth response missing token/user")
                    return null
                }
                return AuthByNameResult(accessToken = token, userId = userId)
            }
        } catch (e: Exception) {
            log("auth exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            null
        }
    }

    fun executeGet(
        endpoint: String,
        token: String,
        requestLabel: String,
        log: (String) -> Unit,
        httpClient: OkHttpClient
    ): HttpResult {
        return try {
            log("$requestLabel url=$endpoint")
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .header("X-Emby-Token", token)
                .build()
            val callClient = httpClient.newBuilder()
                .connectTimeout(6000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                .build()
            callClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
                log("$requestLabel -> HTTP $code, body=${previewPayload(payload)}")
                HttpResult(code = code, payload = payload)
            }
        } catch (e: Exception) {
            log("$requestLabel exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            HttpResult(code = -1, payload = "")
        }
    }

    fun buildHttpClient(embyBase: String, cfReferenceRaw: String): OkHttpClient {
        val embyHost = try {
            URL(embyBase).host.trim()
        } catch (_: Exception) {
            ""
        }
        if (embyHost.isEmpty()) {
            return OkHttpClient()
        }
        val refHost = parseCfReferenceHost(cfReferenceRaw)
        if (refHost.isNullOrEmpty()) {
            log("cf-opt disabled reason=empty-reference-domain host=$embyHost")
            return OkHttpClient()
        }
        log("cf-opt enabled embyHost=$embyHost referenceHost=$refHost ipv6=disabled")
        return OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return resolveDnsForHost(hostname, embyHost, refHost)
                }
            })
            .connectTimeout(10000L, TimeUnit.MILLISECONDS)
            .readTimeout(20000L, TimeUnit.MILLISECONDS)
            .build()
    }

    fun normalizeEmbyBase(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/emby", ignoreCase = true)) {
            normalized
        } else {
            "$normalized/emby"
        }
    }

    fun buildAuthenticateByNameUrl(embyBase: String): String {
        return "$embyBase/Users/AuthenticateByName?${buildCommonEmbyQuery()}"
    }

    fun buildRecommendedItemsUrl(
        embyBase: String,
        userId: String,
        token: String,
        limit: Int
    ): String {
        val params = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=Random",
            "Limit=${limit.coerceAtLeast(1)}",
            "api_key=${urlEncode(token)}"
        )
        return "$embyBase/Users/${urlEncode(userId)}/Items?${params.joinToString("&")}" 
    }

    fun buildLibraryItemsUrl(
        embyBase: String,
        userId: String,
        token: String,
        startIndex: Int,
        limit: Int
    ): String {
        val params = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=SortName",
            "SortOrder=Ascending",
            "StartIndex=${startIndex.coerceAtLeast(0)}",
            "Limit=${limit.coerceAtLeast(1)}",
            "EnableTotalRecordCount=true",
            "api_key=${urlEncode(token)}"
        )
        return "$embyBase/Users/${urlEncode(userId)}/Items?${params.joinToString("&")}" 
    }

    fun buildDownloadUrl(
        embyBase: String,
        trackId: String,
        token: String
    ): String {
        return "$embyBase/Items/${urlEncode(trackId)}/Download?api_key=${urlEncode(token)}"
    }

    fun previewPayload(payload: String): String {
        if (payload.isBlank()) {
            return "<empty>"
        }
        val singleLine = payload.replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= 160) {
            singleLine
        } else {
            singleLine.substring(0, 160) + "..."
        }
    }

    private fun parseCfReferenceHost(raw: String): String? {
        val trimmed = raw.trim().trim('/')
        if (trimmed.isEmpty()) {
            return null
        }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return try {
            URL(withScheme).host.trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDnsForHost(
        hostname: String,
        embyHost: String,
        referenceHost: String
    ): List<InetAddress> {
        val systemResolved = safeLookupIpv4(hostname)
        if (!hostname.equals(embyHost, ignoreCase = true)) {
            if (systemResolved.isNotEmpty()) {
                log("cf-opt bypass host=$hostname reason=non-emby ipv4Count=${systemResolved.size}")
                return systemResolved
            }
            log("cf-opt bypass-fail host=$hostname reason=non-emby-no-ipv4")
            throw UnknownHostException("No IPv4 address for host: $hostname")
        }

        val preferredFromReference = resolvePreferredCfIpv4Candidates(referenceHost)
        if (preferredFromReference.isEmpty()) {
            log("cf-opt fallback reason=reference-resolve-empty host=$hostname")
            if (systemResolved.isNotEmpty()) {
                return systemResolved
            }
            throw UnknownHostException("No IPv4 address for emby host: $hostname")
        }

        val merged = ArrayList<InetAddress>(preferredFromReference.size + systemResolved.size)
        val dedupe = HashSet<String>()
        for (addr in preferredFromReference) {
            if (dedupe.add(addr.hostAddress ?: "")) {
                merged.add(addr)
            }
        }
        for (addr in systemResolved) {
            if (dedupe.add(addr.hostAddress ?: "")) {
                merged.add(addr)
            }
        }
        val preview = merged.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
        val selected = merged.firstOrNull()?.hostAddress ?: "?"
        log(
            "cf-opt dns host=$hostname selected=$selected preferredCount=${preferredFromReference.size} systemCount=${systemResolved.size} merged=${merged.size} sample=$preview"
        )
        if (merged.isNotEmpty()) {
            return merged
        }
        if (systemResolved.isNotEmpty()) {
            log("cf-opt fallback reason=merge-empty-use-system host=$hostname systemCount=${systemResolved.size}")
            return systemResolved
        }
        throw UnknownHostException("No IPv4 address after cf-opt merge for host: $hostname")
    }

    private fun resolvePreferredCfIpv4Candidates(referenceHost: String): List<InetAddress> {
        val now = SystemClock.elapsedRealtime()
        synchronized(dnsCacheLock) {
            val cached = cfPreferredIpv4Cache[referenceHost]
            if (cached != null && now - cached.first < CF_IP_CACHE_TTL_MS) {
                val preview = cached.second.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
                log(
                    "cf-opt cache-hit referenceHost=$referenceHost ageMs=${now - cached.first} ipv4Count=${cached.second.size} sample=$preview"
                )
                return cached.second
            }
        }
        val resolved = safeLookupIpv4(referenceHost)
        val deduped = LinkedHashMap<String, InetAddress>()
        for (addr in resolved) {
            val key = addr.hostAddress ?: continue
            if (!deduped.containsKey(key)) {
                deduped[key] = addr
            }
            if (deduped.size >= MAX_CF_IP_CANDIDATES) {
                break
            }
        }
        val result = deduped.values.toList()
        val resolvedPreview = result.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
        log("cf-opt cache-refresh referenceHost=$referenceHost ipv4Count=${result.size} sample=$resolvedPreview")
        synchronized(dnsCacheLock) {
            cfPreferredIpv4Cache[referenceHost] = now to result
            if (cfPreferredIpv4Cache.size > CF_IP_CACHE_MAX_HOSTS) {
                val iterator = cfPreferredIpv4Cache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun safeLookupIpv4(host: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(host)
                .filterIsInstance<Inet4Address>()
        } catch (_: SocketTimeoutException) {
            log("cf-opt system-dns-timeout host=$host")
            emptyList()
        } catch (e: Exception) {
            log("cf-opt system-dns-fail host=$host type=${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun buildCommonEmbyQuery(): String = commonEmbyQueryParams().joinToString("&")

    private fun commonEmbyQueryParams(): List<String> {
        return listOf(
            "X-Emby-Client=${urlEncode(EMBY_QUERY_CLIENT)}",
            "X-Emby-Device-Name=${urlEncode(EMBY_QUERY_DEVICE_NAME)}",
            "X-Emby-Device-Id=${urlEncode(EMBY_QUERY_DEVICE_ID)}",
            "X-Emby-Client-Version=${urlEncode(EMBY_QUERY_CLIENT_VERSION)}",
            "X-Emby-Language=${urlEncode(EMBY_QUERY_LANGUAGE)}"
        )
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val EMBY_QUERY_CLIENT = "Emby Web"
        private const val EMBY_QUERY_DEVICE_NAME = "Google Chrome Windows"
        private const val EMBY_QUERY_DEVICE_ID = "6ec2a066-66a2-49af-bd97-6302ee307eaf"
        private const val EMBY_QUERY_CLIENT_VERSION = "4.9.1.90"
        private const val EMBY_QUERY_LANGUAGE = "zh-cn"
        private const val CF_IP_CACHE_TTL_MS = 60_000L
        private const val CF_IP_CACHE_MAX_HOSTS = 8
        private const val MAX_CF_IP_CANDIDATES = 6
        private const val MAX_CF_IP_PREVIEW = 3
    }
}
