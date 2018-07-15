package org.embulk.decoder.command;

import java.io.InputStream;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.io.PipedInputStream;

import com.google.common.base.Optional;

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigInject;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskSource;
import org.embulk.config.ConfigException;
import org.embulk.spi.BufferAllocator;
import org.embulk.spi.DecoderPlugin;
import org.embulk.spi.FileInput;
import org.embulk.spi.util.FileInputInputStream;
import org.embulk.spi.util.InputStreamFileInput;

public class CommandDecoderPlugin
        implements DecoderPlugin
{
    public interface PluginTask
            extends Task
    {
        @Config("command")
        String getCommand();

        @ConfigInject
        public BufferAllocator getBufferAllocator();
    }

    @Override
    public void transaction(ConfigSource config, DecoderPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);

        control.run(task.dump());
    }

    @Override
    public FileInput open(TaskSource taskSource, FileInput fileInput)
    {
        final PluginTask task = taskSource.loadTask(PluginTask.class);

        final FileInputInputStream files = new FileInputInputStream(fileInput);

        return new InputStreamFileInput(
               task.getBufferAllocator(),
               new InputStreamFileInput.Provider() {
                   public InputStream openNext() throws IOException
                   {
                       if (!files.nextFile()) {
                           return null;
                       }
                       return new PipedExecInputStream(files, task.getCommand());
                   }

                   public void close() throws IOException
                   {
                       files.close();
                   }
               });
    }
}
