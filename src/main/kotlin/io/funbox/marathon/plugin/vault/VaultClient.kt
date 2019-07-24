package io.funbox.marathon.plugin.vault

import java.time.Instant

class VaultClient private constructor(
    private val options: Options,
    token: String,
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
            val authResp = VaultApi.loginByRole(
                url = options.url,
                roleID = options.roleID,
                secretID = options.secretID,
                timeout = options.timeout.toLong()
            )

            val leaseDuration = if (authResp.leaseDuration > 0) {
                authResp.leaseDuration
            } else {
                Long.MAX_VALUE
            }

            return VaultClient(
                options,
                authResp.tokenValue,
                clock().plusSeconds(leaseDuration)
            )
        }

    }

    private val vaultAPI = VaultApi(options.url, token, options.timeout.toLong())

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
        return vaultAPI.readSecrets(preparePath(path))
    }

    fun listChildren(path: String): List<String> {
        return vaultAPI.listChildren(path)
    }

    fun roleExists(roleName: String): Boolean {
        return vaultAPI.roleExists(roleName)
    }

    private fun preparePath(path: String) = path.trim('/')

    fun <T> loginAs(roleName: String, block: (VaultClient) -> T): T {
        val roleID = getAppRoleID(roleName)
        val secretID = generateSecretID(roleName)
        val newClient = login(options.copy(roleID = roleID, secretID = secretID))

        return block(newClient).also { logout(roleName, secretID) }
    }

    private fun getAppRoleID(roleName: String): String {
        return vaultAPI.getAppRoleID(roleName)
    }

    private fun generateSecretID(roleName: String): String {
        return vaultAPI.generateSecretID(roleName)
    }

    private fun logout(roleName: String, secretID: String) {
        vaultAPI.destroySecretID(roleName, secretID)
    }


}