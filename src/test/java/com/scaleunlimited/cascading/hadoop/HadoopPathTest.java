package com.scaleunlimited.cascading.hadoop;


import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.scaleunlimited.cascading.BasePath;

public class HadoopPathTest extends Assert {

    @Test
    public void test() throws Exception {
        // Clear it out first.
        final String targetDirname = "build/test/HadoopPathTest/test";
        File targetDirFile = new File(targetDirname);
        FileUtils.deleteDirectory(targetDirFile);
        assertFalse(targetDirFile.exists());
        
        BasePath path = new HadoopPath(targetDirname);
        assertEquals(targetDirname, path.getPath());
        assertEquals(targetDirFile.toURI().toString(), path.getAbsolutePath());
        assertEquals(targetDirFile.toURI().toString(), path.toString());
        
        assertFalse(path.exists());
        assertTrue(path.mkdirs());
        assertTrue(path.isDirectory());
        assertFalse(path.isFile());
        assertTrue(targetDirFile.exists());
        assertTrue(targetDirFile.isDirectory());
        
        // Now try out relative paths.
        final String subdirName = "subdir";
        BasePath subPath = new HadoopPath(path, subdirName);
        assertEquals(targetDirname + "/subdir", subPath.getPath());

        BasePath dest = new HadoopPath("build/test/HadoopPathTest/test2");
        dest.delete(true);
        assertFalse(dest.exists());
        assertTrue(path.rename(dest));
        assertTrue(dest.exists());
        assertFalse(path.exists());
        
        // A rename to itself always succeeds, it's just that nothing has changed.
        assertTrue(dest.rename(dest));
    }

    @Test
    public void testInputOutput() throws Exception {
        // Clear it out first.
        final String targetDirname = "build/test/HadoopPathTest/testInputOutput";
        File targetDirFile = new File(targetDirname);
        FileUtils.deleteDirectory(targetDirFile);
        assertFalse(targetDirFile.exists());
        
        BasePath path = new HadoopPath(targetDirname);
        assertTrue(path.mkdirs());
        
        BasePath file = new HadoopPath(path, "test.txt");
        assertFalse(file.exists());
        assertTrue(file.createNewFile());
        assertTrue(file.exists());
        assertTrue(file.isFile());
        
        final String sourceStr = "Hello world";
        OutputStream os = file.openOutputStream();
        os.write(sourceStr.getBytes("UTF-8"));
        os.close();
        
        InputStream is = file.openInputStream();
        byte[] buffer = new byte[1000];
        int len = is.read(buffer);
        String result = new String(buffer, 0, len, "UTF-8");
        assertEquals(sourceStr, result);
        is.close();
    }

}
