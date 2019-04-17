# Marathon Vault Plugin

Plugin for [Marathon](https://mesosphere.github.io/marathon/) which injects secrets stored in [Vault](https://www.vaultproject.io/) via environment variables based on approle vault authorization.

## App roles

Plugin uses vault approles both for itself (it knows how to renew its SecretId) and for fetching secrets for marathon applications.

Role names for marathon applications are formed as following: `{role_prefix_from_config}-{marathon_app_id}`.

For instance, when `role_prefix` is `mesos` and marathon app id is `/apps/my-doge-app`, Vault approle for application will be `mesos-apps-my-doge-app`.
In case this role does not exist plugin will try to use `mesos-apps` as vault approle (one level up marathon app id path).

*You need to create roles in vault for you applications by yourself.*

## Default secrets path

By default plugin injects all secrets defined in vault path: `/{default_secrets_path_from_config}/{marathon-app-id}/*` (but only one level deep, immediate children)

For instance, when `default_secrets_path=mesos` (in plugin config) and `marathon_app_id=/apps/my-doge-app`
then secrets defined in vault path `/mesos/apps/my-doge-app/passwords` will be available as ENV variables `$PASSWORDS_SOME_KEY_NAME`, `$PASSWORDS_OTHER_KEY_NAME`, etc...

*Ensure that approle for marathon application (you should create role yourself) has permissions to read these paths.*

## Marathon secrets api

Plugin supports [Marathon Secrets Api](https://mesosphere.github.io/marathon/docs/secrets.html)

The following example `marathon.json` fragment will read Vault path `/secret/doge/wow`, extract field `password` from that path and inject the field value into an environment variable named `ENV_NAME`:

```json
{
  "env": {
    "ENV_NAME": {
      "secret": "secret_ref"
    }
  },
  "secrets": {
    "secret_ref": {
      "source": "/secret/doge/wow@password"
    }
  }
}
```

## Installation

Please consult the [Start Marathon with plugins](https://mesosphere.github.io/marathon/docs/plugin.html#start-marathon-with-plugins) section of the official docs for a general overview of how plugins are enabled.

The plugin configuration JSON file will need to reference the Vault plugin as follows:

```json
{
  "plugins": {
    "marathon-vault-plugin": {
      "plugin": "mesosphere.marathon.plugin.task.RunSpecTaskProcessor",
      "implementation": "io.funbox.marathon.plugin.vault.VaultEnvPlugin",
      "configuration": {
        "vault_url": "http://your-vault-url:8200",
        "vault_timeout": 1, // timeout in seconds for all vault api calls
        "plugin_role_id": "plugin-role-id",
        "plugin_secret_id": "plugin-secret-id",
        "role_prefix": "mesos", // prefix for application roles
        "default_secrets_path": "/secret/mesos",
        "ssl": { // Optional
            "verify": "false", // don't use in production
            "trust_store_file": "/path/to/truststore/file",
            "key_store_file": "/path/to/keystore/file",
            "key_store_password": "keystore_password",
            "pem_file": "/path/to/pem/file",
            "pem_utf8": "string contents extracted from the PEM file",
            "client_pem_file": "/path/to/client/pem/file",
            "client_key_pem_file": "/path/to/client/pem/file"
        }
      }
    }
  }
}
```

The `ssl` section is optional and it directly configures [the underlying Vault client](https://github.com/BetterCloud/vault-java-driver#ssl-config) but only the options documented here are passed through.

You will also need to start Marathon with the secrets feature being enabled. See [Marathon command line flags](https://mesosphere.github.io/marathon/docs/command-line-flags) for more details. In short, it can be enabled by
* specifying `--enable_features secrets` in Marathon command line
* specifying environment variable `MARATHON_ENABLE_FEATURES=secrets` when starting Marathon

## Vault plugin role policy

In order to work plugin role needs permissions to read AppIds and issue SecretIds for roles with your `role_prefix` path (plugin config)

```hcl
path "auth/approle/role/role_prefix-*" {
	capabilities = ["read", "update"]
}
```
