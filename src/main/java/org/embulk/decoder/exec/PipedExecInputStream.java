package org.embulk.decoder.exec;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FilterInputStream;

public class PipedExecInputStream extends InputStream
{
    protected InputStream file;
    protected Process process;
    protected String command;
    protected InputStream in;
    protected OutputStream out;
    protected InputStream err;

    public PipedExecInputStream(InputStream file, String command) throws IOException
    {
        this.file = file;
        this.command = command;
        this.process = Runtime.getRuntime().exec(command);
        this.in = process.getInputStream();
        this.out = process.getOutputStream();
        this.err = process.getErrorStream();

        new ProviderThread(this).start();
    }

    @Override
    public int read() throws IOException
    {
        // Not implemented yet
        throw new IOException();
    }

    @Override
    public int read(byte[] buf) throws IOException
    {
        return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException
    {
        return this.in.read(buf, off, len);
    }

    static class ProviderThread extends Thread
    {

        protected PipedExecInputStream parent;

        public ProviderThread(PipedExecInputStream parent)
        {
            this.parent = parent;
        }

        @Override
        public void run()
        {
            try {
                fill();
            }
            // FIXME
            catch (IOException ex) {
                System.err.println("throwed IOException");
            }
            catch (InterruptedException ex) {
                System.err.println("throwed InterruptedException");
            }
            catch (RuntimeException ex) {
                System.err.println("throwed RuntimeException");
            }
        }

        private void fill() throws IOException, InterruptedException, RuntimeException
        {
            int n = 0;
            int size = 65536;
            byte[] buf = new byte[size];

            while ((n = this.parent.file.read(buf, 0, size)) != -1) {
                this.parent.out.write(buf, 0, n);
            }
            this.parent.out.close();
            this.parent.process.waitFor();
            if (this.parent.process.exitValue() != 0) {
                buf = new byte[512];
                n = this.parent.err.read(buf, 0, 512);
                System.out.println("command:'" + this.parent.command + "' failed with status:" + this.parent.process.exitValue());
                System.err.println(new String(buf, 0, n, "UTF-8"));
                throw new RuntimeException();
            }
        }
    }
}
