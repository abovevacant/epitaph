#!/usr/bin/env bash
set -Eeuo pipefail
exec python3 "$(dirname "${BASH_SOURCE[0]}")/check_proto_schema.py" "$@"
