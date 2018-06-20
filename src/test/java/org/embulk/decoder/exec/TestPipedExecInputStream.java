package org.embulk.decoder.exec;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import com.google.common.io.Resources;

import org.embulk.decoder.exec.PipedExecInputStream;

public class TestPipedExecInputStream
{
    @Test
    public void checkLzop() throws IOException
    {
        InputStream in = resource("test.lzo");
        PipedExecInputStream piped = new PipedExecInputStream(in, "lzop -dc");
        byte[] buf = new byte[64];
        int n = piped.read(buf, 0, 64);

        assertEquals(19, n);
        assertEquals("{\"hello\":\"world!\"}\n", new String(buf, 0, n, "UTF-8"));
        assertEquals(-1, piped.read(buf, 0, 64));
    }

    @Test
    public void checkGzip() throws IOException
    {
        InputStream in = resource("test.gz");
        PipedExecInputStream piped = new PipedExecInputStream(in, "gzip -dc");
        byte[] buf = new byte[64];
        int n = piped.read(buf, 0, 64);

        assertEquals(19, n);
        assertEquals("{\"hello\":\"world!\"}\n", new String(buf, 0, n, "UTF-8"));
        assertEquals(-1, piped.read(buf, 0, 64));
    }

    private static InputStream resource(String name) throws IOException
    {
        return Resources.getResource("org/embulk/decoder/exec/" + name).openStream();
    }
}
