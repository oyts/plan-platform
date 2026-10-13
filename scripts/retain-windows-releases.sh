#!/bin/sh
# Copy only immutable Windows EXE/signature files; never old feeds or private metadata.
set -eu
old=${1:?Previous public root required}
new=${2:?New public root required}
test -d "$old" && test -d "$new"
test -z "$(find "$old" -type l -print)" || { echo 'Previous public tree contains symlinks'; exit 1; }
for scope in downloads client-updates/windows/releases; do
  [ -d "$old/$scope" ] || continue
  for source in "$old/$scope"/*; do
    [ -d "$source" ] || continue
    version=${source##*/}
    printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || { echo 'Unexpected release directory'; exit 1; }
    name="Plan Cabin_${version}_x64-setup.exe"
    for file in "$source"/*; do
      [ -f "$file" ] || { echo 'Unexpected release entry'; exit 1; }
      case "${file##*/}" in "$name"|"$name.sig") ;; *) echo 'Non-Windows file refused'; exit 1;; esac
      test -s "$file"
      target="$new/$scope/$version/${file##*/}"
      if [ -e "$target" ]; then
        cmp -s "$file" "$target" || { echo 'Immutable release collision'; exit 1; }
      else
        mkdir -p "$new/$scope/$version"
        cp -p "$file" "$target"
        cmp -s "$file" "$target" || { echo 'Retained release copy differs'; exit 1; }
      fi
    done
  done
done
echo 'Previous immutable Windows installers retained byte-for-byte.'
