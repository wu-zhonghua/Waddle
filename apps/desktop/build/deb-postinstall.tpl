#!/bin/bash

if type update-alternatives 2>/dev/null >&1; then
    # Remove previous link if it doesn't use update-alternatives
    if [ -L '/usr/bin/waddle' -a -e '/usr/bin/waddle' -a "`readlink '/usr/bin/waddle'`" != '/etc/alternatives/waddle' ]; then
        rm -f '/usr/bin/waddle'
    fi
    update-alternatives --install '/usr/bin/waddle' 'waddle' '/opt/Waddle/waddle' 100 || ln -sf '/opt/Waddle/waddle' '/usr/bin/waddle'
else
    ln -sf '/opt/Waddle/waddle' '/usr/bin/waddle'
fi

chmod 4755 '/opt/Waddle/chrome-sandbox' || true

if hash update-mime-database 2>/dev/null; then
    update-mime-database /usr/share/mime || true
fi

if hash update-desktop-database 2>/dev/null; then
    update-desktop-database /usr/share/applications || true
fi
