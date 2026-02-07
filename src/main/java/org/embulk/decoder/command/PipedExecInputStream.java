package org.embulk.decoder.command;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PipedExecInputStream extends InputStream
{
    private static final int ERROR_SNIPPET_MAX_BYTES = 8192;

    private final InputStream file;
    private final Process process;
    private final String command;
    private final InputStream in;
    private final OutputStream out;
    private final InputStream err;
    private final Thread providerThread;
    private final Thread stderrCollectorThread;
    private final ByteArrayOutputStream stderrBuffer;
    private volatile IOException failure;
    private volatile boolean closed;
    private final byte[] singleByte;

    public PipedExecInputStream(InputStream file, String command) throws IOException
    {
        this.file = file;
        this.command = command;
        this.process = new ProcessBuilder(buildShellCommand(command)).start();
        this.in = process.getInputStream();
        this.out = process.getOutputStream();
        this.err = process.getErrorStream();
        this.stderrBuffer = new ByteArrayOutputStream();
        this.singleByte = new byte[1];

        this.stderrCollectorThread = new StderrCollectorThread();
        this.stderrCollectorThread.setDaemon(true);
        this.stderrCollectorThread.start();

        this.providerThread = new ProviderThread();
        this.providerThread.setDaemon(true);
        this.providerThread.start();
    }

    @Override
    public int read() throws IOException
    {
        int n = read(singleByte, 0, 1);
        if (n == -1) {
            return -1;
        }
        return singleByte[0] & 0xff;
    }

    @Override
    public int read(byte[] buf) throws IOException
    {
        return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException
    {
        if (buf == null) {
            throw new NullPointerException("buf is null");
        }
        if (off < 0 || len < 0 || len > buf.length - off) {
            throw new IndexOutOfBoundsException(
                    String.format("off=%d len=%d buf.length=%d", off, len, buf.length));
        }
        if (len == 0) {
            return 0;
        }

        throwIfFailed();
        final int read = this.in.read(buf, off, len);
        if (read >= 0) {
            return read;
        }

        waitForWorkers();
        throwIfFailed();
        return -1;
    }

    @Override
    public void close() throws IOException
    {
        if (closed) {
            return;
        }
        closed = true;

        closeQuietly(in);
        closeQuietly(out);
        closeQuietly(file);
        process.destroy();
        waitForWorkers();
        closeQuietly(err);
    }

    private static List<String> buildShellCommand(String command)
    {
        final List<String> cmdline = new ArrayList<String>();
        final String osName = System.getProperty("os.name", "");
        if (osName.toLowerCase().contains("windows")) {
            cmdline.add("PowerShell.exe");
            cmdline.add("-Command");
        }
        else {
            cmdline.add("sh");
            cmdline.add("-c");
        }
        cmdline.add(command);
        return cmdline;
    }

    private void waitForWorkers() throws IOException
    {
        try {
            providerThread.join();
            stderrCollectorThread.join();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for command execution.", ex);
        }
    }

    private synchronized void setFailure(IOException ex)
    {
        if (this.failure == null) {
            this.failure = ex;
        }
    }

    private void throwIfFailed() throws IOException
    {
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(InputStream stream)
    {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        }
        catch (IOException ignored) {
        }
    }

    private static void closeQuietly(OutputStream stream)
    {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        }
        catch (IOException ignored) {
        }
    }

    class ProviderThread extends Thread
    {
        @Override
        public void run()
        {
            copyInputToCommand();
            waitAndCheckExitStatus();
        }

        private void copyInputToCommand()
        {
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = file.read(buf, 0, buf.length)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            catch (IOException ex) {
                setFailure(new IOException("Failed to feed command input: " + command, ex));
                process.destroy();
            }
            finally {
                closeQuietly(out);
            }
        }

        private void waitAndCheckExitStatus()
        {
            int exitStatus;
            try {
                exitStatus = process.waitFor();
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                setFailure(new IOException("Interrupted while waiting for command to finish.", ex));
                return;
            }

            if (exitStatus != 0) {
                final String stderr = new String(stderrBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
                final String message;
                if (stderr.isEmpty()) {
                    message = String.format("Command failed with exit code %d: %s", exitStatus, command);
                }
                else {
                    message = String.format(
                            "Command failed with exit code %d: %s%n%s",
                            exitStatus,
                            command,
                            stderr);
                }
                setFailure(new IOException(message));
            }
        }
    }

    class StderrCollectorThread extends Thread
    {
        @Override
        public void run()
        {
            byte[] buf = new byte[2048];
            int totalBytes = 0;
            try {
                int n;
                while ((n = err.read(buf, 0, buf.length)) != -1) {
                    if (totalBytes < ERROR_SNIPPET_MAX_BYTES) {
                        int writable = Math.min(n, ERROR_SNIPPET_MAX_BYTES - totalBytes);
                        stderrBuffer.write(buf, 0, writable);
                        totalBytes += writable;
                    }
                }
            }
            catch (IOException ex) {
                setFailure(new IOException("Failed to read command stderr: " + command, ex));
            }
            finally {
                closeQuietly(err);
            }
        }
    }
}
