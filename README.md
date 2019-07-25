# Marathon Vault Plugin

Plugin for [Marathon](https://mesosphere.github.io/marathon/) that injects secrets stored in [Vault](https://www.vaultproject.io/) via environment variables based on approle vault authorization.

## App roles

Plugin uses vault approles both for itself (it knows how to renew its SecretId) and for fetching secrets for marathon applications.

Role names for marathon applications are formed as following: `{role_prefix_from_config}-{app_id}`.

`app_id` is by default a first segment of marathon app id.
You can change default `app_id` for any task by supplying custom mesos label.
Label name is configured by `app_name_label` option in plugin configuration.

For instance, when `role_prefix` is `mesos` and marathon app id is `/my-doge-app/some-cmd`, Vault approle for application will be `mesos-my-doge-app`.

*You need to create roles in vault for you applications by yourself.*

## Default secrets path

By default plugin injects all secrets defined in vault path: `/{default_secrets_path_from_config}/{app_id}/*` (but only one level deep, immediate children)

`app_id` is by default a first segment of marathon app id.
You can change default `app_id` for any task by supplying custom mesos label.
Label name is configured by `app_name_label` option in plugin configuration.

For instance, when `default_secrets_path=mesos` (in plugin config) and `app_id=my-doge-app`
then secrets defined in vault path `/mesos/my-doge-app/passwords` will be available as ENV variables `$PASSWORDS_SOME_KEY_NAME`, `$PASSWORDS_OTHER_KEY_NAME`, etc...

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

```js
{
  "plugins": {
    "marathon-vault-plugin": {
      "plugin": "mesosphere.marathon.plugin.task.RunSpecTaskProcessor",
      "implementation": "io.funbox.marathon.plugin.vault.VaultEnvPlugin",
      "configuration": {
        "vault_url": "http://your-vault-url:8200",

        // timeout in seconds for all vault api calls
        "vault_timeout": 1,

        "plugin_role_id": "plugin-role-id",
        "plugin_secret_id": "plugin-secret-id",

        // prefix for application roles
        "role_prefix": "mesos",

        "default_secrets_path": "/secret/mesos"
        
        // optional: name of mesos label for custom app id
        // app_name_label: "app"
      }
    }
  }
}
```

You will also need to start Marathon with the secrets feature being enabled. See [Marathon command line flags](https://mesosphere.github.io/marathon/docs/command-line-flags) for more details. In short, it can be enabled by
* specifying `--enable_features secrets` in Marathon command line
* specifying environment variable `MARATHON_ENABLE_FEATURES=secrets` when starting Marathon

## Vault plugin role policy

In order to work plugin role needs permissions to read RoleIds and issue SecretIds for roles with your `ROLE_PREFIX` path (plugin config)

```hcl
path "auth/approle/role/ROLE_PREFIX-*" {
	capabilities = ["read", "update"]
}
```
