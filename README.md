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
- Search the server list loaded at startup by server name, map, game type, or
  another field in the cached results.
- The server list is fetched from 333networks at startup. Selecting a server
  queries its UT99 GameSpy status port directly; **Refresh server** repeats
  that live query without reloading the list.
- Checks GitHub for a newer release at startup and offers to open the release
  page; the check runs in the background and does not block startup.
- **Query all servers** manually checks the full startup list directly, one
  server at a time, with progress and a cancel option. It does not re-fetch
  the list from 333networks.
- **Add favorite** opens an editor for a server description, address, game port,
  and query port; long server details such as mutators wrap onto multiple lines.
- In the Favorites tab, the same button becomes **Refresh all favorites** and
  directly queries only the saved favorite servers.
- Right-click a favorite and select **Edit favorite** to change its description,
  auto-update setting, IP address, game port, or query port.
- Use **File > Save list** and **File > Load list** to export or import `.lst`
  favorite lists; loading can replace or merge favorites and avoids duplicate
  server addresses.
- Use **File > Favorites Sync** to fetch favorites from or sync favorites to the
  UT99 game browser. Browse to `UnrealTournament.ini` and choose whether to
  merge or replace the destination list; merge avoids duplicate server
  addresses.
- Choose **Themes > Default** or **Dark** to switch the interface colors. The
  selected theme is marked with a check and remembered between launches.
- Shows the last direct-query result in the server and favorites tables. UT99's
  query port is the game port plus one; a server that does not expose this query
  port will not return live status.
- Select a server to query its live status and player list directly from the
  UT99 server, including each player's score (frags).
- Right-click servers to add them to a Favorites tab; favorites are saved between
  launches in `.ut99-server-browser\favorites.properties` under your user home
  folder and can be removed from the same context menu.
- Open the **About** menu beside Themes for the game logo and Zim's Discord
  feedback link.
- About-window images load from `src\img` and do not need an internet connection.
- View server address, current/max players, map, game type, and country in the
  list; the detail view keeps IP:Port first and player columns in a fixed order.
- The server details section shows the selected IP and game port above the
  properties, with Copy and Refresh server buttons; the player table has no
  last-update column.
- Right-click a server in the main list to refresh it directly or copy its
  IP:game-port address. Selecting a server or clicking the selected row again
  continues to refresh its live status.
- Server admin email addresses and homepages appear as clickable links in the
  server details table; email addresses display without the `mailto:` prefix.
Direct UT99 status queries currently require an IPv4 address.
