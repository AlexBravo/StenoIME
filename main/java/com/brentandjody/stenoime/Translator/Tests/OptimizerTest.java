package com.brentandjody.stenoime.Translator.Tests;

import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.util.Log;
import android.widget.ProgressBar;

import com.brentandjody.stenoime.Translator.Dictionary;
import com.brentandjody.stenoime.Translator.Optimizer;
import com.brentandjody.stenoime.Translator.SimpleTranslator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;

public class OptimizerTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        InputStream in = getContext().getAssets().open("test.json");
        File outFile = new File("/sdcard", "test.json");
        OutputStream out = new FileOutputStream(outFile);
        copyFile(in, out);
        in.close();
        out.flush();
        out.close();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        File outFile = new File("/sdcard", "test.json");
        outFile.delete();
    }

    public void testBetterStroke() throws Exception {
        final Dictionary dictionary = new Dictionary(getContext());
        dictionary.load(new String[]{"/sdcard/test.json"}, null, 10);
        SystemClock.sleep(1000); //wait for dictionary to load
        assertFalse(dictionary.isLoading());
        assertTrue(dictionary.size() > 10);
        Optimizer optimizer = new Optimizer(getContext(), dictionary);
        SystemClock.sleep(5000); //wait for thesaurus to load
        assertTrue(optimizer.isLoaded());
        //These two are equal length, and should not optimize
        assertNull(optimizer.test_analyze("AD/SRAPB/TAPBLG", 0, "advantage "));
        assertNull(optimizer.test_analyze("AD/SRAPBT/APBLG", 0, "advantage "));
        //This one has a shorter stroke
        assertEquals(optimizer.test_analyze("AD/SRAPB/TAEU/SKWROUS", 0, "advantageous "), "AD/SRAPBGS");
        //Fingerspelling
        assertNull(optimizer.test_analyze("*A", 0, "a "));
        assertNull(optimizer.test_analyze("*TK", 1, "d "));
        assertNull(optimizer.test_analyze("*SR", 1, "v "));
        assertNull(optimizer.test_analyze("*A", 1, "a "));
        assertNull(optimizer.test_analyze("*TPH", 1, "n "));
        assertNull(optimizer.test_analyze("*KR", 1, "c "));
        assertEquals(optimizer.test_analyze("*-E", 1, "e "), "AD/SRAPBS");
    }


}
