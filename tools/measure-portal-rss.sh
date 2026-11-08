#!/bin/sh
# Compare real process resident memory on the SAME host and same Fabric world.
# Run once with SAMPLE_LABEL=baseline (no mod) and once with
# SAMPLE_LABEL=candidate (SeamlessPortals). No positional arguments needed.
# Optional environment: JAVA_PID, SAMPLE_COUNT (default 60), SAMPLE_INTERVAL (default 5).
set -eu

LABEL=${SAMPLE_LABEL:-candidate}
case "$LABEL" in
  *[!a-zA-Z0-9_-]*|"") echo "Invalid SAMPLE_LABEL" >&2; exit 2 ;;
esac
COUNT=${SAMPLE_COUNT:-60}
INTERVAL=${SAMPLE_INTERVAL:-5}
case "$COUNT" in ""|*[!0-9]*) echo "SAMPLE_COUNT must be numeric" >&2; exit 2;; esac
case "$INTERVAL" in ""|*[!0-9]*) echo "SAMPLE_INTERVAL must be numeric" >&2; exit 2;; esac
[ "$COUNT" -ge 5 ] && [ "$COUNT" -le 3600 ] || { echo "SAMPLE_COUNT out of range" >&2; exit 2; }
[ "$INTERVAL" -ge 1 ] && [ "$INTERVAL" -le 60 ] || { echo "SAMPLE_INTERVAL out of range" >&2; exit 2; }

if [ -z "${JAVA_PID:-}" ]; then
  command -v pgrep >/dev/null 2>&1 || { echo "Set JAVA_PID: pgrep is missing" >&2; exit 2; }
  matches=$(pgrep -f 'net.fabricmc.loader.impl.launch.knot.KnotServer|net.minecraft.server.Main' || true)
  [ -n "$matches" ] || { echo "No Java Minecraft server found. Set JAVA_PID explicitly." >&2; exit 2; }
  count=$(printf '%s\n' "$matches" | wc -l | tr -d ' ')
  [ "$count" -eq 1 ] || { echo "Multiple Java servers found; set JAVA_PID explicitly" >&2; exit 2; }
  JAVA_PID=$matches
fi
case "$JAVA_PID" in ""|*[!0-9]*) echo "Invalid JAVA_PID" >&2; exit 2;; esac
[ -r "/proc/$JAVA_PID/status" ] || { echo "Cannot read /proc/$JAVA_PID/status" >&2; exit 2; }

outdir=${OUTPUT_DIR:-./benchmarks}
mkdir -p "$outdir"
outfile="$outdir/rss_${LABEL}.csv"
printf 'sample,epoch,vmrss_kib\n' > "$outfile"
i=1
while [ "$i" -le "$COUNT" ]; do
  [ -r "/proc/$JAVA_PID/status" ] || { echo "Server exited during sampling" >&2; exit 1; }
  rss=$(awk '/^VmRSS:/ { print $2; exit }' "/proc/$JAVA_PID/status")
  case "$rss" in ""|*[!0-9]*) echo "RSS unavailable" >&2; exit 1;; esac
  printf '%s,%s,%s\n' "$i" "$(date +%s)" "$rss" >> "$outfile"
  i=$((i+1))
  [ "$i" -le "$COUNT" ] && sleep "$INTERVAL"
done

awk -F, 'NR>1 {print $3}' "$outfile" | sort -n | awk '
  { a[NR]=$1; sum+=$1 }
  END {
    n=NR
    if (n==0) exit 1
    i95=int(n*.95); if(i95<n*.95)i95++
    printf "RSS: median %.2f MiB, P95 %.2f MiB, max %.2f MiB (n=%d)\n", ((n%2)?a[(n+1)/2]:(a[n/2]+a[n/2+1])/2)/1024, a[i95]/1024, a[n]/1024, n
  }
'
printf 'CSV: %s\n' "$outfile"
if [ -f "$outdir/rss_baseline.csv" ] && [ -f "$outdir/rss_candidate.csv" ]; then
  awk -F, 'NR>1 {print $3}' "$outdir/rss_baseline.csv" | sort -n > "$outdir/.baseline.sorted"
  awk -F, 'NR>1 {print $3}' "$outdir/rss_candidate.csv" | sort -n > "$outdir/.candidate.sorted"
  med() {
    awk '{a[NR]=$1}END{n=NR;if(n==0)exit 1;print(n%2?a[(n+1)/2]:(a[n/2]+a[n/2+1])/2)}' "$1"
  }
  base=$(med "$outdir/.baseline.sorted")
  cand=$(med "$outdir/.candidate.sorted")
  rm -f "$outdir/.baseline.sorted" "$outdir/.candidate.sorted"
  awk -v base="$base" -v cand="$cand" 'BEGIN {
    delta=(cand-base)/1024;
    printf "Incremental idle RSS: %.2f MiB (candidate minus baseline)\n", delta;
    if(delta<=20) print "Target: PASS by median; verify P95 and drift separately";
    else print "Target: NOT MET by median";
  }'
fi
