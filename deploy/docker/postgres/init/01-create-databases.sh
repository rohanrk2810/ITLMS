#!/bin/sh
# ---------------------------------------------------------------------------
# One database per service.
#
# Runs once, when the Postgres volume is first initialised. Each service owns
# its database outright and no service ever connects to another's - that is
# what lets a service change its own schema without coordinating a release
# with anyone else.
#
# Development shortcut: every database is owned by the same role. In
# production, give each service its own role with rights on its own database
# only, so a compromised service cannot read the others' data.
# ---------------------------------------------------------------------------
set -eu

for name in identity admission course batch assessment finance \
            certificate placement notification file liveclass reporting; do
    echo "Creating database itilms_${name}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -c "CREATE DATABASE itilms_${name} OWNER \"${POSTGRES_USER}\";"
done
