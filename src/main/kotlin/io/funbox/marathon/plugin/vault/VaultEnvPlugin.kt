package io.funbox.marathon.plugin.vault

import com.bettercloud.vault.VaultException
import mesosphere.marathon.plugin.ApplicationSpec
import mesosphere.marathon.plugin.PodSpec
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.task.RunSpecTaskProcessor
import org.apache.mesos.Protos
import org.apache.mesos.Protos.Environment.Variable
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject

class VaultEnvPlugin : RunSpecTaskProcessor, PluginConfiguration {

    val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var conf: PluginConf
    private lateinit var rootVault: VaultClient

    override fun initialize(marathonInfo: scala.collection.immutable.Map<String, Any>?, configuration: JsObject?) {
        conf = PluginConf.fromJson(Json(configuration))
        rootVault = VaultClient.login(conf.vaultOptions)
    }

    override fun taskInfo(appSpec: ApplicationSpec, builder: Protos.TaskInfo.Builder) {
        try {

            rootVault = rootVault.refresh()

            val appID = appSpec.id().toString()
            val appRole = roleNameForMarathonID(appID)
            val defaultSecretsPath = defaultSecretsPath(appID)

            val vault = rootVault.loginAs(appRole)
            val envs = envsFrom(vault, defaultSecretsPath)

            logger.info(
                "appID:$appID appRole:$appRole defaultSecretsPath:$defaultSecretsPath envs:[${envs.keys.joinToString(",")}]"
            )

            setEnvs(envs, builder)
        } catch (exc: VaultException) {
            logger.error("error injecting vault secrets for appID:${appSpec.id()}", exc)
        }
    }

    private fun setEnvs(envs: Map<String, String>, builder: Protos.TaskInfo.Builder) {
        val envBuilder = builder.command.environment.toBuilder()

        envs.forEach { (key, value) ->
            envBuilder.addVariables(
                Variable.newBuilder().setName(key).setValue(value)
            )
        }

        val commandBuilder = builder.command.toBuilder()
        commandBuilder.setEnvironment(envBuilder)

        builder.setCommand(commandBuilder)
    }

    private fun envsFrom(vault: VaultClient, path: String): Map<String, String> {
        return vault
            .listChildren(path)
            .filterNot(::isDirectory)
            .fold(emptyMap()) { secrets, resource ->
                val subSecrets = vault
                    .readSecrets("$path/$resource")
                    .mapKeys { formatEnvName(resource, it.key) }

                secrets + subSecrets
            }
    }

    private fun isDirectory(path: String) = path.endsWith("/")

    private fun defaultSecretsPath(marathonID: String) = conf.defaultSecretsPath + "/" + marathonID

    private fun roleNameForMarathonID(marathonID: String) = marathonID.replace("/", "-")

    private fun formatEnvName(prefix: String, name: String): String {
        return listOf(prefix, name).joinToString("_").toUpperCase()
    }

    override fun taskGroup(
        podSpec: PodSpec?,
        executor: Protos.ExecutorInfo.Builder?,
        taskGroup: Protos.TaskGroupInfo.Builder?
    ) {
        // not implemented
    }

}