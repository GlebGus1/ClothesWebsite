#!/usr/bin/env bash
cd "$(dirname "$0")/.." && source scripts/env.sh && "$MVN" -B "$@"
