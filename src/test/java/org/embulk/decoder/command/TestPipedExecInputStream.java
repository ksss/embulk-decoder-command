package org.embulk.decoder.command;

import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TestPipedExecInputStream
{
    @Test
    public void checkCat() throws IOException
    {
        InputStream in = new ByteArrayInputStream("{\"hello\":\"world!\"}\n".getBytes(StandardCharsets.UTF_8));
        PipedExecInputStream piped = new PipedExecInputStream(in, "cat -");
        assertEquals("{\"hello\":\"world!\"}\n", readAll(piped));
        piped.close();
    }

    @Test
    public void checkPipe() throws IOException
    {
        InputStream in = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
        PipedExecInputStream piped = new PipedExecInputStream(in, "cat - | tr 'a-z' 'A-Z' | cat -");
        assertEquals("HELLO\n", readAll(piped));
        piped.close();
    }

    @Test
    public void checkReadSingleByte() throws IOException
    {
        InputStream in = new ByteArrayInputStream("abc\n".getBytes(StandardCharsets.UTF_8));
        PipedExecInputStream piped = new PipedExecInputStream(in, "cat -");

        assertEquals('a', piped.read());
        assertEquals('b', piped.read());
        assertEquals('c', piped.read());
        assertEquals('\n', piped.read());
        assertEquals(-1, piped.read());
        piped.close();
    }

    @Test
    public void checkGzip() throws IOException
    {
        assumeCommandAvailable("gzip");
        InputStream in = resource("test.gz");
        PipedExecInputStream piped = new PipedExecInputStream(in, "gzip -dc");
        assertEquals("{\"hello\":\"world!\"}\n", readAll(piped));
        piped.close();
    }

    @Test
    public void checkFailure() throws IOException
    {
        InputStream in = new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8));
        PipedExecInputStream piped = new PipedExecInputStream(in, "cat - >/dev/null; echo failed 1>&2; exit 17");

        IOException exception = null;
        try {
            readAll(piped);
            fail("Expected IOException");
        }
        catch (IOException ex) {
            exception = ex;
        }
        finally {
            piped.close();
        }

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("exit code 17"));
        assertTrue(exception.getMessage().contains("failed"));
    }

    private static InputStream resource(String name) throws IOException
    {
        InputStream in = TestPipedExecInputStream.class.getResourceAsStream(
                "/org/embulk/decoder/command/" + name);
        if (in == null) {
            throw new IOException("Missing test resource: " + name);
        }
        return in;
    }

    private static void assumeCommandAvailable(String command) throws IOException
    {
        List<String> checkCommand = new ArrayList<String>();
        String osName = System.getProperty("os.name", "");
        if (osName.toLowerCase().contains("windows")) {
            checkCommand.add("PowerShell.exe");
            checkCommand.add("-Command");
            checkCommand.add("if (Get-Command " + command + " -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }");
        }
        else {
            checkCommand.add("sh");
            checkCommand.add("-c");
            checkCommand.add("command -v " + command + " >/dev/null 2>&1");
        }

        Process process = new ProcessBuilder(checkCommand).start();
        int exitCode;
        try {
            exitCode = process.waitFor();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking command availability", ex);
        }
        Assume.assumeTrue(command + " command is not available", exitCode == 0);
    }

    private static String readAll(InputStream input) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[128];
        int n;
        while ((n = input.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
