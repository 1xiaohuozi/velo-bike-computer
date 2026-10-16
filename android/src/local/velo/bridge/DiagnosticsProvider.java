package local.velo.bridge;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
/** Read-only grantable access to explicitly exported diagnostic ZIP files. */
public class DiagnosticsProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 File resolve(Uri uri)throws FileNotFoundException{if(!"local.velo.bridge.diagnostics".equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new FileNotFoundException();String name=uri.getLastPathSegment();if(name==null||!name.matches("ride-[0-9]+\\.zip"))throw new FileNotFoundException();File f=new File(new File(getContext().getCacheDir(),"ride-exports"),name);if(!f.isFile())throw new FileNotFoundException();return f;}
 public String getType(Uri uri){return "application/zip";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("read only");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=resolve(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(row);return c;}catch(FileNotFoundException e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
