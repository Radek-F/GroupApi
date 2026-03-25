import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Roblox Group Manager — Whitelist Edition with AutoKick
 *
 * Build & run:
 *   javac RobloxGroupManager.java
 *   java RobloxGroupManager
 *
 * Requires: Java 11+  (no extra libraries)
 *
 * HOW IT WORKS:
 *   - Enter your main group ID
 *   - Enter one or more "required" subgroup IDs
 *   - A member is SAFE  if they are in AT LEAST ONE subgroup
 *   - A member is BAD   if they are in NONE of the subgroups -> queued for kick
 *   - Paste your .ROBLOSECURITY cookie to enable the AutoKick button
 */
public class GroupManager extends JFrame {

    // Palette
    static final Color BG       = new Color(0x0D0D12);
    static final Color SURF     = new Color(0x17171F);
    static final Color SURF2    = new Color(0x1F1F2B);
    static final Color SURF3    = new Color(0x26263A);
    static final Color BORDER   = new Color(0x2C2C40);
    static final Color ACCENT   = new Color(0xFF4655);
    static final Color GREEN    = new Color(0x00C9A7);
    static final Color YELLOW   = new Color(0xFFBB44);
    static final Color TEXT     = new Color(0xECECF4);
    static final Color DIM      = new Color(0x6E6E8A);
    static final Color FLAG_ROW = new Color(0x2A1018);
    static final Color SAFE_ROW = new Color(0x0A2520);

    // App state
    final List<Long>         subgroupIds   = new ArrayList<>();
    final List<String>       subgroupNames = new ArrayList<>();
    final List<MemberResult> results       = new ArrayList<>();
    volatile boolean scanning = false;

    // UI
    JTextField    mainGroupField, addSubgroupField;
    JPasswordField cookieField;
    JButton       addSubgroupBtn, removeSubgroupBtn;
    JButton       scanBtn, kickQueueBtn, exportBtn, clearBtn;
    DefaultListModel<String> subgroupModel;
    JList<String> subgroupList;
    JTable        table;
    ResultTableModel tableModel;
    JLabel        statusLabel, statsLabel, cookieStatusLabel;
    JProgressBar  progressBar;

    // Extra conditions
    JCheckBox condAgeEnabled, condFriendsEnabled, condOnlineEnabled;
    JSpinner  condAgeDays, condFriendsMin, condOnlineDays;

    public GroupManager() {
        super("Roblox Group Manager");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1350, 740);
        setMinimumSize(new Dimension(1100, 620));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setVisible(true);
    }

    Component buildHeader() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(0x1C0A12),getWidth(),0,SURF));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(ACCENT);
                g2.fillRect(0,getHeight()-2,getWidth(),2);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(16,24,16,24));

        JLabel title = lbl("ROBLOX GROUP MANAGER", new Font("Monospaced",Font.BOLD,19), TEXT);
        JLabel sub   = lbl("Whitelist scanner + AutoKick", new Font("SansSerif",Font.PLAIN,12), DIM);
        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left,BoxLayout.Y_AXIS)); left.setOpaque(false);
        left.add(title); left.add(Box.createVerticalStrut(3)); left.add(sub);
        p.add(left, BorderLayout.WEST);

        statsLabel = lbl("No scan yet", new Font("Monospaced",Font.PLAIN,12), DIM);
        statsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        p.add(statsLabel, BorderLayout.EAST);
        return p;
    }

    Component buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeft(), buildRight());
        split.setDividerLocation(310);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(BG);
        return split;
    }

    Component buildLeft() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16,16,16,12));

        p.add(secLabel("MAIN GROUP"));
        p.add(vgap(6));
        mainGroupField = textField("Group ID  (e.g. 123456)");
        p.add(mainGroupField);

        p.add(vgap(20));
        p.add(secLabel("REQUIRED SUBGROUPS"));
        p.add(vgap(4));
        JLabel hint = lbl("Members must be in >=1 of these to be SAFE", new Font("SansSerif",Font.ITALIC,11), DIM);
        hint.setAlignmentX(0); p.add(hint);
        p.add(vgap(8));

        JPanel addRow = new JPanel(new BorderLayout(6,0));
        addRow.setOpaque(false); addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        addSubgroupField = textField("Subgroup ID");
        addSubgroupBtn   = accentBtn("ADD");
        addSubgroupBtn.setPreferredSize(new Dimension(58,34));
        addRow.add(addSubgroupField, BorderLayout.CENTER);
        addRow.add(addSubgroupBtn,   BorderLayout.EAST);
        p.add(addRow); p.add(vgap(6));

        subgroupModel = new DefaultListModel<>();
        subgroupList  = new JList<>(subgroupModel);
        subgroupList.setBackground(SURF2); subgroupList.setForeground(TEXT);
        subgroupList.setFont(new Font("Monospaced",Font.PLAIN,12));
        subgroupList.setBorder(new EmptyBorder(4,8,4,8));
        subgroupList.setSelectionBackground(SURF3); subgroupList.setSelectionForeground(TEXT);
        JScrollPane ls = styledScroll(subgroupList);
        ls.setMaximumSize(new Dimension(Integer.MAX_VALUE,130));
        ls.setPreferredSize(new Dimension(0,130));
        p.add(ls); p.add(vgap(6));

        removeSubgroupBtn = dimBtn("REMOVE SELECTED"); p.add(removeSubgroupBtn);

        p.add(vgap(24));
        p.add(secLabel("EXTRA CONDITIONS"));
        p.add(vgap(4));
        JLabel condHint = lbl("Flag members even if in a subgroup, if they fail these", new Font("SansSerif",Font.ITALIC,11), DIM);
        condHint.setAlignmentX(0); p.add(condHint);
        p.add(vgap(10));

        // Account age row
        condAgeEnabled = new JCheckBox("Account must be older than");
        condAgeEnabled.setBackground(BG); condAgeEnabled.setForeground(TEXT);
        condAgeEnabled.setFont(new Font("SansSerif",Font.PLAIN,12));
        condAgeEnabled.setFocusPainted(false); condAgeEnabled.setAlignmentX(0);
        condAgeDays = new JSpinner(new SpinnerNumberModel(30, 1, 9999, 1));
        condAgeDays.setMaximumSize(new Dimension(75, 28));
        styleSpinner(condAgeDays);
        JPanel ageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ageRow.setOpaque(false); ageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        ageRow.setAlignmentX(0);
        ageRow.add(condAgeEnabled); ageRow.add(condAgeDays);
        ageRow.add(lbl("  days", new Font("SansSerif",Font.PLAIN,12), DIM));
        p.add(ageRow);
        p.add(vgap(6));

        // Friend count row
        condFriendsEnabled = new JCheckBox("Must have at least");
        condFriendsEnabled.setBackground(BG); condFriendsEnabled.setForeground(TEXT);
        condFriendsEnabled.setFont(new Font("SansSerif",Font.PLAIN,12));
        condFriendsEnabled.setFocusPainted(false); condFriendsEnabled.setAlignmentX(0);
        condFriendsMin = new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));
        condFriendsMin.setMaximumSize(new Dimension(75, 28));
        styleSpinner(condFriendsMin);
        JPanel friendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        friendRow.setOpaque(false); friendRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        friendRow.setAlignmentX(0);
        friendRow.add(condFriendsEnabled); friendRow.add(condFriendsMin);
        friendRow.add(lbl("  friends", new Font("SansSerif",Font.PLAIN,12), DIM));
        p.add(friendRow);
        p.add(vgap(6));

        // Last online row
        condOnlineEnabled = new JCheckBox("Must have been online within");
        condOnlineEnabled.setBackground(BG); condOnlineEnabled.setForeground(TEXT);
        condOnlineEnabled.setFont(new Font("SansSerif",Font.PLAIN,12));
        condOnlineEnabled.setFocusPainted(false); condOnlineEnabled.setAlignmentX(0);
        condOnlineDays = new JSpinner(new SpinnerNumberModel(30, 1, 9999, 1));
        condOnlineDays.setMaximumSize(new Dimension(75, 28));
        styleSpinner(condOnlineDays);
        JPanel onlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        onlineRow.setOpaque(false); onlineRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        onlineRow.setAlignmentX(0);
        onlineRow.add(condOnlineEnabled); onlineRow.add(condOnlineDays);
        onlineRow.add(lbl("  days", new Font("SansSerif",Font.PLAIN,12), DIM));
        p.add(onlineRow);

        p.add(vgap(24));
        p.add(secLabel("AUTHENTICATION  (for AutoKick)"));
        p.add(vgap(4));
        JLabel ck = lbl(".ROBLOSECURITY cookie — keep this private!", new Font("SansSerif",Font.ITALIC,11), DIM);
        ck.setAlignmentX(0); p.add(ck); p.add(vgap(8));

        cookieField = new JPasswordField();
        cookieField.setBackground(SURF2); cookieField.setForeground(TEXT);
        cookieField.setCaretColor(ACCENT); cookieField.setFont(new Font("Monospaced",Font.PLAIN,12));
        cookieField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,1,1,1,BORDER), new EmptyBorder(6,10,6,10)));
        cookieField.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        cookieField.setAlignmentX(0);
        p.add(cookieField); p.add(vgap(4));

        cookieStatusLabel = lbl("No cookie — AutoKick disabled", new Font("Monospaced",Font.PLAIN,10), DIM);
        cookieStatusLabel.setAlignmentX(0); p.add(cookieStatusLabel);

        p.add(vgap(24));
        p.add(secLabel("ACTIONS")); p.add(vgap(8));

        scanBtn = new JButton("  SCAN GROUP");
        scanBtn.setFont(new Font("Monospaced",Font.BOLD,13));
        scanBtn.setBackground(ACCENT); scanBtn.setForeground(Color.WHITE);
        scanBtn.setBorder(new EmptyBorder(10,0,10,0)); scanBtn.setFocusPainted(false);
        scanBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        scanBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE,42)); scanBtn.setAlignmentX(0);
        p.add(scanBtn); p.add(vgap(8));

        kickQueueBtn = new JButton("  KICK QUEUED MEMBERS");
        kickQueueBtn.setFont(new Font("Monospaced",Font.BOLD,12));
        kickQueueBtn.setBackground(new Color(0x8B1A2A)); kickQueueBtn.setForeground(Color.WHITE);
        kickQueueBtn.setBorder(new EmptyBorder(9,0,9,0)); kickQueueBtn.setFocusPainted(false);
        kickQueueBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        kickQueueBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE,40)); kickQueueBtn.setAlignmentX(0);
        kickQueueBtn.setEnabled(false);
        p.add(kickQueueBtn); p.add(vgap(8));

        exportBtn = dimBtn("  EXPORT REPORT (.txt)");
        clearBtn  = dimBtn("  CLEAR RESULTS");
        exportBtn.setEnabled(false);
        p.add(exportBtn); p.add(vgap(6)); p.add(clearBtn);

        // Wire
        addSubgroupBtn.addActionListener(e -> addSubgroup());
        addSubgroupField.addActionListener(e -> addSubgroup());
        removeSubgroupBtn.addActionListener(e -> removeSubgroup());
        scanBtn.addActionListener(e -> { if (scanning) stopScan(); else startScan(); });
        kickQueueBtn.addActionListener(e -> kickQueued());
        exportBtn.addActionListener(e -> exportReport());
        clearBtn.addActionListener(e -> clearResults());

        cookieField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void upd() {
                boolean ok = new String(cookieField.getPassword()).trim().length() > 0;
                cookieStatusLabel.setText(ok ? "Cookie set — AutoKick enabled" : "No cookie — AutoKick disabled");
                cookieStatusLabel.setForeground(ok ? GREEN : DIM);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { upd(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { upd(); }
        });

        return p;
    }

    Component buildRight() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG); p.setBorder(new EmptyBorder(16,12,16,16));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        bar.setOpaque(false); bar.setBorder(new EmptyBorder(0,0,10,0));
        bar.add(lbl("SHOW:", new Font("Monospaced",Font.PLAIN,11), DIM));
        JToggleButton tAll     = filterToggle("ALL",              true);
        JToggleButton tFlagged = filterToggle("NOT IN SUBGROUPS", false);
        JToggleButton tSafe    = filterToggle("SAFE",             false);
        ButtonGroup bg = new ButtonGroup(); bg.add(tAll); bg.add(tFlagged); bg.add(tSafe);
        bar.add(tAll); bar.add(tFlagged); bar.add(tSafe);
        bar.add(lbl("  Right-click rows to manage queue", new Font("SansSerif",Font.ITALIC,11), DIM));
        tAll.addActionListener(e     -> tableModel.setFilter(ResultTableModel.Filter.ALL));
        tFlagged.addActionListener(e -> tableModel.setFilter(ResultTableModel.Filter.FLAGGED));
        tSafe.addActionListener(e    -> tableModel.setFilter(ResultTableModel.Filter.SAFE));
        p.add(bar, BorderLayout.NORTH);

        tableModel = new ResultTableModel();
        table = new JTable(tableModel);
        table.setBackground(SURF); table.setForeground(TEXT);
        table.setFont(new Font("SansSerif",Font.PLAIN,13)); table.setRowHeight(30);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0,1));
        table.setSelectionBackground(SURF3); table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true); table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        int[] widths = {150, 40, 110, 160, 72, 62, 85, 72, 68};
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class, new RowRenderer());

        JTableHeader header = table.getTableHeader();
        header.setBackground(SURF2); header.setForeground(DIM);
        header.setFont(new Font("Monospaced",Font.BOLD,11));
        header.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER));

        p.add(styledScroll(table), BorderLayout.CENTER);

        JPopupMenu popup = new JPopupMenu(); popup.setBackground(SURF2);
        JMenuItem openProfile  = menuItem("Open Roblox Profile");
        JMenuItem addToQueue   = menuItem("Add to Kick Queue");
        JMenuItem rmFromQueue  = menuItem("Remove from Kick Queue");
        popup.add(openProfile); popup.addSeparator(); popup.add(addToQueue); popup.add(rmFromQueue);
        openProfile.addActionListener(e  -> openProfile());
        addToQueue.addActionListener(e   -> setQueueSelected(true));
        rmFromQueue.addActionListener(e  -> setQueueSelected(false));
        table.setComponentPopupMenu(popup);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row,row);
            }
        });

        return p;
    }

    Component buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout(12,0));
        p.setBackground(SURF2); p.setBorder(new EmptyBorder(6,16,6,16));
        statusLabel = lbl("Ready — configure groups and click Scan.", new Font("Monospaced",Font.PLAIN,11), DIM);
        progressBar = new JProgressBar();
        progressBar.setBackground(SURF); progressBar.setForeground(ACCENT);
        progressBar.setBorderPainted(false); progressBar.setPreferredSize(new Dimension(220,8));
        progressBar.setVisible(false);
        p.add(statusLabel, BorderLayout.CENTER);
        p.add(progressBar, BorderLayout.EAST);
        return p;
    }

    // Actions

    void addSubgroup() {
        String text = addSubgroupField.getText().trim();
        if (text.isEmpty()) return;
        long gid;
        try { gid = Long.parseLong(text); } catch (NumberFormatException e) { err("Invalid ID: "+text); return; }
        if (subgroupIds.contains(gid)) { err("Already in list."); return; }
        setStatus("Fetching group info for "+gid+"...");
        new SwingWorker<String,Void>() {
            protected String doInBackground() { return RobloxApi.getGroupName(gid); }
            protected void done() {
                try {
                    String name = get();
                    if (name==null||name.isEmpty()) { err("Group not found: "+gid); return; }
                    subgroupIds.add(gid); subgroupNames.add(name);
                    subgroupModel.addElement(name+"  ["+gid+"]");
                    addSubgroupField.setText(""); setStatus("Added: "+name);
                } catch (Exception ex) { err("Error: "+ex.getMessage()); }
            }
        }.execute();
    }

    void removeSubgroup() {
        int idx = subgroupList.getSelectedIndex(); if (idx<0) return;
        subgroupIds.remove(idx); subgroupNames.remove(idx); subgroupModel.remove(idx);
    }

    void startScan() {
        String mainText = mainGroupField.getText().trim();
        if (mainText.isEmpty()) { err("Enter a main group ID."); return; }
        long mainId;
        try { mainId = Long.parseLong(mainText); } catch (NumberFormatException e) { err("Invalid main group ID."); return; }
        if (subgroupIds.isEmpty()) { err("Add at least one required subgroup."); return; }

        results.clear(); tableModel.refresh(results);
        exportBtn.setEnabled(false); kickQueueBtn.setEnabled(false);
        scanning = true; scanBtn.setText("  STOP"); scanBtn.setBackground(new Color(0x555566));
        progressBar.setVisible(true); progressBar.setIndeterminate(true);

        List<Long>   ids   = new ArrayList<>(subgroupIds);
        List<String> names = new ArrayList<>(subgroupNames);

        new SwingWorker<Void,MemberResult>() {
            int total=0, done=0;
            protected Void doInBackground() throws Exception {
                setStatus("Fetching member list...");
                List<RobloxApi.Member> members = RobloxApi.getGroupMembers(mainId, () -> scanning);
                total = members.size();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMaximum(Math.max(1,total));
                    progressBar.setValue(0);
                    setStatus("Scanning "+total+" members...");
                });
                // Snapshot condition settings on the EDT before background work
                final boolean checkAge     = condAgeEnabled.isSelected();
                final int     minAgeDays   = (int) condAgeDays.getValue();
                final boolean checkFriends = condFriendsEnabled.isSelected();
                final int     minFriends   = (int) condFriendsMin.getValue();
                final boolean checkOnline  = condOnlineEnabled.isSelected();
                final int     maxOffDays   = (int) condOnlineDays.getValue();

                // Batch-fetch last-online for all members upfront (100 per request)
                Map<Long,Long> lastOnlineMap = new HashMap<>();
                if (checkOnline) {
                    setStatus("Fetching last-online data...");
                    List<Long> allIds = new ArrayList<>();
                    for (RobloxApi.Member m2 : members) allIds.add(m2.userId);
                    lastOnlineMap = RobloxApi.getLastOnlineDays(allIds);
                }
                final Map<Long,Long> onlineMap = lastOnlineMap;

                for (RobloxApi.Member m : members) {
                    if (!scanning) break;
                    Set<Long> uGroups = RobloxApi.getUserGroupIds(m.userId);
                    // WHITELIST: in subgroup check
                    List<String> matched = new ArrayList<>();
                    for (int i=0; i<ids.size(); i++)
                        if (uGroups.contains(ids.get(i))) matched.add(names.get(i));

                    // Extra conditions
                    List<String> failReasons = new ArrayList<>();
                    long ageDays   = -1;
                    int  friends   = -1;

                    if (checkAge || checkFriends) {
                        RobloxApi.UserInfo info = RobloxApi.getUserInfo(m.userId);
                        if (checkAge && info.createdDate != null) {
                            ageDays = (System.currentTimeMillis() - info.createdDate.getTime()) / 86400000L;
                            if (ageDays < minAgeDays) failReasons.add("age:"+ageDays+"<"+minAgeDays);
                        }
                        if (checkFriends) {
                            friends = RobloxApi.getFriendCount(m.userId);
                            if (friends >= 0 && friends < minFriends) failReasons.add("friends:"+friends+"<"+minFriends);
                        }
                    }

                    long lastOnlineDays = -1;
                    if (checkOnline) {
                        lastOnlineDays = onlineMap.getOrDefault(m.userId, -1L);
                        if (lastOnlineDays >= 0 && lastOnlineDays > maxOffDays)
                            failReasons.add("online:"+lastOnlineDays+">"+maxOffDays);
                    }

                    boolean inSubgroup  = !matched.isEmpty();
                    boolean passedExtra = failReasons.isEmpty();
                    boolean safe = inSubgroup && passedExtra;

                    MemberResult r = new MemberResult(m, safe, matched, failReasons);
                    r.accountAgeDays = ageDays;
                    r.friendCount    = friends;
                    r.lastOnlineDays = lastOnlineDays;
                    if (!safe) r.queued = true;
                    publish(r); done++;
                    Thread.sleep(380);
                }
                return null;
            }
            protected void process(List<MemberResult> chunks) {
                results.addAll(chunks); tableModel.refresh(results);
                progressBar.setValue(done);
                long bad  = results.stream().filter(r->!r.safe).count();
                long good = results.stream().filter(r-> r.safe).count();
                setStatus("Scanned "+done+"/"+total+"  —  "+good+" safe, "+bad+" flagged");
                updateStats();
            }
            protected void done() {
                scanning=false; scanBtn.setText("  SCAN GROUP"); scanBtn.setBackground(ACCENT);
                progressBar.setVisible(false);
                long bad = results.stream().filter(r->!r.safe).count();
                setStatus("Scan complete — "+results.size()+" members, "+bad+" flagged for removal.");
                updateStats(); exportBtn.setEnabled(!results.isEmpty());
                kickQueueBtn.setEnabled(bad>0);
            }
        }.execute();
    }

    void stopScan() {
        scanning=false; scanBtn.setText("  SCAN GROUP"); scanBtn.setBackground(ACCENT);
        setStatus("Scan stopped.");
    }

    void kickQueued() {
        String cookie = new String(cookieField.getPassword()).trim();
        if (cookie.isEmpty()) { err("Paste your .ROBLOSECURITY cookie first."); return; }
        String mainText = mainGroupField.getText().trim();
        if (mainText.isEmpty()) { err("Main group ID not set."); return; }
        long mainId;
        try { mainId = Long.parseLong(mainText); } catch (NumberFormatException e) { err("Invalid ID."); return; }

        List<MemberResult> queue = results.stream()
            .filter(r->r.queued && !r.safe && !r.kicked).collect(Collectors.toList());
        if (queue.isEmpty()) { err("No members in the kick queue."); return; }

        int choice = JOptionPane.showConfirmDialog(this,
            "This will kick "+queue.size()+" members who are not in any required subgroup.\nThis cannot be undone. Continue?",
            "Confirm AutoKick", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        kickQueueBtn.setEnabled(false); scanBtn.setEnabled(false);
        progressBar.setVisible(true); progressBar.setIndeterminate(false);
        progressBar.setMaximum(queue.size()); progressBar.setValue(0);

        new SwingWorker<Void,Void>() {
            int done=0, failed=0;
            protected Void doInBackground() throws Exception {
                for (MemberResult r : queue) {
                    String res = RobloxApi.kickMember(mainId, r.member.userId, cookie);
                    if (res.equals("ok")) { r.kicked=true; r.queued=false; }
                    else failed++;
                    done++;
                    int d=done, f=failed;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(d);
                        setStatus("Kicking... "+d+"/"+queue.size()+" done, "+f+" failed");
                    });
                    Thread.sleep(600);
                }
                return null;
            }
            protected void done() {
                scanBtn.setEnabled(true); progressBar.setVisible(false);
                tableModel.refresh(results); updateStats();
                long kicked = queue.stream().filter(r->r.kicked).count();
                setStatus("AutoKick complete — "+kicked+" kicked, "+failed+" failed.");
                String msg = kicked+" members kicked successfully." + (failed>0 ? "\n"+failed+" failed — check cookie / rank permissions." : "");
                JOptionPane.showMessageDialog(GroupManager.this, msg,
                    "AutoKick Complete", failed>0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                long remaining = results.stream().filter(r->!r.safe && !r.kicked).count();
                kickQueueBtn.setEnabled(remaining>0);
            }
        }.execute();
    }

    void setQueueSelected(boolean queued) {
        for (int vr : table.getSelectedRows()) {
            MemberResult r = tableModel.getRow(table.convertRowIndexToModel(vr));
            if (r!=null && !r.safe && !r.kicked) r.queued=queued;
        }
        tableModel.fireTableDataChanged();
        updateKickBtn();
    }

    void openProfile() {
        int row = table.getSelectedRow(); if (row<0) return;
        MemberResult r = tableModel.getRow(table.convertRowIndexToModel(row)); if (r==null) return;
        String url = "https://www.roblox.com/users/"+r.member.userId+"/profile";
        openUrl(url);
    }

    void openUrl(String url) {
        // Try Java Desktop API first
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    return;
                }
            } catch (Exception ignored) {}
        }
        // Fallback: OS-specific command
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32","url.dll,FileProtocolHandler",url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                // Linux — try common browsers in order
                String[] browsers = {"xdg-open","firefox","google-chrome","chromium-browser","brave-browser"};
                boolean opened = false;
                for (String browser : browsers) {
                    try { Runtime.getRuntime().exec(new String[]{browser, url}); opened = true; break; }
                    catch (Exception ignored) {}
                }
                if (!opened) {
                    // Last resort: show URL in a dialog so user can copy it
                    JTextField urlField = new JTextField(url);
                    urlField.setEditable(false); urlField.selectAll();
                    JOptionPane.showMessageDialog(this,
                        new Object[]{"Could not open browser automatically.\nCopy this URL:", urlField},
                        "Open Profile", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (Exception ex) {
            JTextField urlField = new JTextField(url);
            urlField.setEditable(false); urlField.selectAll();
            JOptionPane.showMessageDialog(this,
                new Object[]{"Could not open browser.\nCopy this URL:", urlField},
                "Open Profile", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void exportReport() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("roblox_report.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(fc.getSelectedFile()))) {
            pw.println("=== ROBLOX GROUP MANAGER REPORT ===");
            pw.println("Date      : "+new Date());
            pw.println("Main Group: "+mainGroupField.getText().trim());
            pw.println("Subgroups : "+String.join(", ",subgroupNames));
            pw.println("Logic     : Member must be in >=1 subgroup to be SAFE");
            pw.println();
            List<MemberResult> bad  = results.stream().filter(r->!r.safe).collect(Collectors.toList());
            List<MemberResult> good = results.stream().filter(r-> r.safe).collect(Collectors.toList());
            pw.println("-- NOT IN ANY SUBGROUP ("+bad.size()+") --");
            for (MemberResult r : bad) {
                String extraInfo = (r.accountAgeDays>=0?" | age:"+r.accountAgeDays+"d":"")+(r.friendCount>=0?" | friends:"+r.friendCount:"")+(r.lastOnlineDays>=0?" | lastOnline:"+r.lastOnlineDays+"d ago":"");
                pw.println("  "+r.member.username+" (ID:"+r.member.userId+")"+extraInfo
                    +(r.kicked?" [KICKED]":r.queued?" [QUEUED]":""));
                pw.println("  Role: "+r.member.role+" (Rank "+r.member.rank+")");
                pw.println("  https://www.roblox.com/users/"+r.member.userId+"/profile");
                pw.println();
            }
            pw.println("-- SAFE MEMBERS ("+good.size()+") --");
            for (MemberResult r : good)
                pw.println("  "+r.member.username+"  |  "+r.member.role+"  |  in: "+String.join(", ",r.matchedSubgroups));
            setStatus("Report saved: "+fc.getSelectedFile().getName());
        } catch (IOException ex) { err("Save failed: "+ex.getMessage()); }
    }

    void clearResults() {
        results.clear(); tableModel.refresh(results);
        exportBtn.setEnabled(false); kickQueueBtn.setEnabled(false);
        statsLabel.setText("No scan yet"); setStatus("Results cleared.");
    }

    void updateStats() {
        long safe   = results.stream().filter(r-> r.safe).count();
        long bad    = results.stream().filter(r->!r.safe).count();
        long queued = results.stream().filter(r-> r.queued&&!r.safe).count();
        long kicked = results.stream().filter(r-> r.kicked).count();
        statsLabel.setText(results.size()+" total  |  "+safe+" safe  |  "+bad+" flagged  |  "+queued+" queued  |  "+kicked+" kicked");
    }

    void updateKickBtn() {
        long q = results.stream().filter(r->r.queued&&!r.safe&&!r.kicked).count();
        kickQueueBtn.setEnabled(q>0);
    }

    // Builders
    static JLabel lbl(String t,Font f,Color c){JLabel l=new JLabel(t);l.setFont(f);l.setForeground(c);return l;}
    static JLabel secLabel(String t){return lbl(t,new Font("Monospaced",Font.BOLD,10),DIM);}
    static Component vgap(int h){return Box.createRigidArea(new Dimension(0,h));}
    static JTextField textField(String ph){
        JTextField f=new JTextField(){
            @Override protected void paintComponent(Graphics g){
                super.paintComponent(g);
                if(getText().isEmpty()&&!isFocusOwner()){
                    Graphics2D g2=(Graphics2D)g; g2.setColor(new Color(0x44445A));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    Insets i=getInsets(); g2.drawString(ph,i.left+2,getHeight()-i.bottom-4);
                }
            }
        };
        f.setBackground(SURF2);f.setForeground(TEXT);f.setCaretColor(ACCENT);
        f.setFont(new Font("Monospaced",Font.PLAIN,13));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,1,1,1,BORDER),new EmptyBorder(6,10,6,10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));f.setAlignmentX(0);
        return f;
    }
    static JButton accentBtn(String t){
        JButton b=new JButton(t);b.setBackground(ACCENT);b.setForeground(Color.WHITE);
        b.setFont(new Font("Monospaced",Font.BOLD,11));b.setBorder(new EmptyBorder(6,10,6,10));
        b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JButton dimBtn(String t){
        JButton b=new JButton(t);b.setBackground(SURF2);b.setForeground(DIM);
        b.setFont(new Font("Monospaced",Font.PLAIN,11));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,1,1,1,BORDER),new EmptyBorder(6,10,6,10)));
        b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));b.setAlignmentX(0);return b;
    }
    static JToggleButton filterToggle(String t,boolean sel){
        JToggleButton b=new JToggleButton(t,sel);
        b.setFont(new Font("Monospaced",Font.BOLD,10));b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBackground(sel?ACCENT:SURF2);b.setForeground(sel?Color.WHITE:DIM);
        b.setBorder(new EmptyBorder(4,10,4,10));
        b.addChangeListener(e->{b.setBackground(b.isSelected()?ACCENT:SURF2);b.setForeground(b.isSelected()?Color.WHITE:DIM);});
        return b;
    }
    static JMenuItem menuItem(String t){JMenuItem m=new JMenuItem(t);m.setBackground(SURF2);m.setForeground(TEXT);return m;}
    static JScrollPane styledScroll(Component c){
        JScrollPane sp=new JScrollPane(c);
        sp.setBorder(BorderFactory.createMatteBorder(1,1,1,1,BORDER));
        sp.getViewport().setBackground(SURF);sp.setBackground(SURF);
        sp.getVerticalScrollBar().setBackground(SURF2);return sp;
    }
    static void styleSpinner(JSpinner s){
        s.setBackground(SURF2); s.setForeground(TEXT);
        s.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,1,1,1,BORDER), new EmptyBorder(2,4,2,4)));
        JComponent editor = s.getEditor();
        if(editor instanceof JSpinner.DefaultEditor de){
            de.getTextField().setBackground(SURF2); de.getTextField().setForeground(TEXT);
            de.getTextField().setFont(new Font("Monospaced",Font.PLAIN,12));
            de.getTextField().setCaretColor(ACCENT);
        }
    }
    void setStatus(String m){SwingUtilities.invokeLater(()->statusLabel.setText(m));}
    void err(String m){JOptionPane.showMessageDialog(this,m,"Error",JOptionPane.ERROR_MESSAGE);}

    // Data models

    static class MemberResult {
        RobloxApi.Member member;
        boolean safe, queued, kicked;
        List<String> matchedSubgroups;
        List<String> failReasons; // why flagged beyond subgroups
        int rank;
        int friendCount = -1;   // -1 = not fetched
        long accountAgeDays = -1;
        long lastOnlineDays = -1;  // days since last online
        MemberResult(RobloxApi.Member m, boolean safe, List<String> matched, List<String> reasons){
            member=m; this.safe=safe; matchedSubgroups=matched; failReasons=reasons; rank=m.rank;
        }
    }

    static class ResultTableModel extends AbstractTableModel {
        enum Filter{ALL,FLAGGED,SAFE}
        Filter filter=Filter.ALL;
        List<MemberResult> all=new ArrayList<>(),view=new ArrayList<>();
        final String[] COLS={"USERNAME","RANK","ROLE","IN SUBGROUPS","AGE (DAYS)","FRIENDS","LAST ONLINE","QUEUED","STATUS"};
        void refresh(List<MemberResult> d){
            all=new ArrayList<>(d);
            List<MemberResult> newView=all.stream().filter(r->switch(filter){
                case FLAGGED->!r.safe; case SAFE->r.safe; default->true;
            }).collect(Collectors.toList());
            int oldSize=view.size();
            view=newView;
            if(newView.size()>oldSize){
                // Only appending rows — fire insert to preserve selection
                fireTableRowsInserted(oldSize, newView.size()-1);
            } else {
                // Full reset needed (cleared or filter changed)
                fireTableDataChanged();
            }
        }
        void setFilter(Filter f){filter=f;applyFilter();}
        void applyFilter(){
            view=all.stream().filter(r->switch(filter){
                case FLAGGED->!r.safe; case SAFE->r.safe; default->true;
            }).collect(Collectors.toList());
            fireTableDataChanged();
        }
        MemberResult getRow(int i){return i>=0&&i<view.size()?view.get(i):null;}
        public int getRowCount(){return view.size();}
        public int getColumnCount(){return COLS.length;}
        public String getColumnName(int c){return COLS[c];}
        public Object getValueAt(int r,int c){
            MemberResult res=view.get(r);
            return switch(c){
                case 0->res.member.username;
                case 1->res.member.rank;
                case 2->res.member.role;
                case 3->res.matchedSubgroups.isEmpty()?"-- none":String.join(", ",res.matchedSubgroups);
                case 4->res.accountAgeDays<0?"...":String.valueOf(res.accountAgeDays)+"d";
                case 5->res.friendCount<0?"...":String.valueOf(res.friendCount);
                case 6->res.lastOnlineDays<0?"...":(res.lastOnlineDays==0?"today":res.lastOnlineDays+"d ago");
                case 7->res.safe?"":(res.kicked?"KICKED":(res.queued?"queued":"--"));
                case 8->res.kicked?"KICKED":(res.safe?"SAFE":"REMOVE");
                default->"";
            };
        }
    }

    static class RowRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable t,Object val,boolean sel,boolean focus,int row,int col){
            super.getTableCellRendererComponent(t,val,sel,focus,row,col);
            int mr=t.convertRowIndexToModel(row);
            MemberResult r=((ResultTableModel)t.getModel()).getRow(mr);
            if(r==null)return this;
            setOpaque(true);setBorder(new EmptyBorder(0,10,0,10));
            setFont(new Font("SansSerif",Font.PLAIN,13));
            Color rowBg=r.kicked?new Color(0x111118):(!r.safe?FLAG_ROW:(row%2==0?SURF:new Color(0x14141C)));
            if(sel){setBackground(SURF3);setForeground(TEXT);}
            else{
                setBackground(rowBg);
                if(col==8){
                    setFont(new Font("Monospaced",Font.BOLD,10));
                    setForeground(r.kicked?DIM:(r.safe?GREEN:ACCENT));
                } else if(col==7){
                    setFont(new Font("Monospaced",Font.PLAIN,11));
                    setForeground(r.kicked?DIM:(r.queued?YELLOW:DIM));
                } else if(col==3){
                    setForeground(r.safe?new Color(0x44DDAA):new Color(0x885566));
                } else if(col==4||col==5||col==6){
                    boolean ageFail     = col==4 && r.failReasons!=null && r.failReasons.stream().anyMatch(s->s.startsWith("age"));
                    boolean frndFail    = col==5 && r.failReasons!=null && r.failReasons.stream().anyMatch(s->s.startsWith("friends"));
                    boolean onlineFail  = col==6 && r.failReasons!=null && r.failReasons.stream().anyMatch(s->s.startsWith("online"));
                    setForeground((ageFail||frndFail||onlineFail) ? ACCENT : (r.kicked?DIM:TEXT));
                    setFont(new Font("Monospaced",Font.PLAIN,12));
                } else {
                    setForeground(r.kicked?DIM:TEXT);
                }
            }
            setHorizontalAlignment((col==1||col==4||col==5||col==6)?CENTER:LEFT);
            return this;
        }
    }

    // Roblox API (pure java.net, no external libs)

    static class RobloxApi {
        static class Member{long userId;String username,role;int rank;
            Member(long id,String u,String ro,int ra){userId=id;username=u;role=ro;rank=ra;}}

        static String get(String url){return req("GET",url,null,null);}

        static String req(String method,String url,String body,String cookie){
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setRequestMethod(method);
                c.setRequestProperty("User-Agent","RobloxGroupManager/1.0");
                c.setRequestProperty("Accept","application/json");
                if(cookie!=null&&!cookie.isEmpty()){
                    String ck=cookie.startsWith(".ROBLOSECURITY=")?cookie:".ROBLOSECURITY="+cookie;
                    c.setRequestProperty("Cookie",ck);
                }
                if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                    c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));}
                c.setConnectTimeout(10000);c.setReadTimeout(15000);
                InputStream is=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
                if(is==null)return ""+c.getResponseCode();
                try(Scanner sc=new Scanner(is,StandardCharsets.UTF_8)){sc.useDelimiter("\\A");return sc.hasNext()?sc.next():""+c.getResponseCode();}
            }catch(Exception e){return "";}
        }

        static String extractStr(String json,String key){
            String s="\""+key+"\""; int pos=json.indexOf(s); if(pos<0)return "";
            int colon=json.indexOf(':',pos+s.length()); if(colon<0)return "";
            int st=colon+1; while(st<json.length()&&json.charAt(st)==' ')st++;
            if(st>=json.length())return "";
            if(json.charAt(st)=='"'){int e=json.indexOf('"',st+1);return e<0?"":json.substring(st+1,e);}
            else{int e=st;while(e<json.length()&&",}]\n".indexOf(json.charAt(e))<0)e++;return json.substring(st,e).trim();}
        }

        static String extractUserObj(String obj){
            int idx=obj.indexOf("\"user\""); if(idx<0)return "";
            int b=obj.indexOf('{',idx); if(b<0)return "";
            int d=0,e=b;
            for(int i=b;i<obj.length();i++){if(obj.charAt(i)=='{')d++;else if(obj.charAt(i)=='}'){d--;if(d==0){e=i;break;}}}
            return obj.substring(b,e+1);
        }

        static String getGroupName(long id){return extractStr(get("https://groups.roblox.com/v1/groups/"+id),"name");}

        static List<Member> getGroupMembers(long groupId,java.util.function.BooleanSupplier running){
            List<Member> out=new ArrayList<>();String cursor="";
            for(int page=0;page<300;page++){
                if(!running.getAsBoolean())break;
                String url="https://groups.roblox.com/v1/groups/"+groupId+"/users?limit=100&sortOrder=Asc"+(cursor.isEmpty()?"":"&cursor="+cursor);
                String resp=get(url); if(resp.isEmpty())break;
                int arrOpen=resp.indexOf('[',resp.indexOf("\"data\"")); if(arrOpen<0)break;
                int i=arrOpen+1;
                while(i<resp.length()){
                    int os=resp.indexOf('{',i); if(os<0)break;
                    int d=0,oe=os;
                    for(int j=os;j<resp.length();j++){if(resp.charAt(j)=='{')d++;else if(resp.charAt(j)=='}'){d--;if(d==0){oe=j;break;}}}
                    String obj=resp.substring(os,oe+1);
                    String uobj=extractUserObj(obj);String src=uobj.isEmpty()?obj:uobj;
                    String idStr=extractStr(src,"userId");if(idStr.isEmpty())idStr=extractStr(src,"id");
                    if(!idStr.isEmpty()&&!idStr.equals("0")){
                        try{out.add(new Member(Long.parseLong(idStr),extractStr(src,"username"),extractStr(obj,"name"),
                            Integer.parseInt(extractStr(obj,"rank").isEmpty()?"0":extractStr(obj,"rank"))));}catch(Exception ignored){}
                    }
                    i=oe+1; if(i<resp.length()&&resp.charAt(i)==']')break;
                }
                String next=extractStr(resp,"nextPageCursor");
                if(next.isEmpty()||next.equals("null"))break;
                cursor=next; try{Thread.sleep(300);}catch(InterruptedException e){break;}
            }
            return out;
        }

        static class UserInfo {
            java.util.Date createdDate;
            boolean isBanned;
        }

        static UserInfo getUserInfo(long userId){
            UserInfo info = new UserInfo();
            String resp = get("https://users.roblox.com/v1/users/"+userId);
            if(resp.isEmpty()) return info;
            String created = extractStr(resp,"created");
            if(!created.isEmpty()){
                try{
                    // Format: 2020-03-15T12:34:56.000Z
                    java.time.Instant inst = java.time.Instant.parse(created.endsWith("Z")?created:created+"Z");
                    info.createdDate = java.util.Date.from(inst);
                }catch(Exception ignored){}
            }
            info.isBanned = "true".equals(extractStr(resp,"isBanned"));
            return info;
        }

        static int getFriendCount(long userId){
            String resp = get("https://friends.roblox.com/v1/users/"+userId+"/friends/count");
            if(resp.isEmpty()) return -1;
            String count = extractStr(resp,"count");
            try{ return Integer.parseInt(count); }catch(Exception e){ return -1; }
        }

        /**
         * Batch-fetch last-online for up to thousands of users.
         * Presence API accepts 100 userIds per POST.
         * Returns map of userId -> days since last online (0 = today).
         */
        static Map<Long,Long> getLastOnlineDays(List<Long> userIds){
            Map<Long,Long> result = new HashMap<>();
            int batchSize = 100;
            for(int start=0; start<userIds.size(); start+=batchSize){
                List<Long> batch = userIds.subList(start, Math.min(start+batchSize, userIds.size()));
                StringBuilder body = new StringBuilder("{\"userIds\":[");
                for(int i=0;i<batch.size();i++){if(i>0)body.append(',');body.append(batch.get(i));}
                body.append("]}");
                String resp = req("POST","https://presence.roblox.com/v1/presence/last-online",body.toString(),null);
                if(resp.isEmpty()) continue;
                // Parse array of {userId, lastOnline} objects
                int arrOpen = resp.indexOf('['); if(arrOpen<0) continue;
                int i = arrOpen+1;
                while(i<resp.length()){
                    int os=resp.indexOf('{',i); if(os<0) break;
                    int d=0,oe=os;
                    for(int j=os;j<resp.length();j++){if(resp.charAt(j)=='{')d++;else if(resp.charAt(j)=='}'){d--;if(d==0){oe=j;break;}}}
                    String obj=resp.substring(os,oe+1);
                    String uid=extractStr(obj,"userId");
                    String lo =extractStr(obj,"lastOnline");
                    if(!uid.isEmpty()&&!lo.isEmpty()){
                        try{
                            long id=Long.parseLong(uid);
                            java.time.Instant inst=java.time.Instant.parse(lo.endsWith("Z")?lo:lo+"Z");
                            long days=(System.currentTimeMillis()-inst.toEpochMilli())/86400000L;
                            result.put(id,Math.max(0,days));
                        }catch(Exception ignored){}
                    }
                    i=oe+1; if(i<resp.length()&&resp.charAt(i)==']') break;
                }
                try{Thread.sleep(200);}catch(InterruptedException e){break;}
            }
            return result;
        }

        static Set<Long> getUserGroupIds(long userId){
            String resp=get("https://groups.roblox.com/v1/users/"+userId+"/groups/roles");
            Set<Long> ids=new HashSet<>(); if(resp.isEmpty())return ids;
            int pos=0;
            while((pos=resp.indexOf("\"group\"",pos))>=0){
                int b=resp.indexOf('{',pos); if(b<0)break;
                int d=0,e=b;
                for(int i=b;i<resp.length();i++){if(resp.charAt(i)=='{')d++;else if(resp.charAt(i)=='}'){d--;if(d==0){e=i;break;}}}
                String idStr=extractStr(resp.substring(b,e+1),"id");
                if(!idStr.isEmpty())try{ids.add(Long.parseLong(idStr));}catch(Exception ignored){}
                pos=e+1;
            }
            return ids;
        }

        /**
         * Kick a member. Requires a .ROBLOSECURITY cookie from an account
         * with kick permissions in the group.
         * Returns "ok" on success, or an error description.
         *
         * Roblox uses CSRF protection on mutating requests. We handle the
         * two-step flow: first request returns 403 + x-csrf-token header,
         * second request includes that token and succeeds.
         */
        static String kickMember(long groupId,long userId,String cookie){
            try{
                String ck=cookie.startsWith(".ROBLOSECURITY=")?cookie:".ROBLOSECURITY="+cookie;
                String kickUrl="https://groups.roblox.com/v1/groups/"+groupId+"/users/"+userId;

                // First attempt — will get 403 + CSRF token
                HttpURLConnection c1=(HttpURLConnection)new URL(kickUrl).openConnection();
                c1.setRequestMethod("DELETE");
                c1.setRequestProperty("Cookie",ck);
                c1.setRequestProperty("User-Agent","RobloxGroupManager/1.0");
                c1.setRequestProperty("Content-Length","0");
                c1.setConnectTimeout(10000);c1.setReadTimeout(15000);
                c1.connect();
                int code1=c1.getResponseCode();
                if(code1==200)return "ok";

                String csrf=c1.getHeaderField("x-csrf-token");
                if(csrf==null||csrf.isEmpty())return "HTTP "+code1+" (no CSRF token — check cookie/permissions)";

                // Second attempt with CSRF token
                HttpURLConnection c2=(HttpURLConnection)new URL(kickUrl).openConnection();
                c2.setRequestMethod("DELETE");
                c2.setRequestProperty("Cookie",ck);
                c2.setRequestProperty("X-CSRF-TOKEN",csrf);
                c2.setRequestProperty("User-Agent","RobloxGroupManager/1.0");
                c2.setRequestProperty("Content-Length","0");
                c2.setConnectTimeout(10000);c2.setReadTimeout(15000);
                c2.connect();
                int code2=c2.getResponseCode();
                return code2==200?"ok":"HTTP "+code2;

            }catch(Exception e){return e.getMessage();}
        }
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(GroupManager::new);
    }
}