#!/bin/sh
set -eu

# Render supplies the actual assigned URL, including any name suffix.
# An explicit origin can still be set for a custom domain.
if [ -z "${ALLOWED_ORIGINS:-}" ]; then
  : "${RENDER_EXTERNAL_URL:?Set ALLOWED_ORIGINS to the public HTTPS origin}"
  export ALLOWED_ORIGINS="${RENDER_EXTERNAL_URL%/}"
fi

: "${DB_URL:?Set DB_URL to jdbc:mysql://HOST:PORT/defaultdb}"
: "${DB_USER:?Set DB_USER from the Aiven connection information}"
: "${DB_PASSWORD:?Set DB_PASSWORD from the Aiven connection information}"
: "${DB_CA_PEM:?Paste the full Aiven CA certificate into DB_CA_PEM}"
: "${JWT_SECRET:?Set a random JWT_SECRET with at least 32 characters}"

# Accept a bare JDBC address so TLS settings cannot be duplicated or disabled.
case "$DB_URL" in
  *\?*)
    echo 'DB_URL must have no query string. Use jdbc:mysql://HOST:PORT/defaultdb.' >&2
    exit 1
    ;;
  jdbc:mysql://*) ;;
  *)
    echo 'DB_URL must start with jdbc:mysql://, not mysql://.' >&2
    exit 1
    ;;
esac

umask 077
trust_dir=$(mktemp -d /tmp/earogyata-db.XXXXXX)
printf '%s\n' "$DB_CA_PEM" > "$trust_dir/ca.pem"
if ! keytool -importcert -noprompt -alias aiven-ca \
  -file "$trust_dir/ca.pem" -keystore "$trust_dir/truststore.p12" \
  -storetype PKCS12 -storepass changeit >/dev/null 2>&1; then
  echo 'DB_CA_PEM is not a valid certificate. Paste the full downloaded Aiven ca.pem file.' >&2
  exit 1
fi
rm "$trust_dir/ca.pem"

# This truststore contains only the public CA certificate, no database password.
# Verify the certificate AND the hostname instead of merely encrypting traffic.
export DB_URL="${DB_URL}?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:${trust_dir}/truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
unset DB_CA_PEM

exec java -jar /app/app.jar
