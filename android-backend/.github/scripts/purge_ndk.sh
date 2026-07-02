: ${1?"Usage: $0 NDK Version (ex 21.3.6528147)"}

echo "purging unused NDKs, keeping NDK $1..."

# (i know, i know)
brew install grep

list=$(sdkmanager --list_installed | ggrep -Eo "ndk;[0-9.]+")
for ver in $list
do
    if [ ! "ndk;$1" == "$ver" ];
    then
        echo "uninstalling ndk version $ver"
        sdkmanager --uninstall $ver
    fi
done

echo "done purging old NDKs!"