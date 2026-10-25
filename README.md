# UT99 Server Browser

A lightweight Java 11 desktop browser for Unreal Tournament servers listed by
[333networks](https://333networks.com/json).

## Run

For end users, download and extract the standalone Windows package, then run
`UT99 Browser.bat`; it includes its own Java runtime and does not require Java
to be installed. To build the package from source, install a Java 11 or newer
JDK and run `package-windows.bat`. The version in `version.txt` is used for
both the window title and the generated package name. Update that file for each
release. Developers can still use `run.bat` to compile and run the app from
source.

## Features

- Browse up to 1,000 Unreal Tournament (`ut`) servers from 333networks in one
  scrollable list (the API's maximum results per request).
- Maintain favorites list in app and in UT99's server browser with options to sync
  lists in either direction.
- Favorites can be saved to a external file, shared and loaded by another user.
- App automatically checks for latest version.
- Live query for selected servers.



