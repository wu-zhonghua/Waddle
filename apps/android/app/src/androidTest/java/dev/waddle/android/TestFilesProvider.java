package dev.waddle.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Test APK only. Pure Java because this provider runs outside the instrumented app process. */
public class TestFilesProvider extends ContentProvider {
    private boolean directory(String id) { return Arrays.asList("root", "sub", "unsafe", "duplicate", "cycle").contains(id); }
    private String name(String id) {
        switch (id) {
            case "root": return "项目 中文";
            case "sub": return "子 目录";
            case "note": return "note '你好.txt";
            case "nested": return "code.sh";
            case "escape": return "../escape";
            case "duplicate1": case "duplicate2": return "same.txt";
            default: return id + ".txt";
        }
    }
    private byte[] bytes(String id) {
        switch (id) {
            case "note": return "你好，shared file\n".getBytes(StandardCharsets.UTF_8);
            case "nested": return "printf 'nested\\n'\n".getBytes(StandardCharsets.UTF_8);
            case "binary": return new byte[] {0, 1, 2, 3};
            case "long": {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < 20000; i++) text.append("line\n");
                return text.toString().getBytes(StandardCharsets.UTF_8);
            }
            default: return "fixture\n".getBytes(StandardCharsets.UTF_8);
        }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return directory(DocumentsContract.getDocumentId(uri)) ? DocumentsContract.Document.MIME_TYPE_DIR : "text/plain";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String id = DocumentsContract.getDocumentId(uri);
        if (id.equals("denied")) throw new SecurityException("Synthetic revoked permission");
        List<String> ids = Collections.singletonList(id);
        if ("children".equals(uri.getLastPathSegment())) {
            switch (id) {
                case "root": ids = Arrays.asList("note", "sub"); break;
                case "sub": ids = Collections.singletonList("nested"); break;
                case "unsafe": ids = Collections.singletonList("escape"); break;
                case "duplicate": ids = Arrays.asList("duplicate1", "duplicate2"); break;
                case "cycle": ids = Collections.singletonList("cycle"); break;
                default: ids = Collections.emptyList();
            }
        }
        String[] columns = projection != null ? projection : new String[] {"document_id", "_display_name", "mime_type", "_size"};
        MatrixCursor result = new MatrixCursor(columns);
        for (String item : ids) {
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                switch (columns[i]) {
                    case "document_id": values[i] = item; break;
                    case "_display_name": values[i] = name(item); break;
                    case "mime_type": values[i] = directory(item) ? DocumentsContract.Document.MIME_TYPE_DIR : "text/plain"; break;
                    case "_size": values[i] = directory(item) ? null : (item.equals("large") ? 600L * 1024 * 1024 : (long) bytes(item).length); break;
                }
            }
            result.addRow(values);
        }
        return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.equals("r")) throw new SecurityException("Read-only fixture");
        String id = DocumentsContract.getDocumentId(uri);
        if (id.equals("denied")) throw new SecurityException("Synthetic revoked permission");
        File file = new File(getContext().getCacheDir(), "fixture-" + id.hashCode());
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes(id)); }
        catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
