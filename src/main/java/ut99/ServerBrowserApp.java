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
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ServerBrowserApp {
    private static final int MAX_SERVER_RESULTS = 1000;
    private static final Path GAME_LOGO_PATH = Path.of("src", "img", "Giblogo-mirror.gif");
    private static final Path DISCORD_ICON_PATH = Path.of("src", "img", "discord.png");
    private static final URI DISCORD_INVITE = URI.create("https://discord.com/invite/5AG6Zj96PG");

    private final ApiClient apiClient = new ApiClient();
    private final JFrame frame = new JFrame("Unreal Tournament 99 Server Browser");
    private final JTextField searchField = new JTextField(28);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel detailHeading = new JLabel("Select a server to view its players");
    private final FavoriteStore favoriteStore = new FavoriteStore(
            Path.of(System.getProperty("user.home"), ".ut99-server-browser", "favorites.properties"));
    private final List<ServerEntry> currentServers = new ArrayList<>();
    private final List<ServerEntry> favorites = new ArrayList<>();
    private final DefaultTableModel serverModel = new DefaultTableModel(
            new Object[]{"Server", "Players", "Map", "Game type", "Country", "Address"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable serverTable = new JTable(serverModel);
    private final DefaultTableModel favoriteModel = new DefaultTableModel(
            new Object[]{"Server", "Players", "Map", "Game type", "Country", "Address"}, 0) {
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
            new Object[]{"Player", "Team", "Score (frags)", "Ping", "Mesh", "Skin", "Face", "Last update", "Misc"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable playerTable = new JTable(playerModel);
    private final JButton searchButton = new JButton("Search");
    private String searchQuery = "";
    private int totalServers;
    private ServerEntry selectedServer;
    private long detailRequestId;

    private ServerBrowserApp() {
        buildWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                showStartupError(e);
            }
            new ServerBrowserApp().show();
        });
    }

    private static void showStartupError(Exception e) {
        JOptionPane.showMessageDialog(null, "Unable to set the system look and feel: "
                        + e.getMessage(), "Startup warning", JOptionPane.WARNING_MESSAGE);
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 680));
        frame.setSize(1280, 820);
        frame.setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        root.add(buildToolbar(), BorderLayout.NORTH);

        serverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverTable.setAutoCreateRowSorter(true);
        serverTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectServer(serverTable, currentServers);
            }
        });
        favoriteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        favoriteTable.setAutoCreateRowSorter(true);
        favoriteTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectServer(favoriteTable, favorites);
            }
        });
        detailTable.setAutoCreateRowSorter(true);
        playerTable.setAutoCreateRowSorter(true);
        playerTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
            JTable activeTable = serverTabs.getSelectedIndex() == 1 ? favoriteTable : serverTable;
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
        detailContent.add(wrapTable("Server details", detailTable));
        detailContent.add(wrapTable("Players and scores (frags)", playerTable));
        detailPanel.add(detailContent, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, serverTabs, detailPanel);
        splitPane.setResizeWeight(0.57);
        root.add(splitPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(statusLabel, BorderLayout.CENTER);
        JLabel attribution = new JLabel("Server data provided by 333networks", SwingConstants.RIGHT);
        footer.add(attribution, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(root);
        searchButton.addActionListener(this::search);
        searchField.addActionListener(this::search);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchControls.add(new JLabel("Search servers:"));
        searchControls.add(searchField);
        searchControls.add(searchButton);
        searchControls.add(new JLabel("(name, map, game type, or any API search term)"));
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

    private void show() {
        frame.setVisible(true);
        loadFavorites();
        loadServers();
    }

    private void search(ActionEvent event) {
        searchQuery = searchField.getText().trim();
        if (searchQuery.length() > 90) {
            showError("Search terms must be 90 characters or fewer.");
            return;
        }
        loadServers();
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
                    displayServers(result.servers);
                    statusLabel.setText(String.format("Showing %,d of %,d servers | %,d players",
                            result.servers.size(), totalServers, result.players));
                } catch (Exception e) {
                    showError(messageFor(e));
                } finally {
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
                    server.address()
            });
        }
        clearDetails();
    }

    private void selectServer(JTable table, List<ServerEntry> source) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            clearSelectedServer();
            return;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        selectedServer = source.get(modelRow);
        detailHeading.setText(selectedServer.value("hostname", "Unnamed server"));
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
        for (Map.Entry<String, Object> entry : selectedServer.fields.entrySet()) {
            if (!entry.getKey().startsWith("player_")) {
                detailModel.addRow(new Object[]{entry.getKey(), displayValue(entry.getValue())});
            }
        }
        loadDetails(selectedServer);
    }

    private void clearSelectedServer() {
        selectedServer = null;
        detailRequestId++;
        detailHeading.setText("Select a server to view its players");
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
    }

    private void clearDetails() {
        clearSelectedServer();
    }

    private void loadDetails(ServerEntry server) {
        long requestId = ++detailRequestId;
        statusLabel.setText("Loading player details for " + server.value("hostname", server.address()) + "...");
        detailHeading.setText(server.value("hostname", "Unnamed server") + " — loading details...");
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return apiClient.fetchDetails(server);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> details = get();
                    if (requestId == detailRequestId && selectedServer == server) {
                        displayDetails(details);
                        statusLabel.setText(playerModel.getRowCount() == 0
                                ? "No player data reported for " + server.address()
                                : "Loaded " + playerModel.getRowCount() + " player(s) for " + server.address());
                    }
                } catch (Exception e) {
                    if (requestId == detailRequestId && selectedServer == server) {
                        statusLabel.setText("Could not load player details");
                        JOptionPane.showMessageDialog(frame, messageFor(e), "333networks API error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void displayDetails(Map<String, Object> details) {
        detailModel.setRowCount(0);
        playerModel.setRowCount(0);
        detailHeading.setText(String.valueOf(details.getOrDefault("hostname", "Server details")));
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry.getKey().startsWith("player_") && entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> player = (Map<String, Object>) entry.getValue();
                playerModel.addRow(new Object[]{
                        displayValue(player.get("name")),
                        displayValue(player.get("team")),
                        displayValue(player.get("frags")),
                        displayValue(player.get("mesh")),
                        displayValue(player.get("skin")),
                        displayValue(player.get("face")),
                        displayValue(player.get("ping")),
                        displayValue(player.get("dt_player")),
                        displayValue(player.get("misc"))
                });
            } else {
                detailModel.addRow(new Object[]{entry.getKey(), displayValue(entry.getValue())});
            }
        }
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
                    server.address()
            });
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
