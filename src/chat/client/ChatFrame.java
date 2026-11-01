package chat.client;

import chat.common.Packet;
import common.KetQuaDemChuCai;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Main chat + coursework Lab. All socket/disk work runs on background executors. */
public final class ChatFrame extends JFrame {
    private final JTextField username=new JTextField("Alice",9),host=new JTextField("localhost",13),port=new JTextField("5555",5);
    private final JButton connect=new JButton("Kết nối"),disconnect=new JButton("Ngắt");
    private final JLabel status=new JLabel("Chưa kết nối"),conversation=new JLabel("Chọn người dùng để trò chuyện");
    private final DefaultListModel<String> userModel=new DefaultListModel<>();
    private final JList<String> users=new JList<>(userModel);
    private final Set<String> online=new HashSet<>(),known=new TreeSet<>();
    private final JTextArea messages=area(),input=new JTextArea(3,30),logs=area(),labText=new JTextArea(5,35),cipher=area();
    private final JTextField labKey=new JTextField(24);
    private final JButton send=new JButton("Gửi tin"),attach=new JButton("Gửi file…"),cancel=new JButton("Hủy upload"),refresh=new JButton("Tải lịch sử");
    private final JButton rotate=new JButton("Áp dụng / đổi khóa"),preview=new JButton("Xem bản mã"),analyze=new JButton("Mã hóa và phân tích");
    private final JLabel rsa=new JLabel("RSA Server: chưa kết nối"),labStatus=new JLabel("Kết quả phân tích xuất hiện tại đây");
    private final JProgressBar progress=new JProgressBar(0,100);
    private final DefaultTableModel letterModel=new DefaultTableModel(new Object[]{"Chữ cái","Số lần"},0) {
        @Override public boolean isCellEditable(int r,int c) {return false;}
    };
    private record Attachment(String id,String peer,String name,long size,String status,Path local) {
        @Override public String toString(){return name+" ("+size+" byte) — "+status;}
    }
    private final Map<String,Attachment> attachments=new LinkedHashMap<>();
    private final DefaultListModel<Attachment> attachmentModel=new DefaultListModel<>();
    private final JList<Attachment> files=new JList<>(attachmentModel);
    private final JButton save=new JButton("Lưu bản sao…"),download=new JButton("Tải file đã chọn");
    private final Map<String,LinkedHashMap<String,Packet>> conversations=new HashMap<>();
    private final ExecutorService background=Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean uploadCancelled=new AtomicBoolean();
    private volatile ChatClient client;
    private boolean connecting,uploading;
    private long generation;
    private String own="";

    public ChatFrame() {
        super("Đề tài 8 — Chat TCP / AES-GCM");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(960,680)); setSize(1180,800);
        setLocationRelativeTo(null);
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(Component c:new Component[]{new JLabel("Tên:"),username,new JLabel("Server:"),host,new JLabel("Cổng:"),port,connect,disconnect,status}) top.add(c);
        add(top,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane(); tabs.addTab("Trò chuyện",chatPanel());tabs.addTab("Encryption Lab / Đếm chữ cái",labPanel());tabs.addTab("Kết nối / Nhật ký",logPanel());
        add(tabs,BorderLayout.CENTER);
        connect.addActionListener(e->connect()); disconnect.addActionListener(e->disconnect());
        send.addActionListener(e->sendText());attach.addActionListener(e->chooseFile());cancel.addActionListener(e->{uploadCancelled.set(true);log("Đã yêu cầu hủy upload");});
        refresh.addActionListener(e->loadHistory());
        users.addListSelectionListener(e->{if(!e.getValueIsAdjusting()){render();loadHistory();}});
        rotate.addActionListener(e->{String phrase=labKey.getText(); run(c->c.rotate(phrase));});
        preview.addActionListener(e->{String text=labText.getText();run(c->{String value=c.preview(text);SwingUtilities.invokeLater(()->cipher.setText("AES-GCM: nonce 12 byte + ciphertext + tag 16 byte (Base64)\n"+value+"\nMẫu xem trước dùng AAD 'preview'; frame mạng có AAD phiên/hướng/sequence riêng."));});});
        analyze.addActionListener(e->{String text=labText.getText(),phrase=labKey.getText();run(c->{if(!phrase.isBlank()) c.rotate(phrase);c.lab(text);});});
        save.addActionListener(e->saveFile()); download.addActionListener(e->{Attachment a=files.getSelectedValue();if(a!=null)run(c->c.download(a.id()));});
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){disconnect();background.shutdownNow();dispose();}});
        updateControls(); log("Sẵn sàng. Username chỉ là tên hiển thị, không xác thực mật khẩu.");
    }
    private JPanel chatPanel() {
        JPanel left=new JPanel(new BorderLayout(5,5));left.setBorder(BorderFactory.createTitledBorder("Người dùng / Lịch sử"));
        users.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        users.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean selected,boolean focus){return super.getListCellRendererComponent(l,v+(online.contains(v)?" — Online":" — Offline"),i,selected,focus);}});
        left.add(new JScrollPane(users));left.add(refresh,BorderLayout.SOUTH);
        JPanel center=new JPanel(new BorderLayout(6,6));center.add(conversation,BorderLayout.NORTH);center.add(new JScrollPane(messages));
        JPanel compose=new JPanel(new BorderLayout(5,5));input.setLineWrap(true);input.setWrapStyleWord(true);compose.add(new JScrollPane(input));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(attach);buttons.add(send);compose.add(buttons,BorderLayout.EAST);
        center.add(compose,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,center);split.setDividerLocation(230);
        JPanel transfers=new JPanel(new BorderLayout(4,4));transfers.setBorder(BorderFactory.createTitledBorder("File đính kèm — chỉ lưu bản hoàn chỉnh sau khi SHA-256 khớp"));
        files.setVisibleRowCount(3);transfers.add(new JScrollPane(files));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(download);actions.add(save);actions.add(cancel);transfers.add(actions,BorderLayout.EAST);
        progress.setStringPainted(true);progress.setString("Chưa có chuyển file");transfers.add(progress,BorderLayout.SOUTH);
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));panel.add(split);panel.add(transfers,BorderLayout.SOUTH);return panel;
    }
    private JPanel labPanel() {
        JPanel form=new JPanel(new BorderLayout(6,6));
        JPanel keyRow=new JPanel(new FlowLayout(FlowLayout.LEFT));keyRow.add(new JLabel("Khóa do bạn nhập:"));keyRow.add(labKey);keyRow.add(rotate);form.add(keyRow,BorderLayout.NORTH);
        labText.setLineWrap(true);labText.setWrapStyleWord(true);labText.setText("Xin chào AES — Đại Anh Dũng");
        JPanel editor=new JPanel(new BorderLayout());editor.setBorder(BorderFactory.createTitledBorder("Bản rõ"));editor.add(new JScrollPane(labText));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(preview);actions.add(analyze);editor.add(actions,BorderLayout.SOUTH);form.add(editor);
        JTextArea note=area();note.setText("Chat mặc định dùng khóa phiên ngẫu nhiên. Nhập khóa tại đây để đổi khóa phiên; nút Phân tích tự áp dụng khóa đã nhập.\nServer giải mã và đếm chữ cái, kể cả tiếng Việt. Đây là mã hóa Client–Server; Server đọc được nội dung.");note.setRows(3);form.add(note,BorderLayout.SOUTH);
        JPanel result=new JPanel(new BorderLayout(5,5));result.add(labStatus,BorderLayout.NORTH);result.add(new JScrollPane(new JTable(letterModel)));
        cipher.setRows(5);cipher.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));result.add(new JScrollPane(cipher),BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,form,result);split.setResizeWeight(.5);
        JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));panel.add(split);return panel;
    }
    private JPanel logPanel() {
        JPanel p=new JPanel(new BorderLayout());p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));p.add(rsa,BorderLayout.NORTH);p.add(new JScrollPane(logs));return p;
    }
    private static JTextArea area(){JTextArea a=new JTextArea();a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);return a;}
    @FunctionalInterface private interface Action {void apply(ChatClient c)throws Exception;}
    private void run(Action action) {
        ChatClient captured=client; long epoch=generation;
        if(captured==null || !captured.connected()){log("Chưa kết nối Server");return;}
        background.submit(()->{try{action.apply(captured);}catch(Exception e){SwingUtilities.invokeLater(()->{if(epoch==generation)log("Lỗi: "+e.getMessage());});}});
    }
    private void connect() {
        if(connecting || (client!=null&&client.connected()))return;
        String name=username.getText().trim(),address=host.getText().trim();int number;
        try{number=Integer.parseInt(port.getText().trim());if(number<1||number>65535)throw new NumberFormatException();}
        catch(NumberFormatException e){log("Cổng phải nằm trong 1–65535");return;}
        if(!name.matches("[\\p{L}\\p{N}_-]{1,24}")){log("Tên gồm 1–24 chữ, số, _ hoặc -");return;}
        long epoch=++generation;own=name;connecting=true;conversations.clear();attachments.clear();known.clear();online.clear();userModel.clear();render();
        client=new ChatClient(Path.of(System.getProperty("chat.downloads","client-downloads"),name),p->SwingUtilities.invokeLater(()->{if(epoch==generation)event(p);}));
        ChatClient captured=client;updateControls();
        background.submit(()->{try{captured.connect(address,number,name,"");}catch(Exception e){SwingUtilities.invokeLater(()->{if(epoch==generation){connecting=false;log("Kết nối thất bại: "+e.getMessage());updateControls();}});}});
    }
    private void disconnect() {
        ++generation;connecting=false;uploadCancelled.set(true);uploading=false;
        ChatClient c=client;client=null;if(c!=null)c.close();online.clear();users.repaint();status.setText("Đã ngắt");updateControls();
    }
    private void sendText() {
        String peer=users.getSelectedValue(),text=input.getText();
        if(peer==null || text.isBlank()){log("Chọn người nhận và nhập tin nhắn");return;}
        run(c->c.text(peer,text));input.setText("");
    }
    private void loadHistory(){String peer=users.getSelectedValue();if(peer!=null)run(c->c.history(peer));}
    private void chooseFile() {
        String peer=users.getSelectedValue();if(peer==null){log("Chọn người nhận trước");return;}
        JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        startUpload(peer,chooser.getSelectedFile().toPath());
    }
    /** Also used by the GUI responsiveness test, with real transfer workers. */
    private void startUpload(String peer,Path source) {
        if(uploading)return;uploading=true;uploadCancelled.set(false);updateControls();ChatClient c=client;long epoch=generation;
        background.submit(()->{try{c.file(peer,source,uploadCancelled);}catch(Exception e){SwingUtilities.invokeLater(()->{if(epoch==generation)log("Gửi file lỗi: "+e.getMessage());});}
            finally{SwingUtilities.invokeLater(()->{if(epoch==generation){uploading=false;updateControls();}});}});
    }
    private void saveFile() {
        Attachment a=files.getSelectedValue();if(a==null||a.local()==null){log("Tải file trước khi lưu bản sao");return;}
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File(ChatClient.safeName(a.name())));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path target=chooser.getSelectedFile().toPath();
        if(Files.exists(target) && JOptionPane.showConfirmDialog(this,"Ghi đè file đã tồn tại?","Lưu file",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        background.submit(()->{try{Files.copy(a.local(),target,StandardCopyOption.REPLACE_EXISTING);SwingUtilities.invokeLater(()->log("Đã lưu: "+target));}
            catch(Exception e){SwingUtilities.invokeLater(()->log("Lưu file lỗi: "+e.getMessage()));}});
    }
    private void event(Packet p) {
        switch(p.type()) {
            case "CONNECTED" -> {connecting=false;status.setText("Online: "+own);rsa.setText("RSA fingerprint: "+p.extra()+" — cần đối chiếu trực tiếp với Server");run(ChatClient::contacts);log("Đã kết nối, phiên "+p.id());}
            case "DISCONNECTED" -> {connecting=false;uploading=false;online.clear();users.repaint();status.setText(p.text());log(p.text());}
            case "USERS" -> {online.clear();for(String name:p.text().split("\n"))if(!name.isBlank()&&!name.equals(own)){online.add(name);known.add(name);}updateUsers();}
            case "CONTACTS" -> {for(String name:p.text().split("\n"))if(!name.isBlank()&&!name.equals(own))known.add(name);updateUsers();}
            case "CHAT","HISTORY_ITEM" -> {
                String peer=p.from().equals(own)?p.to():p.from();known.add(peer);updateUsers();
                conversations.computeIfAbsent(peer,k->new LinkedHashMap<>()).put(p.id(),p);render();
                if(p.type().equals("HISTORY_ITEM") && p.data().length>0) showStatistics(new String(p.data(),java.nio.charset.StandardCharsets.UTF_8));
            }
            case "RESULT" -> {
                showStatistics(p.extra());
                log("[Phân tích] "+p.extra());
            }
            case "HISTORY_FILE" -> {
                String peer=p.from().equals(own)?p.to():p.from();Attachment old=attachments.get(p.id());
                attachments.put(p.id(),new Attachment(p.id(),peer,p.text(),p.size(),p.extra(),old==null?null:old.local()));renderAttachments();
            }
            case "FILE_READY" -> {attachments.put(p.text(),new Attachment(p.text(),p.to(),p.extra(),p.size(),"Đang upload",null));renderAttachments();}
            case "UPLOAD_PROGRESS","DOWNLOAD_PROGRESS" -> {progress.setValue(p.index());progress.setString((p.type().equals("UPLOAD_PROGRESS")?"Gửi ":"Nhận ")+p.text()+" — "+p.index()+"% / "+p.size()+" byte");}
            case "FILE_RECEIVED" -> {
                String peer=p.from().equals(own)?p.to():p.from();attachments.put(p.id(),new Attachment(p.id(),peer,p.text(),p.size(),"Đã nhận, SHA-256 khớp",Path.of(p.extra())));renderAttachments();
                progress.setValue(100);progress.setString("Đã nhận "+p.text()+" — SHA-256 khớp");log("[File] Đã nhận: "+p.text()+" → "+p.extra());
            }
            case "FILE_STATUS" -> {
                Attachment a=attachments.get(p.id());if(a!=null)attachments.put(p.id(),new Attachment(a.id(),a.peer(),a.name(),a.size(),p.text(),a.local()));renderAttachments();log("[File] "+p.text());progress.setString(p.text());
            }
            case "ERROR","FILE_ABORT" -> {log("[Lỗi] "+p.text());progress.setString(p.text());}
            case "ROTATED" -> log("Đã đổi khóa phiên AES-GCM; không lưu khóa xuống đĩa");
            default -> { }
        }
        updateControls();
    }
    private void showStatistics(String stats) {
        letterModel.setRowCount(0);var counts=KetQuaDemChuCai.parse(stats);
        counts.forEach((letter,count)->letterModel.addRow(new Object[]{letter,count}));
        labStatus.setText("Server đếm: "+counts.size()+" chữ khác nhau, tổng "+KetQuaDemChuCai.tongChuCai(counts)+" lần");
    }
    private void updateUsers() {
        String selected=users.getSelectedValue();
        List<String> current=Collections.list(userModel.elements());
        if(!current.equals(new ArrayList<>(known))) {
            userModel.clear();known.forEach(userModel::addElement);if(selected!=null)users.setSelectedValue(selected,true);
        }
        users.repaint();
    }
    private void render() {
        String peer=users.getSelectedValue();conversation.setText(peer==null?"Chọn người dùng để trò chuyện":"Cuộc trò chuyện với "+peer);
        StringBuilder b=new StringBuilder();
        if(peer!=null)conversations.getOrDefault(peer,new LinkedHashMap<>()).values().stream().sorted(Comparator.comparing(Packet::extra)).forEach(p->b.append(p.extra()).append("  ").append(p.from()).append(":\n").append(p.text()).append("\n\n"));
        messages.setText(b.toString());messages.setCaretPosition(messages.getDocument().getLength());renderAttachments();
    }
    private void renderAttachments(){String selected=files.getSelectedValue()==null?null:files.getSelectedValue().id();attachmentModel.clear();String peer=users.getSelectedValue();for(Attachment a:attachments.values())if(a.peer().equals(peer)){attachmentModel.addElement(a);if(a.id().equals(selected))files.setSelectedValue(a,true);}}
    private void updateControls() {
        boolean connected=client!=null&&client.connected();connect.setEnabled(!connected&&!connecting);disconnect.setEnabled(connected||connecting);
        username.setEnabled(!connected&&!connecting);host.setEnabled(!connected&&!connecting);port.setEnabled(!connected&&!connecting);
        for(JButton b:new JButton[]{send,refresh,rotate,preview,analyze,download})b.setEnabled(connected);
        attach.setEnabled(connected&&!uploading);cancel.setEnabled(connected&&uploading);save.setEnabled(!attachments.isEmpty());
    }
    private void log(String s){logs.append(java.time.LocalTime.now().withNano(0)+" "+s+"\n");logs.setCaretPosition(logs.getDocument().getLength());}
}
