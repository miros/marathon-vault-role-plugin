package io.funbox.marathon.plugin.vault

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonObject
import okhttp3.*
import java.net.URL
import java.util.concurrent.TimeUnit

class VaultApi(url: String, private val token: String, timeout: Long = 5) {

    open class Error(message: String) : RuntimeException(message)

    class HttpError(method: String, url: URL, response: Response) :
        Error("Vault responded to $method $url with HTTP status code:${response.code()} body:${response.body()?.string()}") {
        val httpCode = response.code()
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()

    private val apiURL = URL(URL(url), "v1")

    @Serializable
    private data class VaultReply<T>(@SerialName("data") val payload: T)

    @Serializable
    data class AuthReply(@SerialName("auth") val payload: Token)

    @Serializable
    data class Token(
        @SerialName("lease_duration") val leaseDuration: Long,
        @SerialName("client_token") val tokenValue: String
    )

    companion object {
        fun loginByRole(url: String, roleID: String, secretID: String, timeout: Long): Token {
            val vault = VaultApi(url = url, timeout = timeout, token = "")

            val reply = vault.callVault(
                "POST", "auth/approle/login",
                AuthReply.serializer(),
                JsonObject(
                    mapOf(
                        "role_id" to JsonLiteral(roleID),
                        "secret_id" to JsonLiteral(secretID)
                    )
                )
            )

            return reply!!.payload
        }
    }

    fun roleExists(roleName: String): Boolean {
        return try {
            callVault<Nothing>("GET", "auth/approle/role/$roleName")
            true
        } catch (exc: HttpError) {
            if (exc.httpCode != 404) {
                throw(exc)
            }
            false
        }
    }


    fun readSecrets(path: String): Map<String, String> {
        return callVault(
            "GET", path,
            VaultReply.serializer((StringSerializer to StringSerializer).map)
        )!!.payload
    }

    @Serializable
    private data class ListData(val keys: List<String>)

    fun listChildren(path: String): List<String> {
        return try {
            callVault(
                "LIST", path,
                VaultReply.serializer(ListData.serializer())
            )!!.payload.keys
        } catch (exc: HttpError) {
            if (exc.httpCode == 404) {
                emptyList()
            } else {
                throw(exc)
            }
        }
    }

    @Serializable
    private data class RoleIDData(val role_id: String)

    fun getAppRoleID(roleName: String): String {
        return callVault(
            "GET", "auth/approle/role/$roleName/role-id",
            VaultReply.serializer(RoleIDData.serializer())
        )!!.payload.role_id
    }

    @Serializable
    private data class SecretIDData(val secret_id: String)

    fun generateSecretID(roleName: String): String {
        return callVault(
            "POST", "auth/approle/role/$roleName/secret-id",
            VaultReply.serializer(SecretIDData.serializer()),
            emptyParams
        )!!.payload.secret_id
    }

    fun mountSecretsEngine(path: String, type: String, version: Int) {
        callVault<Nothing>(
            "POST", "/sys/mounts/" + preparePath(path),
            requestParams = JsonObject(
                mapOf(
                    "type" to JsonLiteral(type),
                    "version" to JsonLiteral(version)
                )
            )
        )
    }

    fun destroySecretID(roleName: String, secretID: String) {
        callVault<Nothing>(
            "POST", "auth/approle/role/$roleName/secret-id/destroy",
            requestParams = JsonObject(mapOf("secret_id" to JsonLiteral(secretID)))
        )
    }

    private val emptyParams = JsonObject(emptyMap())

    fun <T> callVault(
        method: String,
        path: String,
        replySerializer: KSerializer<T>? = null,
        requestParams: JsonObject? = null
    ): T? {
        val url = appendURL(apiURL, preparePath(path))

        val request = Request.Builder()
            .url(url)
            .header("X-Vault-Token", token)
            .method(method, requestParams?.let { serializeRequest(requestParams) })
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw HttpError(method, url, response)
        }

        val body = response.body()?.string()

        if (body.isNullOrEmpty() || replySerializer == null) {
            return null
        }

        return Json.nonstrict.parse(replySerializer, body)
    }

    private fun serializeRequest(params: JsonObject): RequestBody {
        val json = Json.plain.stringify(params)
        return RequestBody.create(MediaType.get("application/json"), json)
    }

    private fun preparePath(path: String) = path.trim('/')

    private fun appendURL(url: URL, vararg parts: String): URL {
        return url.toURI().resolve(url.path + "/" + parts.joinToString("/")).toURL()
    }

}