path "auth/approle/role/mesos-*" {
	capabilities = ["read", "update"]
}

path "secrets_v1/mesos*" {
  capabilities = ["read", "list"]
}