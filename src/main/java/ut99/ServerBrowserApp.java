package ut99;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.JViewport;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.RowSorter;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.text.JTextComponent;
import javax.imageio.ImageIO;
import java.awt.datatransfer.StringSelection;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class ServerBrowserApp {
    private static final int MAX_SERVER_RESULTS = 1000;
    private static final Path GAME_LOGO_PATH = Path.of("src", "img", "Giblogo-mirror.gif");
    private static final Path DISCORD_ICON_PATH = Path.of("src", "img", "discord.png");
    private static final URI DISCORD_INVITE = URI.create("https://discord.gibblets.com/");
    private static final Preferences COLUMN_PREFERENCES =
            Preferences.userNodeForPackage(ServerBrowserApp.class).node("column-widths");
    private static final ColorPalette DARK_PALETTE = new ColorPalette(
            Color.BLACK, Color.BLACK, new Color(240, 240, 240),
            new Color(184, 210, 241), new Color(0, 0, 160), Color.WHITE,
            Color.BLACK, new Color(112, 153, 199), new Color(112, 153, 199), Color.BLACK);
    private static final String[] THEME_UI_KEYS = {
            "Panel.background", "Panel.foreground", "Label.foreground",
            "Button.background", "Button.foreground", "ToggleButton.background",
            "ToggleButton.foreground", "MenuBar.background", "MenuBar.foreground",
            "Menu.background", "Menu.foreground", "Menu.selectionBackground",
            "Menu.selectionForeground", "MenuItem.background", "MenuItem.foreground",
            "MenuItem.selectionBackground", "MenuItem.selectionForeground",
            "TabbedPane.background", "TabbedPane.foreground", "TabbedPane.selected",
            "TabbedPane.contentAreaColor", "TabbedPane.tabAreaBackground",
            "ScrollBar.background", "ScrollBar.foreground", "ScrollBar.track",
            "ScrollBar.trackHighlight", "ScrollBar.thumb", "ScrollBar.thumbDarkShadow",
            "ScrollBar.thumbHighlight", "ScrollBar.thumbShadow",
            "Table.background", "Table.foreground", "Table.selectionBackground",
            "Table.selectionForeground", "Table.gridColor", "TableHeader.background",
            "TableHeader.foreground", "TextField.background", "TextField.foreground",
            "TextArea.background", "TextArea.foreground", "TextField.caretForeground",
            "TextArea.caretForeground", "TextField.selectionBackground",
            "TextArea.selectionBackground", "TextField.selectionForeground",
            "TextArea.selectionForeground"
    };
    private static final Map<String, Object> DEFAULT_UI_VALUES = new HashMap<>();

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
    private final JButton addFavoriteButton = new JButton("Add favorite");
    private final JButton pingButton = new JButton("Refresh server");
    private final JButton queryAllButton = new JButton("Query all servers");
    private final Map<Component, ComponentColors> defaultComponentColors = new IdentityHashMap<>();
    private final Map<Theme, JCheckBoxMenuItem> themeMenuItems = new EnumMap<>(Theme.class);
    private SwingWorker<Void, ServerQueryUpdate> bulkQueryWorker;
    private volatile boolean bulkQueryStopRequested;
    private Theme activeTheme = Theme.DEFAULT;
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
            captureDefaultUiValues();
            try {
                ServerBrowserApp app = new ServerBrowserApp();
                app.applyTheme(savedTheme());
                app.show();
                app.checkForUpdates();
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

    private static void captureDefaultUiValues() {
        for (String key : THEME_UI_KEYS) {
            DEFAULT_UI_VALUES.put(key, UIManager.get(key));
        }
    }

    private static void restoreDefaultUiValues() {
        for (String key : THEME_UI_KEYS) {
            UIManager.put(key, DEFAULT_UI_VALUES.get(key));
        }
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 680));
        restoreWindowBounds();
        frame.setJMenuBar(buildMenuBar());

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
        detailTable.getColumnModel().getColumn(1).setCellRenderer(new WrappedTextRenderer());
        detailTable.getModel().addTableModelListener(event ->
                SwingUtilities.invokeLater(this::adjustDetailRowHeights));
        detailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = detailTable.rowAtPoint(event.getPoint());
                int column = detailTable.columnAtPoint(event.getPoint());
                if (row >= 0 && column == 1) {
                    String key = String.valueOf(detailTable.getValueAt(row, 0));
                    String value = String.valueOf(detailTable.getValueAt(row, column));
                    openServerDetailLink(key, value);
                }
            }
        });
        detailTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int row = detailTable.rowAtPoint(event.getPoint());
                int column = detailTable.columnAtPoint(event.getPoint());
                String key = row >= 0 ? String.valueOf(detailTable.getValueAt(row, 0)) : "";
                detailTable.setCursor(column == 1 && isLinkDetail(key)
                        ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                        : java.awt.Cursor.getDefaultCursor());
            }
        });
        detailTable.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                adjustDetailRowHeights();
            }
        });
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
        JPanel favoritesHeader = new JPanel(new BorderLayout(8, 0));
        favoritesHeader.add(new JLabel("Right-click a server to remove it from favorites"),
                BorderLayout.CENTER);
        favoritesHeader.add(addFavoriteButton, BorderLayout.EAST);
        favoritesPanel.add(favoritesHeader, BorderLayout.NORTH);
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
        captureDefaultComponentColors(frame);
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
        addFavoriteButton.addActionListener(event -> addFavoriteManually());
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
                if (table == detailTable) {
                    SwingUtilities.invokeLater(ServerBrowserApp.this::adjustDetailRowHeights);
                }
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

    private void adjustDetailRowHeights() {
        if (detailTable.getColumnModel().getColumnCount() < 2) {
            return;
        }
        int valueColumnWidth = detailTable.getColumnModel().getColumn(1).getWidth()
                - detailTable.getIntercellSpacing().width;
        for (int row = 0; row < detailTable.getRowCount(); row++) {
            Component renderer = detailTable.prepareRenderer(
                    detailTable.getCellRenderer(row, 1), row, 1);
            renderer.setSize(Math.max(1, valueColumnWidth), Short.MAX_VALUE);
            int height = Math.max(detailTable.getRowHeight(),
                    renderer.getPreferredSize().height + detailTable.getRowMargin());
            if (detailTable.getRowHeight(row) != height) {
                detailTable.setRowHeight(row, height);
            }
        }
    }

    private static final class WrappedTextRenderer extends JTextArea implements TableCellRenderer {
        WrappedTextRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                        boolean focused, int row, int column) {
            setText(value == null ? "" : value.toString());
            boolean hyperlink = column == 1 && row < table.getRowCount()
                    && isLinkDetail(String.valueOf(table.getValueAt(row, 0)));
            setFont(table.getFont());
            setForeground(selected ? table.getSelectionForeground()
                    : hyperlink ? hyperlinkColor(table) : table.getForeground());
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }

        private static Color hyperlinkColor(JTable table) {
            Color background = table.getBackground();
            int brightness = (background.getRed() * 299 + background.getGreen() * 587
                    + background.getBlue() * 114) / 1000;
            return brightness < 128 ? new Color(100, 181, 246) : new Color(0, 80, 180);
        }
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

    private void checkForUpdates() {
        new SwingWorker<Optional<UpdateChecker.Release>, Void>() {
            @Override
            protected Optional<UpdateChecker.Release> doInBackground() throws Exception {
                return new UpdateChecker().checkForUpdate(readVersion());
            }

            @Override
            protected void done() {
                try {
                    Optional<UpdateChecker.Release> update = get();
                    if (update.isPresent()) {
                        UpdateChecker.Release release = update.get();
                        int choice = JOptionPane.showConfirmDialog(frame,
                                "Version " + release.version + " is available. "
                                        + "Would you like to open the GitHub release page?",
                                "Update available", JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            openReleasePage(release.url);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("GitHub update check was interrupted: " + e.getMessage());
                } catch (java.util.concurrent.ExecutionException e) {
                    System.err.println("GitHub update check failed: " + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void openReleasePage(URI releaseUrl) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("This system cannot open web links.");
            }
            Desktop.getDesktop().browse(releaseUrl);
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(frame, "Could not open the GitHub release page: "
                            + e.getMessage(), "Unable to open link", JOptionPane.ERROR_MESSAGE);
        }
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
        ServerEntry favorite = findFavorite(server);
        if (favorite != null
                && Boolean.parseBoolean(favorite.value("autoUpdateDescription", "true"))) {
            String hostname = fields.get("hostname");
            if (hostname != null && !hostname.isBlank()
                    && !hostname.equals(favorite.value("favoriteDescription", ""))) {
                favorite.fields.put("favoriteDescription", hostname);
                persistFavoriteChanges();
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
                model.setValueAt(model == favoriteModel
                                ? server.value("favoriteDescription",
                                        server.value("hostname", "Unnamed server"))
                                : server.value("hostname", "Unnamed server"),
                        row, 0);
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
                .forEach(entry -> detailModel.addRow(new Object[]{
                        entry.getKey(), displayServerDetailValue(entry.getKey(), entry.getValue())}));

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

    private static boolean isLinkDetail(String key) {
        return "adminemail".equalsIgnoreCase(key) || "homepage".equalsIgnoreCase(key);
    }

    private static String displayServerDetailValue(String key, String value) {
        if ("adminemail".equalsIgnoreCase(key) && value.regionMatches(true, 0, "mailto:", 0, 7)) {
            return value.substring(7);
        }
        return value;
    }

    private void openServerDetailLink(String key, String displayedValue) {
        if (!isLinkDetail(key) || displayedValue == null || displayedValue.isBlank()) {
            return;
        }
        try {
            URI target;
            if ("adminemail".equalsIgnoreCase(key)) {
                target = URI.create("mailto:" + displayedValue.trim());
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                    throw new IOException("Opening email links is not supported on this system.");
                }
                Desktop.getDesktop().mail(target);
            } else {
                String homepage = displayedValue.trim();
                if (!homepage.matches("(?i)^https?://.*")) {
                    homepage = "https://" + homepage;
                }
                target = URI.create(homepage);
                if (!"http".equalsIgnoreCase(target.getScheme())
                        && !"https".equalsIgnoreCase(target.getScheme())) {
                    throw new IOException("Only HTTP and HTTPS homepage links are supported.");
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("Opening web links is not supported on this system.");
                }
                Desktop.getDesktop().browse(target);
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(frame, "Could not open the server link: " + e.getMessage(),
                    "Unable to open link", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setBusy(boolean busy, String message) {
        searchButton.setEnabled(!busy);
        searchField.setEnabled(!busy);
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem saveList = new JMenuItem("Save list");
        saveList.addActionListener(event -> saveFavoriteList());
        JMenuItem loadList = new JMenuItem("Load list");
        loadList.addActionListener(event -> loadFavoriteList());
        fileMenu.add(saveList);
        fileMenu.add(loadList);
        menuBar.add(fileMenu);

        JMenu themesMenu = new JMenu("Themes");
        ButtonGroup themeGroup = new ButtonGroup();
        for (Theme theme : Theme.values()) {
            JCheckBoxMenuItem themeItem = new JCheckBoxMenuItem(theme.label);
            themeItem.setSelected(theme == activeTheme);
            themeItem.addActionListener(event -> applyTheme(theme));
            themeGroup.add(themeItem);
            themesMenu.add(themeItem);
            themeMenuItems.put(theme, themeItem);
        }
        menuBar.add(themesMenu);
        return menuBar;
    }

    private void applyTheme(Theme theme) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            restoreDefaultUiValues();
            ColorPalette palette = theme.palette;
            if (palette != null) {
                installPalette(palette);
            }
            SwingUtilities.updateComponentTreeUI(frame);
            restoreDefaultComponentColors(frame);
            if (palette != null) {
                applyPalette(frame, palette);
            }
            activeTheme = theme;
            JCheckBoxMenuItem selectedItem = themeMenuItems.get(theme);
            if (selectedItem != null) {
                selectedItem.setSelected(true);
            }
            frame.invalidate();
            frame.validate();
            frame.repaint();
            COLUMN_PREFERENCES.put("theme", theme.name());
            COLUMN_PREFERENCES.flush();
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException e) {
            JCheckBoxMenuItem activeItem = themeMenuItems.get(activeTheme);
            if (activeItem != null) {
                activeItem.setSelected(true);
            }
            showError("Could not apply the " + theme.label + " theme: " + e.getMessage());
        } catch (BackingStoreException e) {
            showError("Could not save the selected theme: " + e.getMessage());
        }
    }

    private static void installPalette(ColorPalette palette) {
        UIManager.put("Panel.background", palette.surface);
        UIManager.put("Panel.foreground", palette.foreground);
        UIManager.put("Label.foreground", palette.foreground);
        UIManager.put("Button.background", palette.control);
        UIManager.put("Button.foreground", palette.controlForeground);
        UIManager.put("ToggleButton.background", palette.control);
        UIManager.put("ToggleButton.foreground", palette.controlForeground);
        UIManager.put("MenuBar.background", palette.control);
        UIManager.put("MenuBar.foreground", palette.controlForeground);
        UIManager.put("Menu.background", palette.control);
        UIManager.put("Menu.foreground", palette.controlForeground);
        UIManager.put("Menu.selectionBackground", palette.selection);
        UIManager.put("Menu.selectionForeground", palette.selectionForeground);
        UIManager.put("MenuItem.background", palette.control);
        UIManager.put("MenuItem.foreground", palette.controlForeground);
        UIManager.put("MenuItem.selectionBackground", palette.selection);
        UIManager.put("MenuItem.selectionForeground", palette.selectionForeground);
        UIManager.put("TabbedPane.background", palette.control);
        UIManager.put("TabbedPane.foreground", palette.controlForeground);
        UIManager.put("TabbedPane.selected", palette.selectedTab);
        UIManager.put("TabbedPane.contentAreaColor", palette.surface);
        UIManager.put("TabbedPane.tabAreaBackground", palette.control);
        UIManager.put("ScrollBar.background", palette.control);
        UIManager.put("ScrollBar.foreground", palette.controlForeground);
        UIManager.put("ScrollBar.track", palette.control);
        UIManager.put("ScrollBar.trackHighlight", palette.selectedTab);
        UIManager.put("ScrollBar.thumb", palette.border);
        UIManager.put("ScrollBar.thumbDarkShadow", palette.border);
        UIManager.put("ScrollBar.thumbHighlight", palette.control);
        UIManager.put("ScrollBar.thumbShadow", palette.border);
        UIManager.put("Table.background", palette.table);
        UIManager.put("Table.foreground", palette.foreground);
        UIManager.put("Table.selectionBackground", palette.selection);
        UIManager.put("Table.selectionForeground", palette.selectionForeground);
        UIManager.put("Table.gridColor", palette.border);
        UIManager.put("TableHeader.background", palette.control);
        UIManager.put("TableHeader.foreground", palette.controlForeground);
        UIManager.put("TextField.background", palette.input);
        UIManager.put("TextField.foreground", palette.foreground);
        UIManager.put("TextArea.background", palette.input);
        UIManager.put("TextArea.foreground", palette.foreground);
        UIManager.put("TextField.caretForeground", palette.foreground);
        UIManager.put("TextArea.caretForeground", palette.foreground);
        UIManager.put("TextField.selectionBackground", palette.selection);
        UIManager.put("TextArea.selectionBackground", palette.selection);
        UIManager.put("TextField.selectionForeground", palette.selectionForeground);
        UIManager.put("TextArea.selectionForeground", palette.selectionForeground);
    }

    private void captureDefaultComponentColors(Component component) {
        if (component instanceof javax.swing.JComponent) {
            defaultComponentColors.put(component, new ComponentColors(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                captureDefaultComponentColors(child);
            }
        }
    }

    private void restoreDefaultComponentColors(Component component) {
        ComponentColors colors = defaultComponentColors.get(component);
        if (colors != null && component instanceof javax.swing.JComponent) {
            component.setForeground(colors.foreground);
            component.setBackground(colors.background);
            ((javax.swing.JComponent) component).setBorder(colors.border);
            if (component instanceof JTable) {
                JTable table = (JTable) component;
                table.setSelectionBackground(colors.selectionBackground);
                table.setSelectionForeground(colors.selectionForeground);
                table.setGridColor(colors.gridColor);
            }
            if (component instanceof JTabbedPane) {
                JTabbedPane tabs = (JTabbedPane) component;
                for (int index = 0; index < tabs.getTabCount(); index++) {
                    tabs.setBackgroundAt(index, colors.tabBackgrounds[index]);
                    tabs.setForegroundAt(index, colors.tabForegrounds[index]);
                }
            }
            if (component instanceof JTextComponent) {
                JTextComponent textComponent = (JTextComponent) component;
                textComponent.setCaretColor(colors.caretColor);
                textComponent.setSelectionColor(colors.selectionColor);
                textComponent.setSelectedTextColor(colors.selectedTextColor);
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                restoreDefaultComponentColors(child);
            }
        }
    }

    private static void applyPalette(Component component, ColorPalette palette) {
        if (component instanceof javax.swing.JComponent) {
            component.setForeground(palette.foreground);
        }
        if (component instanceof JPanel || component instanceof JViewport
                || component instanceof JScrollPane) {
            component.setBackground(palette.surface);
        }
        if (component instanceof JMenuBar || component instanceof JMenu
                || component instanceof JMenuItem) {
            component.setBackground(palette.control);
            component.setForeground(palette.controlForeground);
        }
        if (component instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) component;
            tabs.setBackground(palette.control);
            tabs.setForeground(palette.controlForeground);
            for (int index = 0; index < tabs.getTabCount(); index++) {
                tabs.setBackgroundAt(index, index == tabs.getSelectedIndex()
                        ? palette.selectedTab : palette.control);
                tabs.setForegroundAt(index, palette.controlForeground);
            }
        }
        if (component instanceof AbstractButton) {
            component.setBackground(palette.control);
            component.setForeground(palette.controlForeground);
        }
        if (component instanceof JTable) {
            JTable table = (JTable) component;
            table.setBackground(palette.table);
            table.setForeground(palette.foreground);
            table.setSelectionBackground(palette.selection);
            table.setSelectionForeground(palette.selectionForeground);
            table.setGridColor(palette.border);
        }
        if (component instanceof JTableHeader) {
            component.setBackground(palette.control);
            component.setForeground(palette.controlForeground);
        }
        if (component instanceof JTextComponent) {
            JTextComponent textComponent = (JTextComponent) component;
            textComponent.setBackground(palette.input);
            textComponent.setForeground(palette.foreground);
            textComponent.setCaretColor(palette.foreground);
            textComponent.setSelectionColor(palette.selection);
            textComponent.setSelectedTextColor(palette.selectionForeground);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyPalette(child, palette);
            }
        }
    }

    private static Theme savedTheme() {
        String saved = COLUMN_PREFERENCES.get("theme", Theme.DEFAULT.name());
        try {
            return Theme.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return Theme.DEFAULT;
        }
    }

    private enum Theme {
        DEFAULT("Default", null),
        DARK("Dark", DARK_PALETTE);

        private final String label;
        private final ColorPalette palette;

        Theme(String label, ColorPalette palette) {
            this.label = label;
            this.palette = palette;
        }
    }

    private static final class ColorPalette {
        private final Color surface;
        private final Color input;
        private final Color foreground;
        private final Color control;
        private final Color selection;
        private final Color selectionForeground;
        private final Color table;
        private final Color selectedTab;
        private final Color border;
        private final Color controlForeground;

        ColorPalette(Color surface, Color input, Color foreground, Color control,
                     Color selection, Color selectionForeground, Color table,
                     Color selectedTab, Color border, Color controlForeground) {
            this.surface = surface;
            this.input = input;
            this.foreground = foreground;
            this.control = control;
            this.selection = selection;
            this.selectionForeground = selectionForeground;
            this.table = table;
            this.selectedTab = selectedTab;
            this.border = border;
            this.controlForeground = controlForeground;
        }
    }

    private static final class ComponentColors {
        private final Color foreground;
        private final Color background;
        private final Color selectionBackground;
        private final Color selectionForeground;
        private final Color gridColor;
        private final Color caretColor;
        private final Color selectionColor;
        private final Color selectedTextColor;
        private final Color[] tabBackgrounds;
        private final Color[] tabForegrounds;
        private final Border border;

        ComponentColors(Component component) {
            foreground = component.getForeground();
            background = component.getBackground();
            border = component instanceof javax.swing.JComponent
                    ? ((javax.swing.JComponent) component).getBorder() : null;
            if (component instanceof JTable) {
                JTable table = (JTable) component;
                selectionBackground = table.getSelectionBackground();
                selectionForeground = table.getSelectionForeground();
                gridColor = table.getGridColor();
            } else {
                selectionBackground = null;
                selectionForeground = null;
                gridColor = null;
            }
            if (component instanceof JTextComponent) {
                JTextComponent textComponent = (JTextComponent) component;
                caretColor = textComponent.getCaretColor();
                selectionColor = textComponent.getSelectionColor();
                selectedTextColor = textComponent.getSelectedTextColor();
            } else {
                caretColor = null;
                selectionColor = null;
                selectedTextColor = null;
            }
            if (component instanceof JTabbedPane) {
                JTabbedPane tabs = (JTabbedPane) component;
                tabBackgrounds = new Color[tabs.getTabCount()];
                tabForegrounds = new Color[tabs.getTabCount()];
                for (int index = 0; index < tabs.getTabCount(); index++) {
                    tabBackgrounds[index] = tabs.getBackgroundAt(index);
                    tabForegrounds[index] = tabs.getForegroundAt(index);
                }
            } else {
                tabBackgrounds = new Color[0];
                tabForegrounds = new Color[0];
            }
        }
    }

    private void saveFavoriteList() {
        JFileChooser chooser = favoriteListChooser();
        chooser.setDialogTitle("Save favorite list");
        chooser.setSelectedFile(new java.io.File("favorites.lst"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedFile = chooser.getSelectedFile().toPath();
        if (!selectedFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".lst")) {
            selectedFile = selectedFile.resolveSibling(selectedFile.getFileName() + ".lst");
        }
        try {
            new FavoriteStore(selectedFile).save(favorites);
            statusLabel.setText("Favorite list saved to " + selectedFile);
        } catch (IOException e) {
            showError("Could not save favorite list: " + e.getMessage());
        }
    }

    private void loadFavoriteList() {
        JFileChooser chooser = favoriteListChooser();
        chooser.setDialogTitle("Load favorite list");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        int choice = JOptionPane.showOptionDialog(frame,
                "How would you like to load this favorite list?",
                "Load favorite list",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Replace current list", "Merge with current list", "Cancel"},
                "Merge with current list");
        if (choice != 0 && choice != 1) {
            return;
        }

        try {
            List<ServerEntry> imported = new FavoriteStore(chooser.getSelectedFile().toPath()).load();
            List<ServerEntry> previousFavorites = new ArrayList<>(favorites);
            favorites.clear();
            favorites.addAll(FavoriteStore.combineFavorites(
                    previousFavorites, imported, choice == 1));
            if (saveFavorites()) {
                statusLabel.setText(favorites.size() + " favorite server(s) loaded");
            } else {
                favorites.clear();
                favorites.addAll(previousFavorites);
                displayServersInModel(favorites, favoriteModel);
            }
        } catch (IOException e) {
            showError("Could not load favorite list: " + e.getMessage());
        }
    }

    private JFileChooser favoriteListChooser() {
        JFileChooser chooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                if (getDialogType() == SAVE_DIALOG) {
                    java.io.File selectedFile = getSelectedFile();
                    if (selectedFile != null
                            && !selectedFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lst")) {
                        selectedFile = new java.io.File(selectedFile.getParentFile(),
                                selectedFile.getName() + ".lst");
                        setSelectedFile(selectedFile);
                    }
                    if (selectedFile != null && selectedFile.exists()
                            && JOptionPane.showConfirmDialog(this,
                            "Replace the existing file \"" + selectedFile.getName() + "\"?",
                            "Confirm save", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                super.approveSelection();
            }
        };
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Favorite lists (*.lst)", "lst"));
        return chooser;
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
                    if (favoritesView) {
                        javax.swing.JMenuItem edit = new javax.swing.JMenuItem("Edit favorite");
                        edit.addActionListener(action -> editFavorite(server));
                        menu.add(edit);
                    }
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
        Map<String, Object> favoriteFields = new LinkedHashMap<>(server.fields);
        favoriteFields.put("favoriteDescription", server.value("hostname", "Unnamed server"));
        favoriteFields.put("autoUpdateDescription", "true");
        ServerEntry favorite = new ServerEntry(favoriteFields);
        favorites.add(favorite);
        if (!saveFavorites()) {
            favorites.remove(favorite);
            displayServersInModel(favorites, favoriteModel);
        }
    }

    private void addFavoriteManually() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ip", "");
        fields.put("hostport", "");
        fields.put("queryport", "");
        fields.put("hostname", "");
        fields.put("favoriteDescription", "");
        fields.put("autoUpdateDescription", "true");
        editFavorite(new ServerEntry(fields), true);
    }

    private void editFavorite(ServerEntry server) {
        editFavorite(server, false);
    }

    private void editFavorite(ServerEntry server, boolean addingFavorite) {
        JTextField descriptionField = new JTextField(
                server.value("favoriteDescription", server.value("hostname", "")), 34);
        JCheckBox autoUpdateDescription = new JCheckBox("Auto-Update Description",
                Boolean.parseBoolean(server.value("autoUpdateDescription", "true")));
        JTextField ipField = new JTextField(server.value("ip", ""), 18);
        JTextField gamePortField = new JTextField(server.value("hostport", ""), 7);
        JTextField queryPortField = new JTextField(server.value("queryport",
                defaultQueryPort(server)), 7);

        JPanel fields = new JPanel(new GridBagLayout());
        addFavoriteEditorRow(fields, 0, "Description", descriptionField);
        GridBagConstraints checkboxConstraints = new GridBagConstraints();
        checkboxConstraints.gridx = 0;
        checkboxConstraints.gridy = 1;
        checkboxConstraints.gridwidth = 2;
        checkboxConstraints.anchor = GridBagConstraints.WEST;
        checkboxConstraints.insets = new Insets(4, 4, 4, 4);
        fields.add(autoUpdateDescription, checkboxConstraints);
        addFavoriteEditorRow(fields, 2, "Server IP Address", ipField);
        addFavoriteEditorRow(fields, 3, "Server Port Number", gamePortField);
        addFavoriteEditorRow(fields, 4, "Query Port Number", queryPortField);

        JButton okButton = new JButton("OK");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(okButton);
        buttons.add(closeButton);

        JDialog dialog = new JDialog(frame, "Edit Favorite", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(6, 8));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(90, 120, 155)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        okButton.addActionListener(event -> {
            String ip = ipField.getText().trim();
            String gamePort = gamePortField.getText().trim();
            String queryPort = queryPortField.getText().trim();
            String validationError = validateFavoriteAddress(ip, gamePort, queryPort);
            if (validationError != null) {
                JOptionPane.showMessageDialog(dialog, validationError,
                        "Invalid favorite server", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (addingFavorite && containsFavoriteAddress(ip, gamePort)) {
                JOptionPane.showMessageDialog(dialog,
                        "This server is already in Favorites.",
                        "Favorite already exists", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String oldAddress = server.address();
            boolean wasSelected = selectedServer == server;
            Map<String, Object> previousFields = new LinkedHashMap<>(server.fields);
            server.fields.put("favoriteDescription", descriptionField.getText().trim());
            server.fields.put("autoUpdateDescription",
                    Boolean.toString(autoUpdateDescription.isSelected()));
            server.fields.put("ip", ip);
            server.fields.put("hostport", gamePort);
            server.fields.put("queryport", queryPort);
            if (addingFavorite) {
                favorites.add(server);
            }
            if (saveFavorites()) {
                dialog.dispose();
                Long previousRequestId = pingRequestIds.get(oldAddress);
                if (previousRequestId != null) {
                    pingRequestIds.put(oldAddress, previousRequestId + 1);
                }
                if (wasSelected) {
                    int modelRow = favorites.indexOf(server);
                    int viewRow = favoriteTable.convertRowIndexToView(modelRow);
                    if (viewRow >= 0) {
                        favoriteTable.setRowSelectionInterval(viewRow, viewRow);
                    }
                }
            } else {
                if (addingFavorite) {
                    favorites.remove(server);
                }
                server.fields.clear();
                server.fields.putAll(previousFields);
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(okButton);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void addFavoriteEditorRow(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(4, 4, 4, 10);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 4, 4, 4);
        panel.add(field, fieldConstraints);
    }

    private static String defaultQueryPort(ServerEntry server) {
        try {
            return Integer.toString(Integer.parseInt(server.value("hostport", "")) + 1);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String validateFavoriteAddress(String ip, String gamePort, String queryPort) {
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            return "Enter a valid IPv4 address.";
        }
        for (String octet : octets) {
            if (!octet.matches("\\d{1,3}") || Integer.parseInt(octet) > 255) {
                return "Enter a valid IPv4 address.";
            }
        }
        if (!isValidPort(gamePort)) {
            return "Game port must be a number between 1 and 65535.";
        }
        if (!isValidPort(queryPort)) {
            return "Query port must be a number between 1 and 65535.";
        }
        return null;
    }

    private static boolean isValidPort(String value) {
        if (!value.matches("\\d{1,5}")) {
            return false;
        }
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65535;
    }

    private boolean containsFavoriteAddress(String ip, String gamePort) {
        String address = ip + ":" + gamePort;
        for (ServerEntry favorite : favorites) {
            if (favorite.address().equalsIgnoreCase(address)) {
                return true;
            }
        }
        return false;
    }

    private ServerEntry findFavorite(ServerEntry server) {
        for (ServerEntry favorite : favorites) {
            if (favorite.address().equalsIgnoreCase(server.address())) {
                return favorite;
            }
        }
        return null;
    }

    private void persistFavoriteChanges() {
        try {
            favoriteStore.save(favorites);
            displayServersInModel(favorites, favoriteModel);
        } catch (IOException e) {
            showError("Could not save favorites: " + e.getMessage());
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
                    model == favoriteModel
                            ? server.value("favoriteDescription", server.value("hostname", "Unnamed server"))
                            : server.value("hostname", "Unnamed server"),
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
