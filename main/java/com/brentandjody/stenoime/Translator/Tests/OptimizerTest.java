package com.brentandjody.stenoime.Translator.Tests;

import android.test.AndroidTestCase;
import android.widget.ProgressBar;

import com.brentandjody.stenoime.Translator.Definition;
import com.brentandjody.stenoime.Translator.Dictionary;
import com.brentandjody.stenoime.Translator.Optimizer;
import com.brentandjody.stenoime.Translator.SimpleTranslator;
import com.brentandjody.stenoime.Translator.Stroke;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        final SimpleTranslator translator = new SimpleTranslator(getContext(), true);
        final Dictionary dictionary = new Dictionary(getContext());
        final CountDownLatch latch = new CountDownLatch(1);
        dictionary.load(new String[]{"/sdcard/test.json"}, null, new ProgressBar(getContext()), 10);
        Optimizer optimizer = new Optimizer(dictionary);
        //These two are equal length, and should not optimize
        assertNull(optimizer.optimize("AD/SRAPB/TAPBLG", 0, "advantage "));
        assertNull(optimizer.optimize("AD/SRAPBT/APBLG", 0, "advantage "));
        //This one has a shorter stroke
        assertEquals(optimizer.optimize("AD/SRAPB/TAEU/SKWROUS", 0, "advantageous "), "AD/SRAPBGS");
        //Fingerspelling
        assertNull(optimizer.optimize("*A", 0, "a "));
        assertNull(optimizer.optimize("*TK", 1, "d "));
        assertNull(optimizer.optimize("*SR", 1, "v "));
        assertNull(optimizer.optimize("*A", 1, "a "));
        assertNull(optimizer.optimize("*TPH", 1, "n "));
        assertNull(optimizer.optimize("*KR", 1, "c "));
        assertEquals(optimizer.optimize("*-E", 1, "e "), "AD/SRAPBS");
    }


}
