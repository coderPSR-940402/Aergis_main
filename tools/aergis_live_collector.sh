#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ $# -eq 0 ]]; then set -- status; fi
case "$1" in
  start|stop)
    action=up
    [[ "$1" == stop ]] && action=down
    exec sv "$action" "${PREFIX:-/data/data/com.termux/files/usr}/var/service/aergis-devloop"
    ;;
  install)
    shift
    exec python3 "$script_dir/install_devloop_toolbox.py" "$@"
    ;;
  *) exec python3 "$script_dir/aergis_live_collector.py" "$@" ;;
esac
