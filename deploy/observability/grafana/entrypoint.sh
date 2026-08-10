#!/bin/sh
set -eu

for plugin_id in tempo elasticsearch zipkin; do
  rm -rf -- "${GF_PATHS_PLUGINS:?}/${plugin_id}"
done

exec /run.sh "$@"
