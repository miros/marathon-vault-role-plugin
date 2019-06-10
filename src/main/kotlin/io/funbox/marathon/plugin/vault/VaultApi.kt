package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.VaultException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.stringify
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

class VaultApi(url: String, private val token: String, timeout: Long = 5) {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()

    private val apiURL = URL(URL(url), "v1")

    @Serializable
    data class VaultReply<T>(val data: T)

    @Serializable
    data class ListData(val keys: List<String>)

    @Serializable
    class EmptyReply

    fun listChildren(path: String): List<String> {
        return try {
            callVault(path, "LIST", ListData.serializer())!!.keys
        } catch (exc: VaultException) {
            if (exc.httpStatusCode == 404) {
                emptyList()
            } else {
                throw(exc)
            }
        }
    }

    fun mountSecretsEngine(path: String, params: JsonObject) {
        callVault("/sys/mounts/" + preparePath(path), "POST", EmptyReply.serializer(), params)
    }

    private fun <T> callVault(path: String, method: String, replySerializer: KSerializer<T>, requestParams: JsonObject? = null): T? {
        val url = appendURL(apiURL, preparePath(path))

        val request = Request.Builder()
            .url(url)
            .header("X-Vault-Token", token)
            .method(method, requestParams?.let { serializeRequest(requestParams) })
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw VaultException(
                "Vault responded with HTTP status code: ${response.code()} body:${response.body()?.string()}",
                response.code()
            )
        }

        val body = response.body()?.string()

        if (body.isNullOrBlank()) {
            return null
        }

        return Json.nonstrict.parse(VaultReply.serializer(replySerializer), body).data
    }

    private fun serializeRequest(params: JsonObject) : RequestBody {
        val json = Json.plain.stringify(params)
        return RequestBody.create(MediaType.get("application/json"), json)
    }

    private fun preparePath(path: String) = path.trim('/')

    private fun appendURL(url: URL, vararg parts: String): URL {
        return url.toURI().resolve(url.path + "/" + parts.joinToString("/")).toURL()
    }

}