package io.funbox.marathon.plugin.vault

import io.funbox.marathon.plugin.vault.helpers.VaultUtils
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.vault.VaultContainer
import java.time.Duration
import kotlin.test.Test


@Testcontainers
class EnvReaderTest {

    companion object {
        const val VAULT_PORT = 8200
    }

    class KVaultContainer : VaultContainer<KVaultContainer>("vault:0.9.6")

    @Container
    private val vaultContainer = KVaultContainer()
        .withVaultToken(VaultUtils.ROOT_TOKEN)
        .withVaultPort(VAULT_PORT)
        .waitingFor(
            Wait.forHttp("/v1/sys/active")
                .forStatusCode(400)
                .withStartupTimeout(Duration.ofSeconds(5))
        )

    private val vaultURL by lazy {
        "http://${vaultContainer.containerIpAddress}:${vaultContainer.getMappedPort(VAULT_PORT)}"
    }

    private val conf by lazy {
        PluginConf(
            vaultOptions = VaultClient.Options(
                url = vaultURL,
                timeout = 1,
                roleID = VaultUtils.PLUGIN_ROLE_ID,
                secretID = VaultUtils.PLUGIN_SECRET_ID
            ),
            rolePrefix = "mesos",
            defaultSecretsPath = "mesos"
        )
    }

    @Test
    fun `returns env variables for application`() {
        val vault = VaultUtils.vaultClient(vaultURL)

        VaultUtils.createTestRoles(vault)

        vault.write(
            "secret/mesos/${VaultUtils.TEST_APP_NAME}/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value",
                "other-secret-key" to "other-secret-value"
            )
        )

        vault.write(
            "secret/mesos/${VaultUtils.TEST_APP_NAME}/other-passwords",
            mapOf("some-secret-key" to "some-secret-value")
        )

        val envs = EnvReader(conf).envsFor(VaultUtils.TEST_APP_NAME)

        assertThat(envs).containsAllEntriesOf(
            mapOf(
                "PASSWORDS_SOME_SECRET_KEY" to "some-secret-value",
                "PASSWORDS_OTHER_SECRET_KEY" to "other-secret-value",
                "OTHER_PASSWORDS_SOME_SECRET_KEY" to "some-secret-value"
            )
        )
    }


}
