package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.api.Logical
import com.bettercloud.vault.json.JsonArray
import com.google.gson.Gson
import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.MarathonClient
import mesosphere.marathon.client.model.v2.App
import okhttp3.OkHttpClient
import okhttp3.Request
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.net.SocketException
import java.time.Duration
import kotlin.test.Test


@Testcontainers
class VaultEnvPluginTest {

    companion object {
        const val VAULT_PORT = 8200
        const val MARATHON_PORT = 8080
        const val MESOS_MASTER_PORT = 5050
        const val MESOS_SLAVE_PORT = 5051
        const val TEST_APP_PORT = 8000

        const val PLUGIN_ROLE = "test-role"
        const val PLUGIN_ROLE_ID = "test-role-id"
        const val PLUGIN_SECRET_ID = "test-secret-id"

        const val TEST_APP_ROLE = "test-app"
    }

    // java recursive generics fail to compile in kotlin without this type hinting
    class KDockerComposeContainer(file: File) : DockerComposeContainer<KDockerComposeContainer>(file)

    private val dockerComposeFile = File("src/test-integration/resources/docker-compose.yml")

    @Container
    private val docker = KDockerComposeContainer(dockerComposeFile)
        .withEnv(
            mapOf(
                "MESOS_VERSION" to System.getProperty("MESOS_VERSION"),
                "MARATHON_VERSION" to System.getProperty("MARATHON_VERSION")
            )
        )
        .withLocalCompose(true)
        .withTailChildContainers(true)
        .withExposedService(
            "vault", VAULT_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "marathon", MARATHON_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "mesos-master", MESOS_MASTER_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )
        .withExposedService(
            "mesos-slave", MESOS_SLAVE_PORT,
            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5))
        )

    @Test
    fun `sets env variables for marathon application`() {
        val testAppURL = getServiceURL("mesos-slave", TEST_APP_PORT)
        println("test app: $testAppURL")

        createTestRoles()

        val marathon = marathonClient()

        // TODO add PORT as param to server.py
        marathon.createApp(App().apply {
            id = TEST_APP_ROLE
            cmd = "python3 /server.py"
        })

        val response = await().ignoreExceptions().untilCallTo { getURL(testAppURL) }.matches { it!!.isNotEmpty() }
        val envs = parseTestAppResponse(response!!)

        println(envs)
    }

    private fun getURL(url: String): String? {
        return try {
            val request = Request.Builder().get().url(url).build()
            OkHttpClient().newCall(request).execute().body()?.string()
        } catch (exc: SocketException) {
            null
        }
    }

    private fun parseTestAppResponse(response: String): Map<String, String> {
        return Gson().fromJson(response, mutableMapOf<String, String>().javaClass)
    }

    private fun createTestRoles() {
        val vault = vaultClient()

        vault.write("sys/auth/approle", mapOf("type" to "approle"))

        createPluginRole(vault)
        createTestAppRole(vault)
    }

    private fun createPluginRole(vault: Logical) {
        vault.write(
            "auth/approle/role/$PLUGIN_ROLE",
            mapOf("policies" to "root")
        )
        vault.write(
            "auth/approle/role/$PLUGIN_ROLE/role-id",
            mapOf("role_id" to PLUGIN_ROLE_ID)
        )
        vault.write("auth/approle/role/$PLUGIN_ROLE/custom-secret-id", mapOf("secret_id" to PLUGIN_SECRET_ID))
    }

    private fun createTestAppRole(vault: Logical) {
        vault.write("auth/approle/role/$TEST_APP_ROLE", emptyMap())
    }

    private fun marathonClient(): Marathon {
        return MarathonClient.getInstance(getServiceURL("marathon", MARATHON_PORT))
    }

    private fun vaultClient(): Logical {
        val config = VaultConfig()
            .address(getServiceURL("vault", VAULT_PORT))
            .token("test-root-token")
            .engineVersion(1)

        return Vault(config.build()).logical()
    }

    private fun getServiceURL(name: String, port: Int): String {
        val dockerClient = DockerClientFactory.instance().client()
        val container =
            dockerClient.listContainersCmd().withLabelFilter(mapOf("com.docker.compose.service" to name)).exec().first()

        val publicPort = container.ports.find { it.privatePort == port }!!.publicPort

        return "http://localhost:$publicPort"
    }


}
