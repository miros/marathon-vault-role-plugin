package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class VaultClient private constructor(
    private val options: Options,
    private val vault: Vault,
    private val token: String,
    private val validUntil: Instant
) {

    data class Options(
        val url: String,
        val timeout: Int,
        val roleID: String,
        val secretID: String
    )

    companion object {

        const val VALIDITY_THRESHOLD_SEC = 60L

        val NOW = { Instant.now() }

        fun login(options: Options, clock: () -> Instant = NOW): VaultClient {
            val authResp = createVault(options).auth()
                .loginByAppRole(options.roleID, options.secretID)

            val leaseDuration = if (authResp.authLeaseDuration > 0) {
                authResp.authLeaseDuration
            } else {
                Long.MAX_VALUE
            }

            return VaultClient(
                options,
                loginWithToken(options, authResp.authClientToken),
                authResp.authClientToken,
                clock().plusSeconds(leaseDuration)
            )
        }

        private fun loginWithToken(options: Options, token: String): Vault {
            return createVault(options) { it.token(token) }
        }

        private fun createVault(options: Options, block: (VaultConfig) -> Unit = {}): Vault {
            val config = VaultConfig()
                .address(options.url)
                .openTimeout(options.timeout)
                .readTimeout(options.timeout)
                .engineVersion(1)


            block(config)

            return Vault(config.build())
        }

    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(options.timeout.toLong(), TimeUnit.SECONDS)
        .build()


    private val apiURL = URL(URL(options.url), "v1")

    @Serializable
    data class VaultReply<T>(val data: T)

    @Serializable
    data class ListData(val keys: List<String> = emptyList())

    fun refresh(clock: () -> Instant = NOW): VaultClient {
        if (isFresh(clock)) {
            return this
        }

        return login(options)
    }

    fun isFresh(clock: () -> Instant = NOW): Boolean {
        return validUntil.minusSeconds(VALIDITY_THRESHOLD_SEC).isAfter(clock())
    }

    fun readSecrets(path: String): Map<String, String> {
        return vault.logical().read(preparePath(path)).data
    }

    fun listChildren(path: String): List<String> {
        return try {
            callVault<ListData>(preparePath(path), "LIST", ListData.serializer()).keys
        } catch (exc: VaultException) {
            if (exc.httpStatusCode == 404) {
                emptyList()
            } else {
                throw(exc)
            }
        }
    }

    fun roleExists(roleName: String): Boolean {
        return try {
            vault.logical().read("auth/approle/role/$roleName")
            true
        } catch (exc: VaultException) {
            if (exc.httpStatusCode != 404) {
                throw(exc)
            }
            false
        }
    }

    private fun preparePath(path: String) = path.trim('/')

    fun <T> loginAs(roleName: String, block: (VaultClient) -> T): T {
        val roleID = getAppRoleID(roleName)
        val secretID = generateSecretID(roleName)
        val newClient = login(options.copy(roleID = roleID, secretID = secretID))

        return block(newClient).also { newClient.logout(roleName, secretID) }
    }

    private fun getAppRoleID(roleName: String): String {
        return vault.logical().read("auth/approle/role/$roleName/role-id")
            .data.getValue("role_id")
    }

    private fun generateSecretID(roleName: String): String {
        return vault.logical().write("auth/approle/role/$roleName/secret-id", emptyMap())
            .data.getValue("secret_id")
    }

    private fun logout(roleName: String, secretID: String) {
        vault.logical().write(
            "auth/approle/role/$roleName/secret-id/destroy",
            mapOf("secret_id" to secretID)
        )
    }

    private fun <T> callVault(path: String, method: String, serializer: KSerializer<T>): T {
        val request = Request.Builder()
            .url(appendURL(apiURL, path))
            .header("X-Vault-Token", token)
            .method(method, null)
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw VaultException(
                "Vault responded with HTTP status code: ${response.code()} body:${response.body()?.string()}",
                response.code()
            )
        }

        return Json.nonstrict.parse(VaultReply.serializer(serializer), response.body()!!.string()).data
    }

    private fun appendURL(url: URL, vararg parts: String): URL {
        return url.toURI().resolve(url.path + "/" + parts.joinToString("/")).toURL()
    }

}