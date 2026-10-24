package ut99;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.SortOrder;
import javax.swing.RowSorter;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.imageio.ImageIO;
import java.awt.datatransfer.StringSelection;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class ServerBrowserApp {
    private static final int MAX_SERVER_RESULTS = 1000;
    private static final Path GAME_LOGO_PATH = Path.of("src", "img", "Giblogo-mirror.gif");
    private static final Path DISCORD_ICON_PATH = Path.of("src", "img", "discord.png");
    private static final URI DISCORD_INVITE = URI.create("https://discord.com/invite/5AG6Zj96PG");
    private static final Preferences COLUMN_PREFERENCES =
            Preferences.userNodeForPackage(ServerBrowserApp.class).node("column-widths");

    private final ApiClient apiClient = new ApiClient();
    private final JFrame frame = new JFrame("Unreal Tournament 99 Server Browser " + readVersion());
    private final JTextField searchField = new JTextField(28);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel detailHeading = new JLabel("Select a server to view its players");
    private final JLabel selectedAddressLabel = new JLabel();
    private final JButton copyAddressButton = new JButton("Copy");
    private final Timer saveLayoutTimer = new Timer(500, event -> saveUiLayout());
    private final FavoriteStore favoriteStore = new FavoriteStore(
            Path.of(System.getProperty("user.home"), ".ut99-server-browser", "favorites.properties"));
    private final List<ServerEntry> currentServers = new ArrayList<>();
    private final List<ServerEntry> allServers = new ArrayList<>();
    private final List<ServerEntry> favorites = new ArrayList<>();
    private final Map<String, String> pingStatuses = new HashMap<>();
    private final Map<String, Long> pingRequestIds = new HashMap<>();
    private final DefaultTableModel serverModel = new DefaultTableModel(
            new Object[]{"Server", "Players", "Map", "Game type", "Country", "Address", "Live status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable serverTable = new JTable(serverModel);
    private final DefaultTableModel favoriteModel = new DefaultTableModel(
            new Object[]{"Server", "Players", "Map", "Game type", "Country", "Address", "Live status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable favoriteTable = new JTable(favoriteModel);
    private final DefaultTableModel detailModel = new DefaultTableModel(
            new Object[]{"Property", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable detailTable = new JTable(detailModel);
    private final DefaultTableModel playerModel = new DefaultTableModel(
            new Object[]{"Player", "Team", "Score (frags)", "Ping", "Mesh", "Skin", "Face", "Misc"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable playerTable = new JTable(playerModel);
    private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JButton searchButton = new JButton("Search");
    private final JButton pingButton = new JButton("Refresh server");
    private final JButton queryAllButton = new JButton("Query all servers");
    private SwingWorker<Void, ServerQueryUpdate> bulkQueryWorker;
    private volatile boolean bulkQueryStopRequested;
    private boolean favoritesTabSelected;
    private String searchQuery = "";
    private int totalServers;
    private ServerEntry selectedServer;

    private ServerBrowserApp() throws IOException {
        buildWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                showStartupError(e);
            }
            try {
                new ServerBrowserApp().show();
            } catch (IOException e) {
                showStartupError(e);
            }
        });
    }

    private static String readVersion() throws IOException {
        String version = Files.readString(Path.of("version.txt"), StandardCharsets.UTF_8).trim();
        if (!version.matches("B\\d+\\.\\d+")) {
            throw new IOException("version.txt must contain a version such as B1.3.");
        }
        return version.toLowerCase(java.util.Locale.ROOT);
    }

    private static void showStartupError(Exception e) {
        JOptionPane.showMessageDialog(null, "Unable to set the system look and feel: "
                        + e.getMessage(), "Startup warning", JOptionPane.WARNING_MESSAGE);
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 680));
        restoreWindowBounds();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        root.add(buildToolbar(), BorderLayout.NORTH);

        serverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configureMainServerSorter();
        serverTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectServer(serverTable, currentServers);
            }
        });
        installSelectedServerRefresh(serverTable, currentServers);
        favoriteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        favoriteTable.setAutoCreateRowSorter(true);
        favoriteTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectServer(favoriteTable, favorites);
            }
        });
        installSelectedServerRefresh(favoriteTable, favorites);
        detailTable.setAutoCreateRowSorter(false);
        playerTable.setAutoCreateRowSorter(false);
        configureColumnWidths(serverTable, "servers", new int[]{245, 50, 140, 105, 50, 115, 100});
        configureColumnWidths(favoriteTable, "favorites", new int[]{245, 50, 140, 105, 50, 115, 100});
        configureColumnWidths(detailTable, "details", new int[]{175, 250});
        configureColumnWidths(playerTable, "players", new int[]{130, 45, 85, 50, 110, 110, 60, 80});
        listenForColumnResizing(serverTable);
        listenForColumnResizing(favoriteTable);
        listenForColumnResizing(detailTable);
        listenForColumnResizing(playerTable);
        installFavoriteMenu(serverTable, currentServers, false);
        installFavoriteMenu(favoriteTable, favorites, true);

        JPanel serverPanel = new JPanel(new BorderLayout(4, 4));
        serverPanel.add(new JLabel("Unreal Tournament servers (select a server to view players)"), BorderLayout.NORTH);
        serverPanel.add(new JScrollPane(serverTable), BorderLayout.CENTER);

        JTabbedPane serverTabs = new JTabbedPane();
        serverTabs.addTab("Servers", serverPanel);
        JPanel favoritesPanel = new JPanel(new BorderLayout(4, 4));
        favoritesPanel.add(new JLabel("Right-click a server to remove it from favorites"), BorderLayout.NORTH);
        favoritesPanel.add(new JScrollPane(favoriteTable), BorderLayout.CENTER);
        serverTabs.addTab("Favorites", favoritesPanel);
        serverTabs.addChangeListener(event -> {
            favoritesTabSelected = serverTabs.getSelectedIndex() == 1;
            updateBulkQueryButton();
            JTable activeTable = favoritesTabSelected ? favoriteTable : serverTable;
            if (activeTable.getSelectedRow() >= 0) {
                selectServer(activeTable, activeTable == favoriteTable ? favorites : currentServers);
            } else {
                clearSelectedServer();
            }
        });

        JPanel detailPanel = new JPanel(new BorderLayout(6, 6));
        detailHeading.setFont(detailHeading.getFont().deriveFont(Font.BOLD, 16f));
        detailPanel.add(detailHeading, BorderLayout.NORTH);
        JPanel detailContent = new JPanel(new GridLayout(2, 1, 4, 8));
        detailContent.add(wrapServerDetailsTable());
        detailContent.add(wrapTable("Players and scores (frags)", playerTable));
        detailPanel.add(detailContent, BorderLayout.CENTER);

        splitPane.setLeftComponent(serverTabs);
        splitPane.setRightComponent(detailPanel);
        splitPane.setResizeWeight(0.67);
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                event -> scheduleLayoutSave());
        root.add(splitPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(statusLabel, BorderLayout.CENTER);
        JLabel attribution = new JLabel("Server data provided by 333networks", SwingConstants.RIGHT);
        footer.add(attribution, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                scheduleLayoutSave();
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                scheduleLayoutSave();
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveUiLayout();
            }
        });
        copyAddressButton.setEnabled(false);
        queryAllButton.setEnabled(false);
        copyAddressButton.setToolTipText("Copy the selected server IP and game port");
        copyAddressButton.addActionListener(event -> copySelectedAddress());
        searchButton.addActionListener(this::search);
        pingButton.addActionListener(event -> {
            if (selectedServer != null) {
                loadServerStatus(selectedServer);
            }
        });
        pingButton.setEnabled(false);
        queryAllButton.addActionListener(event -> {
            if (bulkQueryWorker == null) {
                queryServers(favoritesTabSelected ? favorites : allServers,
                        favoritesTabSelected ? "favorites" : "servers");
            } else {
                bulkQueryStopRequested = true;
                queryAllButton.setText("Stopping...");
                queryAllButton.setEnabled(false);
            }
        });
        searchField.addActionListener(this::search);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchControls.add(new JLabel("Search servers:"));
        searchControls.add(searchField);
        searchControls.add(searchButton);
        searchControls.add(queryAllButton);
        searchControls.add(new JLabel("(searches the server list loaded at startup)"));
        JButton aboutButton = new JButton("About");
        aboutButton.addActionListener(event -> showAboutDialog());
        toolbar.add(searchControls, BorderLayout.WEST);
        toolbar.add(aboutButton, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel wrapTable(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static void configureColumnWidths(JTable table, String tableKey, int[] defaults) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < table.getColumnModel().getColumnCount(); column++) {
            javax.swing.table.TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int modelIndex = tableColumn.getModelIndex();
            int width = COLUMN_PREFERENCES.getInt(tableKey + "." + modelIndex, defaults[modelIndex]);
            tableColumn.setPreferredWidth(width);
            tableColumn.setWidth(width);
        }
    }

    private void listenForColumnResizing(JTable table) {
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent event) {
                scheduleLayoutSave();
            }

            @Override
            public void columnAdded(TableColumnModelEvent event) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent event) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent event) {
                scheduleLayoutSave();
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent event) {
            }
        });
    }

    private void scheduleLayoutSave() {
        saveLayoutTimer.setRepeats(false);
        saveLayoutTimer.restart();
    }

    private void saveUiLayout() {
        try {
            saveColumnWidths(serverTable, "servers");
            saveColumnWidths(favoriteTable, "favorites");
            saveColumnWidths(detailTable, "details");
            saveColumnWidths(playerTable, "players");
            if (frame.getExtendedState() == JFrame.NORMAL) {
                COLUMN_PREFERENCES.putInt("window.x", frame.getX());
                COLUMN_PREFERENCES.putInt("window.y", frame.getY());
                COLUMN_PREFERENCES.putInt("window.width", frame.getWidth());
                COLUMN_PREFERENCES.putInt("window.height", frame.getHeight());
            }
            COLUMN_PREFERENCES.putInt("split.divider", splitPane.getDividerLocation());
            COLUMN_PREFERENCES.flush();
        } catch (BackingStoreException e) {
            statusLabel.setText("Could not save table column widths: " + e.getMessage());
        }
    }

    private void restoreWindowBounds() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(COLUMN_PREFERENCES.getInt("window.width", 1260), screen.width);
        int height = Math.min(COLUMN_PREFERENCES.getInt("window.height", 744), screen.height);
        width = Math.max(Math.min(width, screen.width), Math.min(1050, screen.width));
        height = Math.max(Math.min(height, screen.height), Math.min(680, screen.height));
        int x = COLUMN_PREFERENCES.getInt("window.x", (screen.width - width) / 2);
        int y = COLUMN_PREFERENCES.getInt("window.y", (screen.height - height) / 2);
        x = Math.max(0, Math.min(x, screen.width - width));
        y = Math.max(0, Math.min(y, screen.height - height));
        frame.setBounds(x, y, width, height);
    }

    private static void saveColumnWidths(JTable table, String tableKey) {
        for (int column = 0; column < table.getColumnModel().getColumnCount(); column++) {
            javax.swing.table.TableColumn tableColumn = table.getColumnModel().getColumn(column);
            COLUMN_PREFERENCES.putInt(tableKey + "." + tableColumn.getModelIndex(), tableColumn.getWidth());
        }
    }

    private JPanel wrapServerDetailsTable() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        JLabel ipLabel = new JLabel("IP:");
        ipLabel.setFont(ipLabel.getFont().deriveFont(Font.BOLD));
        JPanel addressLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addressLine.add(ipLabel);
        addressLine.add(selectedAddressLabel);
        addressLine.add(copyAddressButton);
        addressLine.add(pingButton);
        heading.add(addressLine, BorderLayout.NORTH);
        heading.add(new JLabel("Server details"), BorderLayout.SOUTH);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(new JScrollPane(detailTable), BorderLayout.CENTER);
        pingButton.setToolTipText("Refresh the selected server directly; no 333networks request is made.");
        return panel;
    }

    private void copySelectedAddress() {
        if (selectedServer == null) {
            return;
        }
        copyServerAddress(selectedServer);
    }

    private void copyServerAddress(ServerEntry server) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(server.address()), null);
            statusLabel.setText("Copied " + server.address() + " to clipboard");
        } catch (IllegalStateException e) {
            showError("Could not copy the server address: " + e.getMessage());
        }
    }

    private void show() {
        frame.setVisible(true);
        int savedDivider = COLUMN_PREFERENCES.getInt("split.divider", -1);
        if (savedDivider >= 0) {
            splitPane.setDividerLocation(savedDivider);
        } else {
            splitPane.setDividerLocation(0.67);
        }
        loadFavorites();
        loadServers();
    }

    private void search(ActionEvent event) {
        searchQuery = searchField.getText().trim();
        if (searchQuery.length() > 90) {
            showError("Search terms must be 90 characters or fewer.");
            return;
        }
        displayServers(filterServers(searchQuery));
        statusLabel.setText(String.format("Showing %,d of %,d servers from startup list",
                currentServers.size(), totalServers));
    }

    private void loadServers() {
        setBusy(true, "Loading server list...");
        new SwingWorker<ApiClient.ServerPage, Void>() {
            @Override
            protected ApiClient.ServerPage doInBackground() throws Exception {
                return apiClient.fetchServers(searchQuery, MAX_SERVER_RESULTS);
            }

            @Override
            protected void done() {
                try {
                    ApiClient.ServerPage result = get();
                    totalServers = result.total;
                    allServers.clear();
                    allServers.addAll(result.servers);
                    displayServers(filterServers(searchQuery));
                    statusLabel.setText(String.format("Showing %,d of %,d servers | %,d players",
                            currentServers.size(), totalServers, result.players));
                } catch (Exception e) {
                    showError(messageFor(e));
                } finally {
                    updateBulkQueryButton();
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void displayServers(List<ServerEntry> servers) {
        serverTable.clearSelection();
        currentServers.clear();
        currentServers.addAll(servers);
        serverModel.setRowCount(0);
        for (ServerEntry server : servers) {
            serverModel.addRow(new Object[]{
                    server.value("hostname", "Unnamed server"),
                    server.value("numplayers", "0") + " / " + server.value("maxplayers", "?"),
                    server.value("mapname", ""),
                    server.value("gametype", ""),
                    server.value("country", ""),
                    server.address(),
                    pingStatuses.getOrDefault(server.address(), "Not checked")
            });
        }
        clearDetails();
    }

    private void configureMainServerSorter() {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(serverModel);
        sorter.setComparator(1, (left, right) -> Integer.compare(
                playerCount(String.valueOf(left)), playerCount(String.valueOf(right))));
        sorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
        serverTable.setRowSorter(sorter);
    }

    static int playerCount(String value) {
        int separator = value.indexOf('/');
        String count = (separator < 0 ? value : value.substring(0, separator)).trim();
        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<ServerEntry> filterServers(String query) {
        List<ServerEntry> matches = new ArrayList<>();
        String normalizedQuery = query.toLowerCase(java.util.Locale.ROOT);
        for (ServerEntry server : allServers) {
            if (query.isEmpty() || server.fields.values().stream()
                    .anyMatch(value -> value != null
                            && String.valueOf(value).toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))) {
                matches.add(server);
            }
        }
        return matches;
    }

    private void selectServer(JTable table, List<ServerEntry> source) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            clearSelectedServer();
            return;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        selectedServer = source.get(modelRow);
        pingButton.setEnabled(bulkQueryWorker == null);
        selectedAddressLabel.setText(selectedServer.address());
        copyAddressButton.setEnabled(true);
        detailHeading.setText(selectedServer.address() + " — "
                + selectedServer.value("hostname", "Unnamed server"));
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
        detailModel.addRow(new Object[]{"Live status", "Querying game server..."});
        loadServerStatus(selectedServer);
    }

    private void installSelectedServerRefresh(JTable table, List<ServerEntry> source) {
        table.addMouseListener(new MouseAdapter() {
            private ServerEntry serverSelectedOnPress;

            @Override
            public void mousePressed(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                serverSelectedOnPress = row >= 0 && table.getSelectedRow() == row
                        ? source.get(table.convertRowIndexToModel(row))
                        : null;
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int row = table.rowAtPoint(event.getPoint());
                if (row < 0 || table.getSelectedRow() != row || serverSelectedOnPress == null) {
                    return;
                }
                ServerEntry server = source.get(table.convertRowIndexToModel(row));
                if (server == selectedServer && server == serverSelectedOnPress) {
                    loadServerStatus(server);
                }
            }
        });
    }

    private void clearSelectedServer() {
        selectedServer = null;
        selectedAddressLabel.setText("");
        copyAddressButton.setEnabled(false);
        pingButton.setEnabled(false);
        detailHeading.setText("Select a server to view its players");
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
    }

    private void clearDetails() {
        clearSelectedServer();
    }

    private void loadServerStatus(ServerEntry server) {
        if (bulkQueryWorker != null) {
            return;
        }
        String address = server.address();
        long requestId = pingRequestIds.getOrDefault(address, 0L) + 1;
        pingRequestIds.put(address, requestId);
        pingStatuses.put(server.address(), "Querying...");
        updatePingStatus(server);
        pingButton.setEnabled(false);
        if (selectedServer == server) {
            detailModel.setRowCount(0);
            detailModel.addRow(new Object[]{"Live status", "Querying game server..."});
            playerModel.setRowCount(0);
            statusLabel.setText("Querying UT99 server at " + server.address() + "...");
        }
        new SwingWorker<GameServerQuery.Result, Void>() {
            @Override
            protected GameServerQuery.Result doInBackground() throws Exception {
                return GameServerQuery.query(server, 3000);
            }

            @Override
            protected void done() {
                if (!Long.valueOf(requestId).equals(pingRequestIds.get(address))) {
                    return;
                }
                try {
                    GameServerQuery.Result result = get();
                    applyServerStatus(server, result.fields);
                    pingStatuses.put(server.address(), "Online (" + result.roundTripMillis + " ms)");
                    updatePingStatus(server);
                    if (selectedServer == server) {
                        displayDetails(server, result.fields, result.roundTripMillis);
                        detailHeading.setText(server.address() + " — "
                                + result.fields.getOrDefault("hostname",
                                        server.value("hostname", "Unnamed server")));
                        statusLabel.setText("Updated live status for " + server.address()
                                + " in " + result.roundTripMillis + " ms");
                    }
                } catch (Exception e) {
                    pingStatuses.put(server.address(), "No status response");
                    updatePingStatus(server);
                    if (selectedServer == server) {
                        String error = messageFor(e);
                        statusLabel.setText("Live query failed for " + server.address() + ": " + error);
                        detailModel.setValueAt("Query failed: " + error, 0, 1);
                    }
                } finally {
                    pingRequestIds.remove(address, requestId);
                    pingButton.setEnabled(selectedServer != null);
                }
            }
        }.execute();
    }

    private void updateBulkQueryButton() {
        if (bulkQueryWorker != null) {
            return;
        }
        queryAllButton.setText(favoritesTabSelected ? "Refresh all favorites" : "Query all servers");
        queryAllButton.setEnabled(favoritesTabSelected ? !favorites.isEmpty() : !allServers.isEmpty());
    }

    private void queryServers(List<ServerEntry> source, String listName) {
        if (source.isEmpty()) {
            statusLabel.setText(favoritesTabSelected
                    ? "No favorites are available to refresh."
                    : "No servers are loaded to query.");
            return;
        }
        List<ServerEntry> serversToQuery = new ArrayList<>(source);
        int total = serversToQuery.size();
        queryAllButton.setText("Cancel queries");
        pingButton.setEnabled(false);
        bulkQueryWorker = new SwingWorker<Void, ServerQueryUpdate>() {
            private int completed;
            private int successful;
            private int failed;

            @Override
            protected Void doInBackground() {
                int attempted = 0;
                for (ServerEntry server : serversToQuery) {
                    if (bulkQueryStopRequested) {
                        break;
                    }
                    try {
                        publish(new ServerQueryUpdate(server, GameServerQuery.query(server, 1000), null));
                    } catch (Exception e) {
                        publish(new ServerQueryUpdate(server, null, messageFor(e)));
                    }
                    attempted++;
                    if (attempted < total && !bulkQueryStopRequested) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<ServerQueryUpdate> updates) {
                for (ServerQueryUpdate update : updates) {
                    if (update.result == null) {
                        failed++;
                        pingStatuses.put(update.server.address(), "No status response");
                        if (selectedServer == update.server) {
                            detailModel.setRowCount(0);
                            detailModel.addRow(new Object[]{"Live status", "Query failed: " + update.error});
                        }
                    } else {
                        successful++;
                        applyServerStatus(update.server, update.result.fields);
                        pingStatuses.put(update.server.address(),
                                "Online (" + update.result.roundTripMillis + " ms)");
                        if (selectedServer == update.server) {
                            displayDetails(update.server, update.result.fields, update.result.roundTripMillis);
                        }
                    }
                    updatePingStatus(update.server);
                    completed++;
                    statusLabel.setText(String.format("Queried %,d of %,d servers | %,d online | %,d no response",
                            completed, total, successful, failed));
                }
            }

            @Override
            protected void done() {
                boolean cancelled = bulkQueryStopRequested;
                bulkQueryWorker = null;
                bulkQueryStopRequested = false;
                updateBulkQueryButton();
                pingButton.setEnabled(selectedServer != null);
                statusLabel.setText(String.format("%s %,d of %,d servers | %,d online | %,d no response",
                        cancelled ? "Stopped refreshing" : "Finished refreshing",
                        completed, total, successful, failed));
            }
        };
        statusLabel.setText("Refreshing " + total + " " + listName + " directly...");
        bulkQueryWorker.execute();
    }

    private static final class ServerQueryUpdate {
        final ServerEntry server;
        final GameServerQuery.Result result;
        final String error;

        ServerQueryUpdate(ServerEntry server, GameServerQuery.Result result, String error) {
            this.server = server;
            this.result = result;
            this.error = error;
        }
    }

    private void updatePingStatus(ServerEntry server) {
        updatePingStatus(currentServers, serverModel, server);
        updatePingStatus(favorites, favoriteModel, server);
    }

    private void updatePingStatus(List<ServerEntry> source, DefaultTableModel model, ServerEntry server) {
        for (int row = 0; row < source.size(); row++) {
            if (source.get(row).address().equalsIgnoreCase(server.address())) {
                model.setValueAt(pingStatuses.get(server.address()), row, 6);
            }
        }
    }

    private void applyServerStatus(ServerEntry server, Map<String, String> fields) {
        for (String key : new String[]{"hostname", "mapname", "gametype", "numplayers", "maxplayers"}) {
            String value = fields.get(key);
            if (value != null) {
                server.fields.put(key, value);
            }
        }
        updateServerRow(currentServers, serverModel, server);
        updateServerRow(favorites, favoriteModel, server);
    }

    private void updateServerRow(List<ServerEntry> source, DefaultTableModel model, ServerEntry updatedServer) {
        for (int row = 0; row < source.size(); row++) {
            ServerEntry server = source.get(row);
            if (server.address().equalsIgnoreCase(updatedServer.address())) {
                server.fields.putAll(updatedServer.fields);
                model.setValueAt(server.value("hostname", "Unnamed server"), row, 0);
                model.setValueAt(server.value("numplayers", "0") + " / " + server.value("maxplayers", "?"),
                        row, 1);
                model.setValueAt(server.value("mapname", ""), row, 2);
                model.setValueAt(server.value("gametype", ""), row, 3);
                model.setValueAt(server.address(), row, 5);
            }
        }
    }

    private void displayDetails(ServerEntry server, Map<String, String> details, long roundTripMillis) {
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
        detailHeading.setText(server.address() + " — "
                + details.getOrDefault("hostname", server.value("hostname", "Server details")));
        detailModel.addRow(new Object[]{"Data source", "Live UT99 server query"});
        detailModel.addRow(new Object[]{"Query response time", roundTripMillis + " ms"});

        String[][] preferredFields = {
                {"hostname", "Server"},
                {"mapname", "Map"},
                {"gametype", "Game type"},
                {"numplayers", "Players"},
                {"maxplayers", "Max players"},
                {"password", "Password protected"},
                {"gamever", "Game version"}
        };
        for (String[] field : preferredFields) {
            String value = details.get(field[0]);
            if (value != null) {
                detailModel.addRow(new Object[]{field[1], value});
            }
        }

        details.entrySet().stream()
                .filter(entry -> !isDisplayedDetailField(entry.getKey()))
                .filter(entry -> !entry.getKey().matches(".*_\\d+$"))
                .filter(entry -> !isUpdateTimestampField(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> detailModel.addRow(new Object[]{entry.getKey(), entry.getValue()}));

        Map<Integer, Map<String, String>> players = new java.util.TreeMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            java.util.regex.Matcher playerField = java.util.regex.Pattern
                    .compile("^(.*)_(\\d+)$").matcher(entry.getKey());
            if (playerField.matches()) {
                int playerId = Integer.parseInt(playerField.group(2));
                players.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                        .put(playerField.group(1), entry.getValue());
            }
        }
        List<Map<String, String>> sortedPlayers = sortPlayersByScore(new ArrayList<>(players.values()));
        for (Map<String, String> player : sortedPlayers) {
            playerModel.addRow(new Object[]{
                    player.getOrDefault("playername", player.getOrDefault("player", "")),
                    player.getOrDefault("team", ""),
                    player.getOrDefault("frags", ""),
                    player.getOrDefault("ping", ""),
                    player.getOrDefault("mesh", ""),
                    player.getOrDefault("skin", ""),
                    player.getOrDefault("face", ""),
                    player.getOrDefault("misc", "")
            });
        }
    }

    static List<Map<String, String>> sortPlayersByScore(List<Map<String, String>> players) {
        players.sort(Comparator
                .comparingInt((Map<String, String> player) -> playerScore(player.get("frags")))
                .reversed()
                .thenComparing(player -> player.getOrDefault("playername",
                        player.getOrDefault("player", "")), String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private static int playerScore(String score) {
        if (score == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(score.trim());
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean isDisplayedDetailField(String key) {
        for (String field : new String[]{"hostname", "mapname", "gametype", "numplayers", "maxplayers",
                "password", "gamever"}) {
            if (field.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUpdateTimestampField(String key) {
        String normalized = key.replace("_", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("dt") || normalized.contains("update");
    }

    private void setBusy(boolean busy, String message) {
        searchButton.setEnabled(!busy);
        searchField.setEnabled(!busy);
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    private void loadFavorites() {
        try {
            favorites.clear();
            favorites.addAll(favoriteStore.load());
            displayServersInModel(favorites, favoriteModel);
        } catch (IOException e) {
            showError("Could not load saved favorites: " + e.getMessage());
        }
    }

    private void installFavoriteMenu(JTable table, List<ServerEntry> source, boolean favoritesView) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopup(event);
            }

            private void showPopup(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(event.getPoint());
                if (row < 0) {
                    return;
                }
                table.setRowSelectionInterval(row, row);
                ServerEntry server = source.get(table.convertRowIndexToModel(row));
                boolean isFavorite = containsFavorite(server);
                JPopupMenu menu = new JPopupMenu();
                if (!favoritesView) {
                    javax.swing.JMenuItem refresh = new javax.swing.JMenuItem("Refresh selected server");
                    refresh.setEnabled(bulkQueryWorker == null);
                    refresh.addActionListener(action -> loadServerStatus(server));
                    menu.add(refresh);
                    javax.swing.JMenuItem copyAddress = new javax.swing.JMenuItem("Copy IP");
                    copyAddress.addActionListener(action -> copyServerAddress(server));
                    menu.add(copyAddress);
                    menu.addSeparator();
                }
                if (favoritesView || isFavorite) {
                    javax.swing.JMenuItem remove = new javax.swing.JMenuItem("Remove from favorites");
                    remove.addActionListener(action -> removeFavorite(server));
                    menu.add(remove);
                } else {
                    javax.swing.JMenuItem add = new javax.swing.JMenuItem("Add to favorites");
                    add.addActionListener(action -> addFavorite(server));
                    menu.add(add);
                }
                menu.show(table, event.getX(), event.getY());
            }
        });
    }

    private boolean containsFavorite(ServerEntry server) {
        for (ServerEntry favorite : favorites) {
            if (favorite.address().equalsIgnoreCase(server.address())) {
                return true;
            }
        }
        return false;
    }

    private void addFavorite(ServerEntry server) {
        if (containsFavorite(server)) {
            return;
        }
        favorites.add(server);
        if (!saveFavorites()) {
            favorites.remove(server);
            displayServersInModel(favorites, favoriteModel);
        }
    }

    private void removeFavorite(ServerEntry server) {
        int index = -1;
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).address().equalsIgnoreCase(server.address())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        ServerEntry removed = favorites.remove(index);
        if (!saveFavorites()) {
            favorites.add(index, removed);
            displayServersInModel(favorites, favoriteModel);
            return;
        }
        if (selectedServer != null && selectedServer.address().equalsIgnoreCase(server.address())) {
            clearSelectedServer();
        }
    }

    private boolean saveFavorites() {
        try {
            favoriteStore.save(favorites);
            displayServersInModel(favorites, favoriteModel);
            statusLabel.setText(favorites.size() + " favorite server(s) saved");
            return true;
        } catch (IOException e) {
            showError("Could not save favorites: " + e.getMessage());
            return false;
        }
    }

    private void displayServersInModel(List<ServerEntry> servers, DefaultTableModel model) {
        model.setRowCount(0);
        for (ServerEntry server : servers) {
            model.addRow(new Object[]{
                    server.value("hostname", "Unnamed server"),
                    server.value("numplayers", "0") + " / " + server.value("maxplayers", "?"),
                    server.value("mapname", ""),
                    server.value("gametype", ""),
                    server.value("country", ""),
                    server.address(),
                    pingStatuses.getOrDefault(server.address(), "Not checked")
            });
        }
        if (model == favoriteModel) {
            updateBulkQueryButton();
        }
    }

    private void showAboutDialog() {
        JDialog dialog = new JDialog(frame, "About UT99 Server Browser", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        JLabel logo = new JLabel("Loading logo...", SwingConstants.CENTER);
        content.add(logo, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(0, 1, 6, 6));
        JLabel title = new JLabel("Unreal Tournament server browser by Zim", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        center.add(title);
        JButton discordButton = new JButton("Discord");
        discordButton.setHorizontalAlignment(SwingConstants.CENTER);
        discordButton.setIconTextGap(8);
        discordButton.addActionListener(event -> openDiscordInvite());
        center.add(discordButton);
        center.add(new JLabel("Any feedback/ideas contact me on discord", SwingConstants.CENTER));
        content.add(center, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(370, 270));
        dialog.setLocationRelativeTo(frame);
        loadAboutImages(dialog, logo, discordButton);
        dialog.setVisible(true);
    }

    private void loadAboutImages(JDialog dialog, JLabel logo, JButton discordButton) {
        new SwingWorker<Image[], Void>() {
            @Override
            protected Image[] doInBackground() throws Exception {
                return new Image[]{loadImage(GAME_LOGO_PATH), loadImage(DISCORD_ICON_PATH)};
            }

            @Override
            protected void done() {
                try {
                    Image[] images = get();
                    Image gameLogo = images[0];
                    logo.setText("");
                    int logoWidth = Math.max(1, gameLogo.getWidth(null) / 2);
                    int logoHeight = Math.max(1, gameLogo.getHeight(null) / 2);
                    logo.setPreferredSize(new Dimension(logoWidth, logoHeight));
                    logo.setIcon(new javax.swing.ImageIcon(gameLogo.getScaledInstance(
                            logoWidth, logoHeight, Image.SCALE_SMOOTH)));
                    Image discordIcon = images[1].getScaledInstance(24, 24, Image.SCALE_SMOOTH);
                    discordButton.setIcon(new javax.swing.ImageIcon(discordIcon));
                    dialog.pack();
                    dialog.setLocationRelativeTo(frame);
                } catch (Exception e) {
                    logo.setText("Logo could not be loaded");
                    statusLabel.setText("Could not load About window images: " + messageFor(e));
                }
            }
        }.execute();
    }

    private static Image loadImage(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Image file not found: " + path.toAbsolutePath());
        }
        Image image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported image file: " + path.toAbsolutePath());
        }
        return image;
    }

    private void openDiscordInvite() {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Opening web links is not supported on this system.");
            }
            Desktop.getDesktop().browse(DISCORD_INVITE);
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(frame, "Could not open the Discord invite: " + e.getMessage(),
                    "Unable to open Discord", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showError(String message) {
        statusLabel.setText("Request failed");
        JOptionPane.showMessageDialog(frame, message, "UT99 Server Browser error",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String messageFor(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private static String displayValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
