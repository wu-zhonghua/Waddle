#!/data/data/com.termux/files/usr/bin/bash
# Read-only probes. Run as an ordinary Termux app process, not adb shell/root.
# Do not expand this list to accounts, credentials, contacts, or personal content.
set -u
probe() {
    local name=$1 output status
    shift
    output=$(timeout 8 "$@" 2>&1)
    status=$?
    printf '\n[%s] exit=%s\n%.3000s\n' "$name" "$status" "$output"
}
probe identity /system/bin/id
probe selinux /system/bin/id -Z
probe android_version /system/bin/getprop ro.build.version.release
probe cpu_abi /system/bin/getprop ro.product.cpu.abi
probe timezone /system/bin/getprop persist.sys.timezone
probe system_shell_config /system/bin/cat /system/etc/mkshrc
probe own_test_file /system/bin/cat ./public-test.txt
probe brightness /system/bin/settings get system screen_brightness
probe brightness_mode /system/bin/settings get system screen_brightness_mode
probe airplane_mode /system/bin/settings get global airplane_mode_on
probe accessibility_enabled /system/bin/settings get secure accessibility_enabled
own_user=$(( $(/system/bin/id -u) / 100000 ))
# Select only this app's Android user. Default "current user" lookup itself
# requires a separate permission and can fail before checking the setting.
probe brightness_own_user /system/bin/settings --user "$own_user" get system screen_brightness
probe brightness_mode_own_user /system/bin/settings --user "$own_user" get system screen_brightness_mode
probe airplane_mode_own_user /system/bin/settings --user "$own_user" get global airplane_mode_on
probe accessibility_own_user /system/bin/settings --user "$own_user" get secure accessibility_enabled
probe brightness_content_provider /system/bin/content query --uri content://settings/system --projection name:value --where "name='screen_brightness'"
probe brightness_content_system_path /system/bin/env PATH=/system/bin:/data/data/com.termux/files/usr/bin /system/bin/content query --user "$own_user" --uri content://settings/system --projection name:value --where "name='screen_brightness'"
probe shared_download_directory /system/bin/ls /sdcard/Download
# Precondition: Waddle is installed and its public probe fixture exists.
probe other_app_test_file /system/bin/cat /data/data/dev.waddle.android/files/access-probe-public.txt
probe system_data_directory /system/bin/ls /data/system
probe current_window /system/bin/dumpsys -t 3 window displays
probe visible_packages /system/bin/pm list packages dev.waddle.android
printf '\nACCESS_PROBE_COMPLETE\n'
