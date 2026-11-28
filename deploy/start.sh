#!/bin/sh
set -eu

# Northflank supplies the public DNS names; explicit origins take precedence.
if [ -z "${ALLOWED_ORIGINS:-}" ]; then
  : "${NF_HOSTS:?Set ALLOWED_ORIGINS to the public HTTPS origin, or expose a Northflank HTTP port}"
  ALLOWED_ORIGINS=$(printf '%s' "$NF_HOSTS" | tr ',' '\n' |
    sed 's/^[[:space:]]*//;s/[[:space:]]*$//;/^$/d;s|^|https://|' | paste -sd ',' -)
  : "${ALLOWED_ORIGINS:?No public hostname found; set ALLOWED_ORIGINS}"
  export ALLOWED_ORIGINS
fi

: "${DB_URL:?Set DB_URL to jdbc:mysql://HOST:PORT/DATABASE without query parameters}"
: "${DB_USER:?Set DB_USER from the database connection information}"
: "${DB_PASSWORD:?Set DB_PASSWORD from the database connection information}"
: "${JWT_SECRET:?Set a random JWT_SECRET with at least 32 characters}"

# Accept a bare JDBC address so TLS settings cannot be duplicated or disabled.
case "$DB_URL" in
  *\?*)
    echo 'DB_URL must have no query string. Use jdbc:mysql://HOST:PORT/DATABASE.' >&2
    exit 1
    ;;
  jdbc:mysql://*) ;;
  *)
    echo 'DB_URL must start with jdbc:mysql://, not mysql://.' >&2
    exit 1
    ;;
esac

trust_options=''
if [ -n "${DB_CA_PEM:-}" ]; then
  umask 077
  trust_dir=$(mktemp -d /tmp/earogyata-db.XXXXXX)
  printf '%s\n' "$DB_CA_PEM" > "$trust_dir/ca.pem"
  if ! keytool -importcert -noprompt -alias database-ca \
    -file "$trust_dir/ca.pem" -keystore "$trust_dir/truststore.p12" \
    -storetype PKCS12 -storepass changeit >/dev/null 2>&1; then
    echo 'DB_CA_PEM is not a valid certificate. Paste the full provider CA certificate.' >&2
    rm -rf "$trust_dir"
    exit 1
  fi
  rm "$trust_dir/ca.pem"
  trust_options="&trustCertificateKeyStoreUrl=file:${trust_dir}/truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit"
fi

# Use Java's normal trusted CAs unless the provider requires a custom CA.
# Always verify the server certificate and hostname in either case.
export DB_URL="${DB_URL}?sslMode=VERIFY_IDENTITY${trust_options}&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
unset DB_CA_PEM

exec java -jar /app/app.jar
