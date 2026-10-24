# UT99 Server Browser

A lightweight Java 11 desktop browser for Unreal Tournament servers listed by
[333networks](https://333networks.com/json).

## Run

Install a Java 11 or newer JDK, then double-click `run.bat` or run it from a
terminal. The script compiles the app into `out` and launches the Swing UI.

## Features

- Browse up to 1,000 Unreal Tournament (`ut`) servers from 333networks in one
  scrollable list (the API's maximum results per request).
- Search by server name, map, game type, or another API-supported search term.
- Select a server to automatically load its detailed properties and player list,
  including each player's score (frags).
- Right-click servers to add them to a Favorites tab; favorites are saved between
  launches in `.ut99-server-browser\favorites.properties` under your user home
  folder and can be removed from the same context menu.
- Open the About window for the game logo and Zim's Discord feedback link.
- About-window images load from `src\img` and do not need an internet connection.
- View server address, current/max players, map, game type, and country in the
  list; sortable columns and detail tables are available.
- Displays the required 333networks data attribution.
- Caches each API response and blocks the same request for 7.5 minutes, in line
  with 333networks API terms. The server list loads on startup or search, and
  server details load only when a server is selected.

The 333networks detail endpoint documents IPv4 addresses; selecting a server
with a native IPv6 address will show a clear message that its details cannot be
loaded through that endpoint.

## Run tests

From the project folder, compile the app and tests, then run both test classes:

```bat
javac -encoding UTF-8 -d out src\main\java\ut99\*.java src\test\java\ut99\*.java
java -cp out ut99.JsonParserTest
java -cp out ut99.FavoriteStoreTest
java -cp out ut99.AboutImageAssetsTest
```
