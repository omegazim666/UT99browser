# UT99 Server Browser

A lightweight Java 11 desktop browser for Unreal Tournament servers listed by
[333networks](https://333networks.com/json).

## Run

Install a Java 11 or newer JDK (a JRE alone is not enough), then double-click
`run.bat` or run it from a terminal. The script compiles the app into `out` and
launches the Swing UI. If `javac` is not found, add the JDK's `bin` directory to
`PATH` or set `JAVA_HOME` to the JDK installation folder. The launcher displays
a popup with a Java download button when Java is missing.

## Features

- Browse up to 1,000 Unreal Tournament (`ut`) servers from 333networks in one
  scrollable list (the API's maximum results per request).
- Search the server list loaded at startup by server name, map, game type, or
  another field in the cached results.
- The server list is fetched from 333networks at startup. Selecting a server
  queries its UT99 GameSpy status port directly; **Refresh server** repeats
  that live query without reloading the list.
- **Query all servers** manually checks the full startup list directly, one
  server at a time, with progress and a cancel option. It does not re-fetch
  the list from 333networks.
- Shows the last direct-query result in the server and favorites tables. UT99's
  query port is the game port plus one; a server that does not expose this query
  port will not return live status.
- Select a server to query its live status and player list directly from the
  UT99 server, including each player's score (frags).
- Right-click servers to add them to a Favorites tab; favorites are saved between
  launches in `.ut99-server-browser\favorites.properties` under your user home
  folder and can be removed from the same context menu.
- Open the About window for the game logo and Zim's Discord feedback link.
- About-window images load from `src\img` and do not need an internet connection.
- View server address, current/max players, map, game type, and country in the
  list; the detail view keeps IP:Port first and player columns in a fixed order.
- The server details section shows the selected IP and game port above the
  properties, with Copy and Refresh server buttons; the player table has no
  last-update column.
- Right-click a server in the main list to refresh it directly or copy its
  IP:game-port address. Selecting a server or clicking the selected row again
  continues to refresh its live status.
- Players are listed by frags, highest score first. Resized columns in the
  server, favorites, details, and player tables are saved as they are changed.
  The app also restores its window size, position, and server/details split
  divider; the initial window size is 1260 by 744 pixels.
- The main server list opens sorted by current player count, highest first.
  Users can change the sort priority by clicking any column header.
- Displays the required 333networks data attribution.
- Caches each API response and blocks the same request for 7.5 minutes, in line
  with 333networks API terms. Server-list search is local; live server status
  and player data are queried directly from the game server.

Direct UT99 status queries currently require an IPv4 address.

## Run tests

From the project folder, compile the app and tests, then run both test classes:

```bat
javac -encoding UTF-8 -d out src\main\java\ut99\*.java src\test\java\ut99\*.java
java -cp out ut99.JsonParserTest
java -cp out ut99.FavoriteStoreTest
java -cp out ut99.AboutImageAssetsTest
java -cp out ut99.GameServerQueryTest
```
