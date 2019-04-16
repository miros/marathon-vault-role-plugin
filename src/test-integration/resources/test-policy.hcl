path "auth/approle/role/mesos-*" {
	capabilities = ["read", "update"]
}

path "secret/mesos*" {
  capabilities = ["read", "list"]
}