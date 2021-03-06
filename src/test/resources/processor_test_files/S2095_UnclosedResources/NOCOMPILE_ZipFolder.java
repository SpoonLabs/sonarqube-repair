
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import spoon.Launcher;
import spoon.compiler.SpoonFile;
import spoon.compiler.SpoonFolder;
import spoon.compiler.SpoonResourceHelper;

public class NOCOMPILE_ZipFolder implements SpoonFolder {

    File file;

    List<SpoonFile> files;

    public NOCOMPILE_ZipFolder(File file) throws IOException {
        super();
        if (!file.isFile()) {
            throw new IOException(file.getName() + " is not a valid zip file");
        }
        this.file = file;
    }

    public List<SpoonFile> getAllFiles() {
        return getFiles();
    }

    public List<SpoonFile> getAllJavaFiles() {
        List<SpoonFile> files = new ArrayList<>();

        for (SpoonFile f : getFiles()) {
            if (f.isJava()) {
                files.add(f);
            }
        }

        // no subfolder, skipping
        // for (CtFolder fol : getSubFolder())
        // files.addAll(fol.getAllJavaFile());
        return files;
    }

    public List<SpoonFile> getFiles() {
        // Indexing content
        if (files == null) {
            files = new ArrayList<>();
            ZipInputStream zipInput = null;
            try {
                zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));// Noncompliant

                ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    // deflate in buffer
                    final int buffer = 2048;
                    ByteArrayOutputStream output = new ByteArrayOutputStream(
                            buffer);
                    int count;
                    byte data[] = new byte[buffer];
                    while ((count = zipInput.read(data, 0, buffer)) != -1) {
                        output.write(data, 0, count);
                    }
                    output.flush();
                    output.close();

                    files.add(new ZipFile(this, entry.getName(), output
                            .toByteArray()));
                }
                zipInput.close();

            } catch (Exception e) {
                Launcher.LOGGER.error(e.getMessage(), e);
            }
        }
        return files;
    }

    public String getName() {
        return file.getName();
    }

    public SpoonFolder getParent() {
        try {
            return SpoonResourceHelper.createFolder(file.getParentFile());
        } catch (FileNotFoundException e) {
            Launcher.LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    public List<SpoonFolder> getSubFolders() {
        return new ArrayList<>(0);
    }

    public boolean isFile() {
        return false;
    }

    @Override
    public String toString() {
        return getPath();
    }

    public String getPath() {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            Launcher.LOGGER.error(e.getMessage(), e);
            return file.getPath();
        }
    }

    @Override
    public boolean isArchive() {
        return true;
    }

    @Override
    public File getFileSystemParent() {
        return file.getParentFile();
    }

    @Override
    public File toFile() {
        return file;
    }

    @Override
    public boolean equals(Object obj) {
        return toString().equals(obj.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public void addFile(SpoonFile source) {
        throw new UnsupportedOperationException("not possible a real folder");
    }

    @Override
    public void addFolder(SpoonFolder source) {
        throw new UnsupportedOperationException("not possible a real folder");
    }

    /** physically extracts on disk all files of this zip file in the destinationDir `destDir` */
    public void extract(File destDir) {
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));// Noncompliant

            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                File f = new File(destDir + File.separator + entry.getName());
                if (entry.isDirectory()) { // if its a directory, create it
                    f.mkdir();
                    continue;
                }
                // deflate in buffer
                final int buffer = 2048;
                // Force parent directory creation, sometimes directory was not yet handled
                f.getParentFile().mkdirs();
                // in the zip entry iteration
                OutputStream output = new BufferedOutputStream(new FileOutputStream(f));// Noncompliant
                int count;
                byte data[] = new byte[buffer];
                while ((count = zipInput.read(data, 0, buffer)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
            }
            zipInput.close();
        } catch (Exception e) {
            Launcher.LOGGER.error(e.getMessage(), e);
        }
    }
}
