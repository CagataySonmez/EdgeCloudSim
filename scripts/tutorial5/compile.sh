#!/usr/bin/env sh
set -eu
exec "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)/compile.sh"
