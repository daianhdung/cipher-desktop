package chat.server;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** A single serialized JDBC connection: short transactions, no network I/O inside DB locks. */
public final class ChatRepository implements AutoCloseable {
    private final Connection db;
    public record Entry(String id,String sender,String recipient,String time,String kind,String keyId,byte[] cipher,String stats,String name,long size,String hash,String status,String path) {}
    public ChatRepository(Path path) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Files.createDirectories(path.toAbsolutePath().getParent());
        db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS users(username TEXT PRIMARY KEY, created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS client_sessions(id TEXT PRIMARY KEY, username TEXT NOT NULL REFERENCES users(username), remote TEXT, joined_at TEXT NOT NULL, left_at TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY, sender TEXT NOT NULL REFERENCES users(username), recipient TEXT, created_at TEXT NOT NULL, kind TEXT NOT NULL, key_id TEXT NOT NULL, ciphertext BLOB NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS letter_statistics(message_id TEXT PRIMARY KEY REFERENCES messages(id), statistics TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS file_attachments(id TEXT PRIMARY KEY, sender TEXT NOT NULL REFERENCES users(username), recipient TEXT NOT NULL REFERENCES users(username), created_at TEXT NOT NULL, name TEXT NOT NULL, size INTEGER NOT NULL, sha256 TEXT, status TEXT NOT NULL, path TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS messages_pair ON messages(sender,recipient,created_at)");
            s.execute("CREATE INDEX IF NOT EXISTS files_pair ON file_attachments(sender,recipient,created_at)");
        }
        update("UPDATE client_sessions SET left_at=? WHERE left_at IS NULL",now());
        update("UPDATE file_attachments SET status='INTERRUPTED' WHERE status IN ('UPLOADING','DELIVERING')");
    }
    public static String now() { return Instant.now().toString(); }
    private void update(String sql,Object... args) throws SQLException {
        try(var p=db.prepareStatement(sql)) { bind(p,args); p.executeUpdate(); }
    }
    private static void bind(PreparedStatement p,Object...args) throws SQLException {
        for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
    }
    public synchronized void join(String id,String user,String remote) throws SQLException {
        db.setAutoCommit(false);
        try {
            update("INSERT OR IGNORE INTO users VALUES(?,?)",user,now());
            update("INSERT INTO client_sessions VALUES(?,?,?,?,NULL)",id,user,remote,now()); db.commit();
        } catch(SQLException e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
    }
    public synchronized void leave(String id) throws SQLException { update("UPDATE client_sessions SET left_at=? WHERE id=?",now(),id); }
    public synchronized void message(String id,String from,String to,String kind,String keyId,byte[] cipher,String stats) throws SQLException {
        db.setAutoCommit(false);
        try {
            update("INSERT INTO messages VALUES(?,?,?,?,?,?,?)",id,from,to,now(),kind,keyId,cipher);
            update("INSERT INTO letter_statistics VALUES(?,?)",id,stats); db.commit();
        } catch(SQLException e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
    }
    public synchronized void file(String id,String from,String to,String name,long size,String path) throws SQLException {
        update("INSERT INTO file_attachments VALUES(?,?,?,?,?,?,NULL,'UPLOADING',?)",id,from,to,now(),name,size,path);
    }
    public synchronized void status(String id,String hash,String status) throws SQLException {
        update("UPDATE file_attachments SET sha256=COALESCE(?,sha256),status=? WHERE id=?",hash,status,id);
    }
    public synchronized Entry fileFor(String id,String user) throws SQLException {
        try(var p=db.prepareStatement("SELECT * FROM file_attachments WHERE id=? AND (sender=? OR recipient=?)")) {
            bind(p,id,user,user); try(var r=p.executeQuery()) { return r.next()?fileEntry(r):null; }
        }
    }
    private Entry fileEntry(ResultSet r) throws SQLException {
        return new Entry(r.getString("id"),r.getString("sender"),r.getString("recipient"),r.getString("created_at"),"FILE","",new byte[0],"",r.getString("name"),r.getLong("size"),r.getString("sha256"),r.getString("status"),r.getString("path"));
    }
    public synchronized List<Entry> history(String user,String peer) throws SQLException {
        List<Entry> list=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT m.*,l.statistics FROM messages m JOIN letter_statistics l ON l.message_id=m.id WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?) ORDER BY created_at DESC LIMIT 200")) {
            bind(p,user,peer,peer,user); try(var r=p.executeQuery()) { while(r.next()) list.add(new Entry(r.getString("id"),r.getString("sender"),r.getString("recipient"),r.getString("created_at"),r.getString("kind"),r.getString("key_id"),r.getBytes("ciphertext"),r.getString("statistics"),"",0,"","","")); }
        }
        try(var p=db.prepareStatement("SELECT * FROM file_attachments WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?) ORDER BY created_at DESC LIMIT 100")) {
            bind(p,user,peer,peer,user); try(var r=p.executeQuery()) { while(r.next()) list.add(fileEntry(r)); }
        }
        list.sort(Comparator.comparing(Entry::time)); return list;
    }
    public synchronized List<String> users(String except) throws SQLException {
        List<String> users=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT username FROM users WHERE username<>? ORDER BY username LIMIT 200")) {
            bind(p,except); try(var r=p.executeQuery()) { while(r.next()) users.add(r.getString(1)); }
        }
        return users;
    }
    @Override public synchronized void close() throws SQLException { db.close(); }
}
