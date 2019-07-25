package io.funbox.marathon.plugin.vault

import io.funbox.marathon.plugin.vault.helpers.VaultTestContext
import io.funbox.marathon.plugin.vault.vault_client.VaultClient
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

    class KVaultContainer : VaultContainer<KVaultContainer>("vault:${System.getProperty("VAULT_VERSION")}")

    @Container
    private val vaultContainer = KVaultContainer()
        .withVaultToken(VaultTestContext.ROOT_TOKEN)
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
                roleID = VaultTestContext.PLUGIN_ROLE_ID,
                secretID = VaultTestContext.PLUGIN_SECRET_ID
            ),
            rolePrefix = "mesos",
            defaultSecretsPath = "/secrets_v1/mesos",
            appNameLabel = null
        )
    }

    private val vaultContext by lazy {
        VaultTestContext(vaultURL)
    }


    @Test
    fun `returns env variables for application`() {
        vaultContext.init()

        vaultContext.writeSecret(
            "secrets_v1/mesos/${VaultTestContext.TEST_APP_NAME}/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value",
                "other-secret-key" to "other-secret-value"
            )
        )

        vaultContext.writeSecret(
            "secrets_v1/mesos/${VaultTestContext.TEST_APP_NAME}/other-passwords",
            mapOf("some-secret-key" to "some-secret-value")
        )

        val result = EnvReader(conf).envsFor(VaultTestContext.TEST_APP_NAME, emptyMap(), emptyMap())

        assertThat(result.allSecrets).containsAllEntriesOf(
            mapOf(
                "PASSWORDS_SOME_SECRET_KEY" to "some-secret-value",
                "PASSWORDS_OTHER_SECRET_KEY" to "other-secret-value",
                "OTHER_PASSWORDS_SOME_SECRET_KEY" to "some-secret-value"
            )
        )
    }

    @Test
    fun `supports namespaced marathon tasks`() {
        vaultContext.init()

        vaultContext.writeSecret(
            "secrets_v1/mesos/${VaultTestContext.TEST_APP_NAME}/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value"
            )
        )

        val result = EnvReader(conf).envsFor("/${VaultTestContext.TEST_APP_NAME}/some-task", emptyMap(), emptyMap())

        assertThat(result.allSecrets).containsEntry("PASSWORDS_SOME_SECRET_KEY", "some-secret-value")
        assertThat(result.appRole).isEqualTo("mesos-${VaultTestContext.TEST_APP_NAME}")
    }

    @Test
    fun `uses label for app name when configured`() {
        vaultContext.init(roleName = "mesos-some-namespace-${VaultTestContext.TEST_APP_NAME}")

        vaultContext.writeSecret(
            "secrets_v1/mesos/some-namespace/${VaultTestContext.TEST_APP_NAME}/passwords",
            mapOf(
                "some-secret-key" to "some-secret-value"
            )
        )

        val conf = conf.copy(appNameLabel = "app")

        val result = EnvReader(conf).envsFor("/some-namespace/${VaultTestContext.TEST_APP_NAME}/some-task", emptyMap(),
            mapOf("app" to "/some-namespace/${VaultTestContext.TEST_APP_NAME}"))

        assertThat(result.allSecrets).containsEntry("PASSWORDS_SOME_SECRET_KEY", "some-secret-value")
        assertThat(result.appRole).isEqualTo("mesos-some-namespace-${VaultTestContext.TEST_APP_NAME}")
    }

    @Test
    fun `returns custom secrets`() {
        vaultContext.init()

        vaultContext.writeSecret(
            "secrets_v1/mesos-custom/password",
            mapOf(
                "some-secret-key" to "some-secret-value"
            )
        )

        val result = EnvReader(conf).envsFor(
            "test-app", mapOf(
                "SECRET_KEY" to "secrets_v1/mesos-custom/password@some-secret-key"
            ),
            emptyMap()
        )

        assertThat(result.allSecrets).containsEntry("SECRET_KEY", "some-secret-value")
    }

    @Test
    fun `ignores missing default secrets`() {
        vaultContext.init()
        val result = EnvReader(conf).envsFor("test-app", emptyMap(), emptyMap())
        assertThat(result.allSecrets).isEmpty()
    }

    @Test
    fun `sets missing custom secrets to blank string`() {
        vaultContext.init()

        val result = EnvReader(conf).envsFor(
            "test-app", mapOf(
                "SECRET_KEY" to "secrets_v1/mesos-custom/password@some-secret-key"
            ),
            emptyMap()
        )

        assertThat(result.customSecrets).containsEntry("SECRET_KEY", "")
    }

}
